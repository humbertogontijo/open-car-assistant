package cc.opencar.assistant.integrations.common

/**
 * Property read/write facade for AAOS vehicle properties.
 * Integrations pick a transport (CarPropertyManager, gRPC, …) per install mode.
 */
interface VehiclePropertyBackend : AutoCloseable {
    val mode: PropertyAccessMode
    val available: Boolean

    fun read(propId: Int, areaId: Int): Any?
    fun readDetailed(propId: Int, areaId: Int): DetailedRead
    fun writeInt(propId: Int, areaId: Int, value: Int): Boolean
    fun writeFloat(propId: Int, areaId: Int, value: Float): Boolean
    fun writeBoolean(propId: Int, areaId: Int, value: Boolean): Boolean

    sealed class DetailedRead {
        data class Ok(val value: Any?) : DetailedRead()
        data class Denied(val permission: String?, val message: String?) : DetailedRead()
        data class Failed(val message: String?) : DetailedRead()
        data object Empty : DetailedRead()
        data object Unavailable : DetailedRead()
    }
}

/**
 * How a session talks to VHAL.
 *
 * - [CAR_PROPERTY]: [android.car.CarPropertyManager] (typically privileged / priv-app)
 * - [GRPC]: platform-specific unprivileged transport (e.g. Antora VenusVehicleServer)
 */
enum class PropertyAccessMode(val wireName: String) {
    CAR_PROPERTY("car_property"),
    GRPC("grpc"),
}
