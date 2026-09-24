package cc.opencar.assistant.integrations.common

import android.content.Context

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

    override fun close() = bridge.close()
}
