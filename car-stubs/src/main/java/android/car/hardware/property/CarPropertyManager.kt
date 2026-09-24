package android.car.hardware.property

import android.car.hardware.CarPropertyValue

class CarPropertyManager {
    interface CarPropertyEventCallback {
        fun onChangeEvent(value: CarPropertyValue<*>)
        fun onErrorEvent(propId: Int, zone: Int)
    }

    fun getProperty(clazz: Class<*>, propId: Int, areaId: Int): CarPropertyValue<*>? = null
    fun <T> getProperty(propId: Int, areaId: Int): CarPropertyValue<T>? = null
    fun setProperty(clazz: Class<*>, propId: Int, areaId: Int, value: Any?) {}
    fun registerCallback(callback: CarPropertyEventCallback, propId: Int, rate: Float) {}
    fun unregisterCallback(callback: CarPropertyEventCallback) {}
    fun getPropertyList(): List<CarPropertyConfig> = emptyList()
}

class CarPropertyConfig(
    val propertyId: Int,
    val areaIds: IntArray = intArrayOf(0),
)
