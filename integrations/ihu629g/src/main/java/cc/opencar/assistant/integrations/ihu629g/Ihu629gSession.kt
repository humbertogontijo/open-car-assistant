package cc.opencar.assistant.integrations.ihu629g

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
import cc.opencar.assistant.integrations.common.CarPropertyBackend
import cc.opencar.assistant.integrations.common.PlatformConfig
import cc.opencar.assistant.integrations.common.SessionEventFanout
import cc.opencar.assistant.integrations.common.VehiclePropertyBackend
import cc.opencar.assistant.integrations.common.entityByProp
import cc.opencar.assistant.integrations.common.toPropertyValue
import cc.opencar.assistant.integrations.common.wellKnownByKey
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
    private val bindings = wellKnownBindings(platform)
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
            Log.i(TAG, "telemetry: observe (push) mode")
            telemetryJob = scope.launch {
                try {
                    fanout.publishTelemetry(readSnapshot())
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
            cm.cameraIdList.mapIndexed { i, id -> CameraSource(id, "Camera $i", id) }
        } catch (t: Throwable) {
            Log.w(TAG, "cameras: ${t.message}")
            emptyList()
        }
    }

    override fun dvrStreamConfig(): DvrStreamConfig = platform.dvr

    override fun close() {
        telemetryJob?.cancel()
        entityObserveJob?.cancel()
        scope.cancel()
        backend.close()
    }

    private fun resolve(property: VehicleProperty): Pair<Int, Int>? =
        bindings[property] ?: platform.bindings[property.key]?.let { it.nativeId to it.areaId }

    private fun decode(property: VehicleProperty, raw: Any?): PropertyValue? {
        if (raw == null) return null
        when (property.key) {
            WellKnownProperties.HVAC_TEMP_C.key -> {
                val f = (raw as? Number)?.toFloat() ?: return toValue(raw)
                return PropertyValue.FloatVal(Ihu629gCodecs.tempRawToC(f))
            }
            WellKnownProperties.CHARGE_PLUG.key -> {
                val i = (raw as? Number)?.toInt() ?: return toValue(raw)
                return PropertyValue.IntVal(if (Ihu629gCodecs.plugConnected(i) == true) 1 else 0)
            }
            WellKnownProperties.PARKING_COMFORT.key -> {
                val i = (raw as? Number)?.toInt() ?: return toValue(raw)
                return PropertyValue.IntVal(if (Ihu629gCodecs.parkModeIsOn(i)) 1 else 0)
            }
        }
        return toValue(raw)
    }

    private fun encode(property: VehicleProperty, value: PropertyValue): PropertyValue {
        when (property.key) {
            WellKnownProperties.HVAC_TEMP_C.key -> {
                val c = when (value) {
                    is PropertyValue.FloatVal -> value.value
                    is PropertyValue.IntVal -> value.value.toFloat()
                    else -> return value
                }
                return PropertyValue.FloatVal(Ihu629gCodecs.tempCToRaw(c))
            }
            WellKnownProperties.PARKING_COMFORT.key -> {
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
                "hvac_temp_c" -> Ihu629gCodecs.tempRawToC(raw.toFloat())
                "speed_kmh" -> AospVehicleIds.speedMsToKmh(raw.toFloat())
                else -> raw.toFloat()
            }
        }
        val mode = intOf("drive_mode")
        val plug = intOf("charge_plug")
        return TelemetrySnapshot(
            gear = intOf("gear"),
            speedKmh = floatOf("speed_kmh"),
            evBatteryPercent = floatOf("ev_battery_percent"),
            rangeKm = floatOf("range_km"),
            driveMode = mode?.let { platform.driveModeEnum[it] ?: "mode:$it" },
            regenLevel = intOf("regen"),
            ignitionState = intOf("ignition"),
            hvacPower = intOf("hvac_power")?.let { it != 0 && it != 2 },
            hvacTempC = floatOf("hvac_temp_c"),
            hvacFan = intOf("hvac_fan"),
            chargeCurrentA = floatOf("charge_current"),
            chargePlugConnected = Ihu629gCodecs.plugConnected(plug),
            extras = mapOf(
                "bridge" to if (backend.available) "ok" else "unavailable",
                "backend" to "vhal",
                "accessMode" to backend.mode.wireName,
                "family" to "flyme",
            ),
        )
    }

    private fun toValue(raw: Any?): PropertyValue? =
        cc.opencar.assistant.integrations.common.toPropertyValue(raw)

    companion object {
        private const val TAG = "Ihu629gSession"
        private const val POLL_MS = 1000L

        private fun wellKnownBindings(platform: PlatformConfig): Map<VehicleProperty, Pair<Int, Int>> {
            val out = mutableMapOf<VehicleProperty, Pair<Int, Int>>()
            for ((key, binding) in platform.bindings) {
                val prop = wellKnownByKey(key) ?: continue
                out[prop] = binding.nativeId to binding.areaId
            }
            return out
        }
    }
}
