package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import cc.opencar.assistant.api.CameraSource
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.api.PlatformVariant
import cc.opencar.assistant.api.PropertyValue
import cc.opencar.assistant.api.ReadOutcome
import cc.opencar.assistant.api.TelemetrySnapshot
import cc.opencar.assistant.api.VehicleEvent
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.integrations.aaos.AospVehicleIds
import cc.opencar.assistant.integrations.aaos.PlatformConfig
import cc.opencar.assistant.integrations.aaos.PropertyAccessMode
import cc.opencar.assistant.integrations.aaos.PropertyUpdate
import cc.opencar.assistant.integrations.aaos.SessionEventFanout
import cc.opencar.assistant.integrations.aaos.VehiclePropertyBackend
import cc.opencar.assistant.integrations.aaos.entityByProp
import cc.opencar.assistant.integrations.aaos.toPropertyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@OptIn(kotlinx.coroutines.FlowPreview::class)
class AntoraVehicleSession(
    private val context: Context,
    initialVariant: PlatformVariant,
) : VehicleSession {
    private val backend: VehiclePropertyBackend = AntoraBackendFactory.create(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val basePlatform: PlatformConfig = AntoraCatalog.platformConfig(context)
    private var platform: PlatformConfig = basePlatform.forSelection(
        initialVariant.skuId,
        initialVariant.id,
    )
    private val catalogEntries: List<CatalogEntry> = basePlatform.catalogEntries()
    private val allowlist = basePlatform.writableAllowlist

    private val _variant = MutableStateFlow(initialVariant)
    override val variant: StateFlow<PlatformVariant> = _variant.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    private val fanout = SessionEventFanout(_telemetry, _events)

    private var telemetryJob: Job? = null
    private var entityObserveJob: Job? = null
    private var wheelJob: Job? = null

    /** propId → entityId for EntityValueChanged fan-out (follows active model). */
    private val entityByProp: Map<Int, String>
        get() = platform.entityByProp()

    private val bindings: Map<VehicleProperty, Pair<Int, Int>>
        get() = platform.propertyBindings()

    /** SWC hard keys from platform.json (`WHEEL_HARD_KEY_*`). */
    private val wheelHardKeys: Map<String, Int> by lazy {
        basePlatform.properties
            .asSequence()
            .filter { it.key.startsWith("WHEEL_HARD_KEY_") }
            .associate { p ->
                p.key.removePrefix("WHEEL_HARD_KEY_").lowercase() to p.id
            }
    }

    /** Props that affect [readSnapshot] — ignore unrelated Venus stream noise. */
    private val telemetryPropIds: Set<Int>
        get() = buildSet {
            platform.bindings.values.forEach { add(it.nativeId) }
            for (key in EXTRA_TELEMETRY_KEYS) {
                catalogNativeId(key)?.let { add(it) }
            }
        }

    private fun catalogNativeId(key: String): Int? =
        platform.bindings[key]?.nativeId
            ?: basePlatform.properties.firstOrNull { it.key == key }?.id

    private fun catalogArea(key: String): Int =
        platform.bindings[key]?.areaId
            ?: basePlatform.properties.firstOrNull { it.key == key }?.areas?.firstOrNull()
            ?: 0

    private fun readCatalog(key: String): PropertyValue? {
        val id = catalogNativeId(key) ?: return null
        return toPropertyValue(backend.read(id, catalogArea(key)))
    }

    override val integrationId: String = Antora1000Integration.ID

    val accessMode: PropertyAccessMode get() = backend.mode

    init {
        scope.launch { _events.emit(VehicleEvent.Boot) }
        val propIds = platform.bindings.values.map { it.nativeId }.distinct().toIntArray()
        val observe = when (backend.mode) {
            PropertyAccessMode.GRPC -> backend.observe(null)
            PropertyAccessMode.CAR_PROPERTY ->
                backend.observe(propIds.takeIf { it.isNotEmpty() })
        }
        if (observe != null) {
            Log.i(TAG, "telemetry: observe (push) mode — no continuous poll")
            // One-shot seed from cache / CarProperty so UI + edge detectors have a baseline
            // before the first stream delta. Continuous refresh is observe-only.
            telemetryJob = scope.launch {
                try {
                    fanout.publishTelemetry(readSnapshot())
                    fanout.emitBoundSnapshots(platform.bindings) { id, area ->
                        backend.read(id, area)
                    }
                } catch (_: Throwable) { /* best-effort */ }
                observe
                    .filter { it.propId in telemetryPropIds }
                    .debounce(150)
                    .collect {
                        try {
                            fanout.publishTelemetry(readSnapshot())
                        } catch (_: Throwable) { /* best-effort */ }
                    }
            }
            entityObserveJob = scope.launch {
                observe.collect { update -> fanout.onPropertyUpdate(update, entityByProp) }
            }
            wheelJob = scope.launch { observeWheelKeys(observe) }
        } else {
            Log.i(TAG, "telemetry: poll mode (1s) — observe unavailable")
            telemetryJob = scope.launch {
                while (isActive) {
                    try {
                        fanout.publishTelemetry(readSnapshot())
                        fanout.emitBoundSnapshots(platform.bindings) { id, area ->
                            backend.read(id, area)
                        }
                    } catch (_: Throwable) { /* best-effort */ }
                    delay(POLL_MS)
                }
            }
            wheelJob = scope.launch { pollWheelKeys() }
        }
    }

    override fun telemetry(): Flow<TelemetrySnapshot> = _telemetry

    override fun events(): Flow<VehicleEvent> = _events.asSharedFlow()

    override suspend fun get(property: VehicleProperty): PropertyValue? {
        val (propId, areaId) = resolve(property) ?: return null
        val raw = backend.read(propId, areaId)
        val value = toPropertyValue(raw)
        if (property.key == "INFO_VIN" && value is PropertyValue.StringVal) {
            return PropertyValue.StringVal(redactVin(value.value))
        }
        return value
    }

    override suspend fun diagnose(property: VehicleProperty, areaId: Int?): ReadOutcome {
        val resolved = resolve(property) ?: return ReadOutcome.Unavailable(areaId ?: 0)
        val propId = resolved.first
        val areas = if (areaId != null) listOf(areaId) else {
            val fromCatalog = catalogEntries.firstOrNull {
                it.property.key == property.key || it.name == property.key ||
                    it.property.nativeId == property.nativeId
            }?.areaIds
            (fromCatalog ?: listOf(resolved.second)).distinct()
        }
        var last: ReadOutcome = ReadOutcome.Unavailable(areas.firstOrNull() ?: 0)
        for (a in areas) {
            when (val d = backend.readDetailed(propId, a)) {
                is VehiclePropertyBackend.DetailedRead.Ok -> {
                    var value = toPropertyValue(d.value)
                    if (property.key == "INFO_VIN" && value is PropertyValue.StringVal) {
                        value = PropertyValue.StringVal(redactVin(value.value))
                    }
                    return ReadOutcome.Ok(value, a)
                }
                is VehiclePropertyBackend.DetailedRead.Denied ->
                    last = ReadOutcome.Denied(d.permission, a, d.message)
                is VehiclePropertyBackend.DetailedRead.Failed ->
                    last = ReadOutcome.Failed(d.message, a)
                is VehiclePropertyBackend.DetailedRead.Empty ->
                    last = ReadOutcome.Unavailable(a)
                is VehiclePropertyBackend.DetailedRead.Unavailable ->
                    last = ReadOutcome.Unavailable(a)
            }
        }
        return last
    }

    override suspend fun set(property: VehicleProperty, value: PropertyValue): Result<Unit> {
        val (propId, areaId) = resolve(property)
            ?: return Result.failure(IllegalArgumentException("Unknown property ${property.qualifiedName}"))
        if (propId !in allowlist) {
            return Result.failure(SecurityException("Property not on writable allowlist"))
        }
        val ok = when (value) {
            is PropertyValue.IntVal -> backend.writeInt(propId, areaId, value.value)
            is PropertyValue.FloatVal -> backend.writeFloat(propId, areaId, value.value)
            is PropertyValue.BoolVal -> backend.writeBoolean(propId, areaId, value.value)
            is PropertyValue.LongVal -> backend.writeInt(propId, areaId, value.value.toInt())
            else -> false
        }
        return if (ok) Result.success(Unit) else Result.failure(IllegalStateException("VHAL write failed"))
    }

    override fun catalog(): List<CatalogEntry> = catalogEntries

    override fun entityBindings(): Map<Long, String> =
        platform.bindings.entries.associate { (entityId, b) ->
            b.nativeId.toLong() to entityId
        }

    override fun hasBinding(property: VehicleProperty): Boolean = resolve(property) != null

    override fun cameras(): List<CameraSource> {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            platform.resolveCameras(cm.cameraIdList.toList())
        } catch (t: Throwable) {
            Log.w(TAG, "camera enum failed: ${t.message}")
            emptyList()
        }
    }

    override fun androidVolumeGroups() = platform.androidVolumeGroups()

    override fun close() {
        telemetryJob?.cancel()
        entityObserveJob?.cancel()
        wheelJob?.cancel()
        scope.cancel()
        backend.close()
    }

    fun updateVariant(variant: PlatformVariant) {
        platform = basePlatform.forSelection(variant.skuId, variant.id)
        _variant.value = variant
    }

    /**
     * SWC hard-key edges from the property observe stream (reactive path).
     * Short press = release before [WHEEL_LONG_PRESS_MS]; long-press = timer while held.
     * If these props never appear on the stream, shortcuts will stay silent — intentional.
     */
    private suspend fun observeWheelKeys(observe: Flow<PropertyUpdate>) {
        val propToKey = wheelHardKeys.entries.associate { (k, id) -> id to k }
        val last = mutableMapOf<String, Int?>()
        val longFired = mutableSetOf<String>()
        val longJobs = mutableMapOf<String, Job>()
        Log.i(TAG, "wheel keys: observe (push) mode props=${propToKey.size}")
        for ((key, propId) in wheelHardKeys) {
            last[key] = wheelLevel(backend.read(propId, 0))
        }
        observe.filter { it.propId in propToKey }.collect { update ->
            val key = propToKey[update.propId] ?: return@collect
            val value = wheelLevel(update.value) ?: return@collect
            applyWheelEdge(key, value, last, longFired, longJobs)
        }
    }

    /**
     * Poll SWC hard-key VHAL props when observe is unavailable (CarProperty fallback).
     */
    private suspend fun pollWheelKeys() {
        val last = mutableMapOf<String, Int?>()
        val longFired = mutableSetOf<String>()
        val longJobs = mutableMapOf<String, Job>()
        Log.i(TAG, "wheel keys: poll mode (${WHEEL_POLL_MS}ms)")
        while (coroutineContext.isActive) {
            for ((key, propId) in wheelHardKeys) {
                val value = wheelLevel(backend.read(propId, 0))
                if (value != null) {
                    applyWheelEdge(key, value, last, longFired, longJobs)
                }
            }
            delay(WHEEL_POLL_MS)
        }
    }

    private fun applyWheelEdge(
        key: String,
        value: Int,
        last: MutableMap<String, Int?>,
        longFired: MutableSet<String>,
        longJobs: MutableMap<String, Job>,
    ) {
        val prev = last[key]
        if (value != 0) {
            if (prev == null || prev == 0) {
                longFired.remove(key)
                longJobs.remove(key)?.cancel()
                longJobs[key] = scope.launch {
                    delay(WHEEL_LONG_PRESS_MS)
                    if (key !in longFired) {
                        longFired.add(key)
                        Log.i(TAG, "wheel long-press key=$key")
                        _events.tryEmit(VehicleEvent.WheelKeyLongPressed(key))
                    }
                }
            }
        } else if (prev != null && prev != 0) {
            longJobs.remove(key)?.cancel()
            if (key !in longFired) {
                Log.i(TAG, "wheel press key=$key")
                _events.tryEmit(VehicleEvent.WheelKeyPressed(key))
            }
            longFired.remove(key)
        }
        last[key] = value
    }

    private fun wheelLevel(raw: Any?): Int? = when (raw) {
        is Number -> raw.toInt()
        is Boolean -> if (raw) 1 else 0
        else -> null
    }

    private fun resolve(property: VehicleProperty): Pair<Int, Int>? {
        bindings[property]?.let { (id, area) ->
            val preferred = property.defaultAreaId
            return id to if (preferred != 0) preferred else area
        }
        bindings.entries.firstOrNull { it.key.key == property.key }?.value?.let { (id, area) ->
            val preferred = property.defaultAreaId
            return id to if (preferred != 0) preferred else area
        }
        val native = property.nativeId?.toInt()
        if (native != null) return native to property.defaultAreaId
        val fromCatalog = catalogEntries.firstOrNull {
            it.property.key == property.key || it.name == property.key
        }
        val id = fromCatalog?.property?.nativeId?.toInt() ?: return null
        val preferred = property.defaultAreaId
        val catalogArea = fromCatalog.areaIds.firstOrNull() ?: 0
        return id to if (preferred != 0) preferred else catalogArea
    }

    private fun readSnapshot(): TelemetrySnapshot {
        fun intProp(key: String): Int? {
            val b = platform.bindings[key] ?: return null
            return toPropertyValue(backend.read(b.nativeId, b.areaId))?.asInt()
        }

        fun floatProp(key: String): Float? {
            val b = platform.bindings[key] ?: return null
            return toPropertyValue(backend.read(b.nativeId, b.areaId))?.asFloat()
        }

        val speedMs = floatProp("PERF_VEHICLE_SPEED")
        val speedKmh = speedMs?.let { AospVehicleIds.speedMsToKmh(it) }
        // Prefer vendor display % (matches cluster). Fall back to Wh-level / capacity,
        // then hybrid SOC (charge-target band on EM-i — often ≠ dashboard %).
        val evPercentDirect = floatProp("TYPE_EV_BATTERY_PERCENTAGE")
        val evRaw = floatProp("EV_BATTERY_LEVEL")
        val battCap = readCatalog("INFO_EV_BATTERY_CAPACITY")?.asFloat()
        val evPercentFromWh = if (evRaw != null && battCap != null && battCap > 0f) {
            (evRaw / battCap) * 100f
        } else {
            null
        }
        val hybridSoc = floatProp("HYBRID_FUNC_BATTERY_SOC")
        val evPercent = evPercentDirect ?: evPercentFromWh ?: hybridSoc
        val rangeM = floatProp("RANGE_REMAINING")
        val rangeEv = floatProp("SENSOR_TYPE_ENDURANCE_MILEAGE_EV")
        val rangeFuel = floatProp("SENSOR_TYPE_ENDURANCE_MILEAGE_FUEL")
        val fuelPercent = floatProp("TYPE_FUEL_PERCENTAGE")
        val odometer = floatProp("PERF_ODOMETER")
        val tempAmbient = floatProp("SENSOR_TYPE_TEMPERATURE_AMBIENT")
        val tempIndoor = floatProp("SENSOR_TYPE_TEMPERATURE_INDOOR")
        val batteryTemp = floatProp("SENSOR_TYPE_EV_BATTERY_TEMP")
        val chargeEta = floatProp("CHARGE_FUNC_CHARGING_ESTIMATED_TIME")
        val chargeEnergy = floatProp("CHARGE_FUNC_CHARGING_ENERGY")
        val chargeWorkA = floatProp("CHARGE_FUNC_CHARGING_WORK_CURRENT")
        val chargeWorkV = floatProp("CHARGE_FUNC_CHARGING_WORK_VOLTAGE")
        val dischargeSoc = floatProp("CHARGE_FUNC_DISCHARGING_SOC")
        val avgEnergy = floatProp("TRIP_DI_AVG_ELC_CONSUMPTION")
        val avgFuel = floatProp("TRIP_DI_AVG_FUEL_CONSUMPTION")
        val flowDriving = floatProp("TRIP_ED_DRIVING_ENERGY_FLOW")
        val flowBattery = floatProp("TRIP_ED_BATTERY_ENERGY_FLOW")
        val flowClimate = floatProp("TRIP_ED_CLIMATE_ENERGY_FLOW")
        val maintKm = floatProp("TYPE_MAINTENANCE_MILEAGE")
        val sinceMaintKm = floatProp("TYPE_SINCE_MAINTENANCE_TOTAL_MILEAGE")
        val driveModeRaw = intProp("DM_FUNC_DRIVE_MODE_SELECT")
        val pure = readCatalog("DRIVE_MODE_SELECTION_PURE")?.asInt()
        val hybrid = readCatalog("DRIVE_MODE_SELECTION_HYBRID")?.asInt()
        val power = readCatalog("DRIVE_MODE_SELECTION_POWER")?.asInt()
        val driveLabel = driveModeRaw?.let { platform.driveModeEnum[it] ?: "mode:$it" }
        val energyLabel = when {
            pure == 1 -> "opt.drive_mode.1"
            hybrid == 1 -> "opt.drive_mode.2"
            power == 1 -> "opt.drive_mode.3"
            else -> null
        }
        val plug = intProp("CHARGE_FUNC_CHARGING_PLUG_STATE")
        val hvacPower = readCatalog("HVAC_POWER_ON")?.asInt()?.let { it != 0 }
        val model = readCatalog("INFO_MODEL")?.display()
        val parkingBrake = readCatalog("PARKING_BRAKE_ON")
        val parkingLabel = when (parkingBrake) {
            is PropertyValue.BoolVal -> if (parkingBrake.value) "on" else "off"
            is PropertyValue.IntVal -> if (parkingBrake.value != 0) "on" else "off"
            else -> parkingBrake?.display()
        }
        val rangeKm = when {
            rangeM == null -> null
            rangeM >= 10_000f -> rangeM / 1000f
            else -> rangeM
        }

        return TelemetrySnapshot(
            gear = intProp("GEAR_SELECTION"),
            speedKmh = speedKmh,
            evBatteryPercent = evPercent,
            fuelCapacityMl = floatProp("INFO_FUEL_CAPACITY"),
            fuelPercent = fuelPercent,
            rangeKm = rangeKm,
            rangeEvKm = rangeEv,
            rangeFuelKm = rangeFuel,
            odometerKm = odometer,
            hvacPower = hvacPower,
            hvacTempC = floatProp("HVAC_TEMPERATURE_SET"),
            hvacFan = intProp("HVAC_FAN_SPEED"),
            tempAmbientC = tempAmbient,
            tempIndoorC = tempIndoor,
            batteryTempC = batteryTemp,
            hybridSocPercent = hybridSoc,
            chargeCurrentA = floatProp("CHARGE_FUNC_CHARGING_CURRENT"),
            chargePlugConnected = plug?.let { it != 0 },
            chargeEstimatedTimeMin = chargeEta?.takeIf { it >= 0f },
            chargeEnergyKwh = chargeEnergy?.takeIf { it >= 0f },
            chargeWorkCurrentA = chargeWorkA?.takeIf { it >= 0f },
            chargeWorkVoltageV = chargeWorkV?.takeIf { it >= 0f },
            dischargeSocPercent = dischargeSoc,
            avgEnergyKwh100km = avgEnergy,
            avgFuelL100km = avgFuel,
            energyFlowDriving = flowDriving,
            energyFlowBattery = flowBattery,
            energyFlowClimate = flowClimate,
            maintenanceMileageKm = maintKm,
            sinceMaintenanceKm = sinceMaintKm,
            driveMode = driveLabel,
            regenLevel = intProp("SETTING_FUNC_ENERGY_REGENERATION"),
            ignitionState = intProp("IGNITION_STATE"),
            extras = buildMap {
                put("bridge", if (backend.available) "ok" else "unavailable")
                put("accessMode", backend.mode.wireName)
                put("catalogSize", catalogEntries.size.toString())
                if (driveModeRaw != null) put("driveModeRaw", driveModeRaw.toString())
                if (model != null) put("model", model)
                if (parkingLabel != null) put("parkingBrake", parkingLabel)
                if (energyLabel != null) put("energyMode", energyLabel)
                put("energyPure", (pure == 1).toString())
                put("energyHybrid", (hybrid == 1).toString())
                put("energyPower", (power == 1).toString())
                battCap?.let { put("evBatteryCapacityWh", it.toString()) }
            },
        )
    }

    companion object {
        private const val TAG = "AntoraSession"
        private const val POLL_MS = 1000L
        private const val WHEEL_POLL_MS = 100L
        private const val WHEEL_LONG_PRESS_MS = 700L

        /** Catalog keys read in [readSnapshot] that may sit outside the SKU allowlist. */
        private val EXTRA_TELEMETRY_KEYS = listOf(
            "INFO_EV_BATTERY_CAPACITY",
            "DRIVE_MODE_SELECTION_PURE",
            "DRIVE_MODE_SELECTION_HYBRID",
            "DRIVE_MODE_SELECTION_POWER",
            "HVAC_POWER_ON",
            "INFO_MODEL",
            "PARKING_BRAKE_ON",
        )

        fun redactVin(vin: String): String =
            if (vin.length < 8) "[redacted]" else vin.take(3) + "****" + vin.takeLast(4)
    }
}
