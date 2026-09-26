package cc.opencar.assistant.integrations.ihu629g

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
import cc.opencar.assistant.integrations.aaos.CarPropertyBackend
import cc.opencar.assistant.integrations.aaos.PlatformConfig
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * EX2 / IHU629G session — VHAL via [CarPropertyBackend].
 * No VenusVehicleServer on this HU.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class Ihu629gSession(
    private val context: Context,
    private val platform: PlatformConfig,
    initialVariant: PlatformVariant,
) : VehicleSession {
    private val backend: VehiclePropertyBackend = CarPropertyBackend(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bindings = platform.propertyBindings()
    private val allowlist = platform.writableAllowlist

    private val _variant = MutableStateFlow(initialVariant)
    override val variant: StateFlow<PlatformVariant> = _variant.asStateFlow()
    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    private val _events = MutableSharedFlow<VehicleEvent>(extraBufferCapacity = 64)
    private val fanout = SessionEventFanout(_telemetry, _events)
    private var telemetryJob: Job? = null
    private var entityObserveJob: Job? = null

    private val entityByProp: Map<Int, String> = platform.entityByProp()

    override val integrationId: String = Ihu629gIntegration.ID

    init {
        scope.launch { _events.emit(VehicleEvent.Boot) }
        val propIds = platform.bindings.values.map { it.nativeId }.distinct().toIntArray()
        val observe = backend.observe(propIds.takeIf { it.isNotEmpty() })
        if (observe != null) {
            Log.i(TAG, "telemetry: observe (push) mode — no continuous poll")
            telemetryJob = scope.launch {
                try {
                    fanout.publishTelemetry(readSnapshot())
                    fanout.emitBoundSnapshots(platform.bindings) { id, area ->
                        backend.read(id, area)
                    }
                } catch (_: Throwable) { /* best-effort */ }
                observe.debounce(150).collect {
                    try {
                        fanout.publishTelemetry(readSnapshot())
                    } catch (_: Throwable) { /* best-effort */ }
                }
            }
            entityObserveJob = scope.launch {
                observe.collect { update -> fanout.onPropertyUpdate(update, entityByProp) }
            }
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
        }
    }

    override fun telemetry(): Flow<TelemetrySnapshot> = _telemetry
    override fun events(): Flow<VehicleEvent> = _events.asSharedFlow()

    override suspend fun get(property: VehicleProperty): PropertyValue? {
        val (propId, areaId) = resolve(property) ?: return null
        val raw = backend.read(propId, areaId) ?: return null
        return decode(property, raw)
    }

    override suspend fun diagnose(property: VehicleProperty, areaId: Int?): ReadOutcome {
        val resolved = resolve(property) ?: return ReadOutcome.Unavailable(areaId ?: 0)
        val propId = resolved.first
        val areas = listOfNotNull(areaId, resolved.second).distinct()
        var last: ReadOutcome = ReadOutcome.Unavailable(areas.firstOrNull() ?: 0)
        for (a in areas) {
            when (val d = backend.readDetailed(propId, a)) {
                is VehiclePropertyBackend.DetailedRead.Ok -> {
                    val value = decode(property, d.value)
                    return ReadOutcome.Ok(value, a)
                }
                is VehiclePropertyBackend.DetailedRead.Denied ->
                    last = ReadOutcome.Denied(d.permission, a, d.message)
                is VehiclePropertyBackend.DetailedRead.Failed ->
                    last = ReadOutcome.Failed(d.message, a)
                VehiclePropertyBackend.DetailedRead.Empty ->
                    last = ReadOutcome.Unavailable(a)
                VehiclePropertyBackend.DetailedRead.Unavailable ->
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
        val encoded = encode(property, value)
        val ok = when (encoded) {
            is PropertyValue.IntVal -> backend.writeInt(propId, areaId, encoded.value)
            is PropertyValue.FloatVal -> backend.writeFloat(propId, areaId, encoded.value)
            is PropertyValue.BoolVal -> backend.writeBoolean(propId, areaId, encoded.value)
            is PropertyValue.LongVal -> backend.writeInt(propId, areaId, encoded.value.toInt())
            else -> false
        }
        return if (ok) Result.success(Unit)
        else Result.failure(IllegalStateException("VHAL write failed"))
    }

    override fun catalog(): List<CatalogEntry> = platform.catalogEntries()

    override fun entityBindings(): Map<Long, String> =
        platform.bindings.entries.associate { (entityId, b) ->
            b.nativeId.toLong() to entityId
        }

    override fun hasBinding(property: VehicleProperty): Boolean = resolve(property) != null

    override fun androidVolumeGroups() = platform.androidVolumeGroups()

    override fun cameras(): List<CameraSource> {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            platform.resolveCameras(cm.cameraIdList.toList())
        } catch (t: Throwable) {
            Log.w(TAG, "cameras: ${t.message}")
            emptyList()
        }
    }

    override fun close() {
        telemetryJob?.cancel()
        entityObserveJob?.cancel()
        scope.cancel()
        backend.close()
    }

    private fun resolve(property: VehicleProperty): Pair<Int, Int>? {
        bindings[property]?.let { (id, area) ->
            val preferred = property.defaultAreaId
            return id to if (preferred != 0) preferred else area
        }
        platform.bindings[property.key]?.let {
            val preferred = property.defaultAreaId
            return it.nativeId to if (preferred != 0) preferred else it.areaId
        }
        return null
    }

    private fun decode(property: VehicleProperty, raw: Any?): PropertyValue? {
        if (raw == null) return null
        when (property.key) {
            "HVAC_TEMPERATURE_SET" -> {
                val f = (raw as? Number)?.toFloat() ?: return toValue(raw)
                return PropertyValue.FloatVal(Ihu629gCodecs.tempRawToC(f))
            }
            "charge_plug" -> {
                val i = (raw as? Number)?.toInt() ?: return toValue(raw)
                return PropertyValue.IntVal(if (Ihu629gCodecs.plugConnected(i) == true) 1 else 0)
            }
            "parking_comfort" -> {
                val i = (raw as? Number)?.toInt() ?: return toValue(raw)
                return PropertyValue.IntVal(if (Ihu629gCodecs.parkModeIsOn(i)) 1 else 0)
            }
        }
        return toValue(raw)
    }

    private fun encode(property: VehicleProperty, value: PropertyValue): PropertyValue {
        when (property.key) {
            "HVAC_TEMPERATURE_SET" -> {
                val c = when (value) {
                    is PropertyValue.FloatVal -> value.value
                    is PropertyValue.IntVal -> value.value.toFloat()
                    else -> return value
                }
                return PropertyValue.FloatVal(Ihu629gCodecs.tempCToRaw(c))
            }
            "parking_comfort" -> {
                val on = when (value) {
                    is PropertyValue.BoolVal -> value.value
                    is PropertyValue.IntVal -> value.value != 0
                    else -> return value
                }
                return PropertyValue.IntVal(Ihu629gCodecs.parkModeOn(on))
            }
        }
        return value
    }

    private fun readSnapshot(): TelemetrySnapshot {
        fun intOf(key: String): Int? {
            val b = platform.bindings[key] ?: return null
            return (backend.read(b.nativeId, b.areaId) as? Number)?.toInt()
        }
        fun floatOf(key: String): Float? {
            val b = platform.bindings[key] ?: return null
            val raw = backend.read(b.nativeId, b.areaId) as? Number ?: return null
            return when (key) {
                "HVAC_TEMPERATURE_SET" -> Ihu629gCodecs.tempRawToC(raw.toFloat())
                "PERF_VEHICLE_SPEED" -> AospVehicleIds.speedMsToKmh(raw.toFloat())
                else -> raw.toFloat()
            }
        }
        val mode = intOf("drive_mode")
        val plug = intOf("charge_plug")
        return TelemetrySnapshot(
            gear = intOf("CURRENT_GEAR"),
            speedKmh = floatOf("PERF_VEHICLE_SPEED"),
            evBatteryPercent = floatOf("ev_battery_percent"),
            rangeKm = floatOf("range_km"),
            driveMode = mode?.let { platform.driveModeEnum[it] ?: "mode:$it" },
            regenLevel = intOf("regen"),
            ignitionState = intOf("IGNITION_STATE"),
            hvacPower = intOf("HVAC_POWER_ON")?.let { it != 0 && it != 2 },
            hvacTempC = floatOf("HVAC_TEMPERATURE_SET"),
            hvacFan = intOf("HVAC_FAN_SPEED"),
            chargeCurrentA = floatOf("charge_current"),
            chargePlugConnected = Ihu629gCodecs.plugConnected(plug),
            extras = buildMap {
                put("bridge", if (backend.available) "ok" else "unavailable")
                put("backend", "vhal")
                put("accessMode", backend.mode.wireName)
                put("family", "flyme")
                if (mode != null) put("driveModeRaw", mode.toString())
            },
        )
    }

    private fun toValue(raw: Any?): PropertyValue? =
        cc.opencar.assistant.integrations.aaos.toPropertyValue(raw)

    companion object {
        private const val TAG = "Ihu629gSession"
        private const val POLL_MS = 1000L
    }
}
