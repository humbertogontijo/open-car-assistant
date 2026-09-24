package android.car.hardware

class CarPropertyValue<T>(
    val propertyId: Int,
    val areaId: Int,
    val status: Int,
    val timestamp: Long,
    val value: T?,
) {
    companion object {
        const val STATUS_AVAILABLE = 0
        const val STATUS_UNAVAILABLE = 1
        const val STATUS_ERROR = 2
    }
}
