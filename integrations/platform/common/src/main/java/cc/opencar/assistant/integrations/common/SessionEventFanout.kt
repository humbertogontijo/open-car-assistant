package cc.opencar.assistant.integrations.common

import cc.opencar.assistant.api.TelemetrySnapshot
import cc.opencar.assistant.api.VehicleEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared telemetry edge detection + entity-value fan-out for vehicle sessions.
 * Keeps Antora / IHU observe+poll loops thin and consistent.
 */
class SessionEventFanout(
    private val telemetry: MutableStateFlow<TelemetrySnapshot>,
    private val events: MutableSharedFlow<VehicleEvent>,
) {
    private var lastGear: Int? = null
    private var lastIgnition: Int? = null
    private val lastEntityDisplay = mutableMapOf<String, String?>()

    /** Publish [snap] if changed; emit gear/ignition edges after the first seed. */
    fun publishTelemetry(snap: TelemetrySnapshot) {
        if (snap == telemetry.value) return
        val prevGear = lastGear
        val prevIgn = lastIgnition
        telemetry.value = snap
        val gear = snap.gear
        if (gear != null && gear != prevGear) {
            if (prevGear != null) {
                events.tryEmit(VehicleEvent.GearChanged(gear))
            }
            lastGear = gear
        }
        val ign = snap.ignitionState
        if (ign != null && prevIgn != null && ign != prevIgn) {
            events.tryEmit(VehicleEvent.IgnitionChanged(ign))
        }
        if (ign != null) lastIgnition = ign
    }

    fun onPropertyUpdate(update: PropertyUpdate, entityByProp: Map<Int, String>) {
        val entityId = entityByProp[update.propId] ?: return
        val display = toPropertyValue(update.value)?.display()
            ?: update.value?.toString()
            ?: return
        emitEntityIfChanged(entityId, display)
    }

    fun emitBoundSnapshots(
        bindings: Map<String, PlatformConfig.Binding>,
        read: (nativeId: Int, areaId: Int) -> Any?,
    ) {
        for ((entityId, binding) in bindings) {
            try {
                val raw = read(binding.nativeId, binding.areaId) ?: continue
                val display = toPropertyValue(raw)?.display() ?: continue
                emitEntityIfChanged(entityId, display)
            } catch (_: Throwable) { /* best-effort */ }
        }
    }

    private fun emitEntityIfChanged(entityId: String, display: String?) {
        val prev = lastEntityDisplay.put(entityId, display)
        if (prev == null || prev == display) return
        events.tryEmit(VehicleEvent.EntityValueChanged(entityId, display))
    }
}

/** Native prop id → product entity id (first binding wins). */
fun PlatformConfig.entityByProp(): Map<Int, String> = buildMap {
    for ((entityId, binding) in bindings) {
        putIfAbsent(binding.nativeId, entityId)
    }
}
