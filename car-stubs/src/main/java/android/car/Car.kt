package android.car

import android.content.Context
import android.content.ServiceConnection
import android.os.Handler

/**
 * Compile-time stub. On AAOS the framework provides the real class at runtime.
 */
class Car private constructor() {
    fun getCarManager(serviceName: String): Any? = null
    fun disconnect() {}
    fun isConnected(): Boolean = false

    companion object {
        const val PROPERTY_SERVICE = "property"

        @JvmStatic
        fun createCar(context: Context): Car? = Car()

        @JvmStatic
        fun createCar(context: Context, handler: Handler?): Car? = Car()

        @JvmStatic
        fun createCar(
            context: Context,
            serviceConnectionListener: ServiceConnection?,
            handler: Handler?,
        ): Car? = Car()
    }
}
