package cc.opencar.assistant.integrations.aaos

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** AAOS [android.car.hardware.property.CarPropertyManager] transport. */
class CarPropertyBackend(context: Context) : VehiclePropertyBackend {
    private val bridge = CarPropertyBridge(context)

    override val mode: PropertyAccessMode = PropertyAccessMode.CAR_PROPERTY
    override val available: Boolean get() = bridge.available

    override fun read(propId: Int, areaId: Int): Any? = bridge.read(propId, areaId)

    override fun readDetailed(propId: Int, areaId: Int): VehiclePropertyBackend.DetailedRead =
        when (val d = bridge.readDetailed(propId, areaId)) {
            is CarPropertyBridge.DetailedRead.Ok -> VehiclePropertyBackend.DetailedRead.Ok(d.value)
            is CarPropertyBridge.DetailedRead.Denied ->
                VehiclePropertyBackend.DetailedRead.Denied(d.permission, d.message)
            is CarPropertyBridge.DetailedRead.Failed ->
                VehiclePropertyBackend.DetailedRead.Failed(d.message)
            CarPropertyBridge.DetailedRead.Empty -> VehiclePropertyBackend.DetailedRead.Empty
            CarPropertyBridge.DetailedRead.Unavailable ->
                VehiclePropertyBackend.DetailedRead.Unavailable
        }

    override fun writeInt(propId: Int, areaId: Int, value: Int): Boolean =
        bridge.writeInt(propId, areaId, value)

    override fun writeFloat(propId: Int, areaId: Int, value: Float): Boolean =
        bridge.writeFloat(propId, areaId, value)

    override fun writeBoolean(propId: Int, areaId: Int, value: Boolean): Boolean =
        bridge.writeBoolean(propId, areaId, value)

    /**
     * Registers [CarPropertyBridge.registerCallback] for [propIds].
     * Returns null when ids are missing or the platform callback API is unavailable
     * (session must poll).
     */
    override fun observe(propIds: IntArray?): Flow<PropertyUpdate>? {
        if (propIds == null || propIds.isEmpty()) return null
        if (!bridge.available) return null
        val updates = MutableSharedFlow<PropertyUpdate>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val ok = bridge.registerCallback(propIds, RATE_HZ) { propId, value ->
            updates.tryEmit(PropertyUpdate(propId = propId, areaId = 0, value = value))
        }
        if (!ok) {
            Log.d(TAG, "observe unavailable — session will poll")
            return null
        }
        return updates.asSharedFlow()
    }

    override fun close() = bridge.close()

    companion object {
        private const val TAG = "CarPropertyBackend"
        private const val RATE_HZ = 5f
    }
}
