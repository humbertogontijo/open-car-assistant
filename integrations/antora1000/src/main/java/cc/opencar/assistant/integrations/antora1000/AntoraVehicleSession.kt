package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import cc.opencar.assistant.api.CameraSource
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.api.DvrStreamConfig
import cc.opencar.assistant.api.PlatformVariant
import cc.opencar.assistant.api.PropertyValue
import cc.opencar.assistant.api.ReadOutcome
import cc.opencar.assistant.api.TelemetrySnapshot
import cc.opencar.assistant.api.VehicleEvent
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.WellKnownProperties
import cc.opencar.assistant.integrations.common.AospVehicleIds
import cc.opencar.assistant.integrations.common.PlatformConfig
import cc.opencar.assistant.integrations.common.PropertyAccessMode
import cc.opencar.assistant.integrations.common.SessionEventFanout
import cc.opencar.assistant.integrations.common.VehiclePropertyBackend
import cc.opencar.assistant.integrations.common.entityByProp
import cc.opencar.assistant.integrations.common.toPropertyValue
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
    private val catalogEntries: List<CatalogEntry> = AntoraCatalog.loadFromAssets(context)
    private val platform: PlatformConfig = AntoraCatalog.platformConfig(context)
    private val bindings = AntoraCatalog.wellKnownBindings(context)
    private val allowlist = platform.writableAllowlist

    private val _variant = MutableStateFlow(initialVariant)
    override val variant: StateFlow<PlatformVariant> = _variant.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    private val fanout = SessionEventFanout(_telemetry, _events)

    private var telemetryJob: Job? = null
    private var entityObserveJob: Job? = null
    private var wheelJob: Job? = null

    /** propId → entityId for EntityValueChanged fan-out. */
    private val entityByProp: Map<Int, String> = platform.entityByProp()

    /** Props that affect [readSnapshot] — ignore unrelated Venus stream noise. */
    private val telemetryPropIds: Set<Int> = buildSet {
        bindings.values.forEach { add(it.first) }
        add(AntoraVhalIds.INFO_EV_BATTERY_CAPACITY)
        add(AntoraVhalIds.DRIVE_MODE_SELECTION_PURE)
        add(AntoraVhalIds.DRIVE_MODE_SELECTION_HYBRID)
        add(AntoraVhalIds.DRIVE_MODE_SELECTION_POWER)
        add(AntoraVhalIds.HVAC_POWER_ON)
        add(AntoraVhalIds.INFO_MODEL)
        add(AntoraVhalIds.PARKING_BRAKE_ON)
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
            Log.i(TAG, "telemetry: observe (push) mode")
            telemetryJob = scope.launch {
                try {
                    fanout.publishTelemetry(readSnapshot())
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
        } else {
            Log.i(TAG, "telemetry: poll mode (1s)")
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
        }
        // Wheel-key poll kept until those props are confirmed on the gRPC stream.
        wheelJob = scope.launch { pollWheelKeys() }
    }

    override fun telemetry(): Flow<TelemetrySnapshot> = _telemetry

    override fun events(): Flow<VehicleEvent> = _events.asSharedFlow()

    override suspend fun get(property: VehicleProperty): PropertyValue? {
        val (propId, areaId) = resolve(property) ?: return null
        val raw = backend.read(propId, areaId)
        val value = toPropertyValue(raw)
        if (property.key == WellKnownProperties.INFO_VIN.key && value is PropertyValue.StringVal) {
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
                    if (property.key == WellKnownProperties.INFO_VIN.key && value is PropertyValue.StringVal) {
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
            cm.cameraIdList.mapIndexed { index, id ->
                CameraSource(id = id, label = "Camera $index ($id)", cameraId = id)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "camera enum failed: ${t.message}")
            emptyList()
        }
    }

    override fun dvrStreamConfig(): DvrStreamConfig = platform.dvr

    override fun androidVolumeGroups() = platform.androidVolumeGroups()

    override fun close() {
        telemetryJob?.cancel()
        entityObserveJob?.cancel()
        wheelJob?.cancel()
        scope.cancel()
        backend.close()
    }

    fun updateVariant(variant: PlatformVariant) {
        _variant.value = variant
    }

    /**
     * Poll SWC hard-key VHAL props for press / long-press edges.
     * Short press fires on release before [WHEEL_LONG_PRESS_MS]; long-press fires once while held.
     */
    private suspend fun pollWheelKeys() {
        val last = mutableMapOf<String, Int?>()
        val downAt = mutableMapOf<String, Long>()
        val longFired = mutableSetOf<String>()
        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            for ((key, propId) in AntoraVhalIds.WHEEL_HARD_KEYS) {
                val raw = backend.read(propId, AntoraVhalIds.AREA_GLOBAL)
                val value = when (raw) {
                    is Number -> raw.toInt()
                    is Boolean -> if (raw) 1 else 0
                    else -> null
                }
                val prev = last[key]
                if (value != null && value != 0) {
                    if (prev == null || prev == 0) {
                        downAt[key] = now
                        longFired.remove(key)
                    } else if (key !in longFired) {
                        val started = downAt[key] ?: now
                        if (now - started >= WHEEL_LONG_PRESS_MS) {
                            longFired.add(key)
                            _events.emit(VehicleEvent.WheelKeyLongPressed(key))
                        }
                    }
                } else if (prev != null && prev != 0 && (value == null || value == 0)) {
                    if (key !in longFired) {
                        _events.emit(VehicleEvent.WheelKeyPressed(key))
                    }
                    downAt.remove(key)
                    longFired.remove(key)
                }
                if (value != null) last[key] = value
            }
            delay(WHEEL_POLL_MS)
        }
    }

    private fun resolve(property: VehicleProperty): Pair<Int, Int>? {
        bindings[property]?.let { return it }
        bindings.entries.firstOrNull { it.key.key == property.key }?.value?.let { return it }
        val native = property.nativeId?.toInt()
        if (native != null) return native to property.defaultAreaId
        val fromCatalog = catalogEntries.firstOrNull {
            it.property.key == property.key || it.name == property.key
        }
        val id = fromCatalog?.property?.nativeId?.toInt() ?: return null
        return id to (fromCatalog.areaIds.firstOrNull() ?: 0)
    }

    private fun readSnapshot(): TelemetrySnapshot {
        fun intProp(p: VehicleProperty): Int? {
            val binding = bindings[p] ?: return null
            return toPropertyValue(backend.read(binding.first, binding.second))?.asInt()
        }

        fun floatProp(p: VehicleProperty): Float? {
            val binding = bindings[p] ?: return null
            return toPropertyValue(backend.read(binding.first, binding.second))?.asFloat()
        }

        val speedMs = floatProp(WellKnownProperties.SPEED_KMH)
        val speedKmh = speedMs?.let { AospVehicleIds.speedMsToKmh(it) }
        // Prefer vendor display % (matches cluster). Fall back to Wh-level / capacity,
        // then hybrid SOC (charge-target band on EM-i — often ≠ dashboard %).
        val evPercentDirect = floatProp(WellKnownProperties.EV_BATTERY_PERCENT)
        val evRaw = floatProp(WellKnownProperties.EV_BATTERY_LEVEL_RAW)
        val battCap = toPropertyValue(
            backend.read(AntoraVhalIds.INFO_EV_BATTERY_CAPACITY, 0),
        )?.asFloat()
        val evPercentFromWh = if (evRaw != null && battCap != null && battCap > 0f) {
            (evRaw / battCap) * 100f
        } else {
            null
        }
        val hybridSoc = floatProp(WellKnownProperties.HYBRID_SOC)
        val evPercent = evPercentDirect ?: evPercentFromWh ?: hybridSoc
        val rangeM = floatProp(WellKnownProperties.RANGE_KM)
        val rangeEv = floatProp(WellKnownProperties.RANGE_EV_KM)
        val rangeFuel = floatProp(WellKnownProperties.RANGE_FUEL_KM)
        val fuelPercent = floatProp(WellKnownProperties.FUEL_PERCENT)
        val odometer = floatProp(WellKnownProperties.ODOMETER_KM)
        val tempAmbient = floatProp(WellKnownProperties.TEMP_AMBIENT_C)
        val tempIndoor = floatProp(WellKnownProperties.TEMP_INDOOR_C)
        val batteryTemp = floatProp(WellKnownProperties.BATTERY_TEMP_C)
        val chargeEta = floatProp(WellKnownProperties.CHARGE_ESTIMATED_TIME)
        val chargeEnergy = floatProp(WellKnownProperties.CHARGE_ENERGY)
        val chargeWorkA = floatProp(WellKnownProperties.CHARGE_WORK_CURRENT)
        val chargeWorkV = floatProp(WellKnownProperties.CHARGE_WORK_VOLTAGE)
        val dischargeSoc = floatProp(WellKnownProperties.CHARGE_DISCHARGE_SOC)
        val avgEnergy = floatProp(WellKnownProperties.AVG_ENERGY_KWH_100KM)
        val avgFuel = floatProp(WellKnownProperties.AVG_FUEL_L_100KM)
        val flowDriving = floatProp(WellKnownProperties.ENERGY_FLOW_DRIVING)
        val flowBattery = floatProp(WellKnownProperties.ENERGY_FLOW_BATTERY)
        val flowClimate = floatProp(WellKnownProperties.ENERGY_FLOW_CLIMATE)
        val maintKm = floatProp(WellKnownProperties.MAINTENANCE_MILEAGE_KM)
        val sinceMaintKm = floatProp(WellKnownProperties.SINCE_MAINTENANCE_KM)
        val driveModeRaw = intProp(WellKnownProperties.DRIVE_MODE)
        val pure = toPropertyValue(backend.read(AntoraVhalIds.DRIVE_MODE_SELECTION_PURE, 0))?.asInt()
        val hybrid = toPropertyValue(backend.read(AntoraVhalIds.DRIVE_MODE_SELECTION_HYBRID, 0))?.asInt()
        val power = toPropertyValue(backend.read(AntoraVhalIds.DRIVE_MODE_SELECTION_POWER, 0))?.asInt()
        val driveLabel = driveModeRaw?.let { platform.driveModeEnum[it] ?: "mode:$it" }
        val energyLabel = when {
            pure == 1 -> "opt.drive_mode.1"
            hybrid == 1 -> "opt.drive_mode.2"
            power == 1 -> "opt.drive_mode.3"
            else -> null
        }
        val plug = intProp(WellKnownProperties.CHARGE_PLUG)
        val hvacPower = toPropertyValue(
            backend.read(AntoraVhalIds.HVAC_POWER_ON, AntoraVhalIds.AREA_HVAC_PRIMARY),
        )?.asInt()?.let { it != 0 }
        val model = toPropertyValue(
            backend.read(AntoraVhalIds.INFO_MODEL, AntoraVhalIds.AREA_GLOBAL),
        )?.display()
        val parkingBrake = toPropertyValue(
            backend.read(AntoraVhalIds.PARKING_BRAKE_ON, AntoraVhalIds.AREA_GLOBAL),
        )
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
            gear = intProp(WellKnownProperties.GEAR),
            speedKmh = speedKmh,
            evBatteryPercent = evPercent,
            fuelCapacityMl = floatProp(WellKnownProperties.FUEL_CAPACITY),
            fuelPercent = fuelPercent,
            rangeKm = rangeKm,
            rangeEvKm = rangeEv,
            rangeFuelKm = rangeFuel,
            odometerKm = odometer,
            hvacPower = hvacPower,
            hvacTempC = floatProp(WellKnownProperties.HVAC_TEMP_C),
            hvacFan = intProp(WellKnownProperties.HVAC_FAN),
            tempAmbientC = tempAmbient,
            tempIndoorC = tempIndoor,
            batteryTempC = batteryTemp,
            hybridSocPercent = hybridSoc,
            chargeCurrentA = floatProp(WellKnownProperties.CHARGE_CURRENT),
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
            regenLevel = intProp(WellKnownProperties.REGEN),
            ignitionState = intProp(WellKnownProperties.IGNITION),
            extras = buildMap {
                put("bridge", if (backend.available) "ok" else "unavailable")
                put("accessMode", backend.mode.wireName)
                put("catalogSize", catalogEntries.size.toString())
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

        fun redactVin(vin: String): String =
            if (vin.length < 8) "[redacted]" else vin.take(3) + "****" + vin.takeLast(4)
    }
}
