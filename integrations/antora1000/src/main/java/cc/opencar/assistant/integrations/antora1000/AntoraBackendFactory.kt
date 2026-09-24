package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import cc.opencar.assistant.integrations.common.CarPropertyBackend
import cc.opencar.assistant.integrations.common.VehiclePropertyBackend

/**
 * Chooses Antora VHAL transport:
 * - privileged (`CAR_VENDOR_EXTENSION` granted) → CarPropertyManager
 * - otherwise → VenusVehicleServer gRPC
 */
object AntoraBackendFactory {
    private const val TAG = "OcaAntoraBackend"
    private const val VENDOR_PERM = "android.car.permission.CAR_VENDOR_EXTENSION"

    fun create(context: Context): VehiclePropertyBackend {
        val privileged =
            ContextCompat.checkSelfPermission(context, VENDOR_PERM) == PackageManager.PERMISSION_GRANTED
        if (privileged) {
            val car = CarPropertyBackend(context)
            if (car.available) {
                Log.i(TAG, "accessMode=car_property (privileged)")
                return car
            }
            Log.w(TAG, "privileged but CarPropertyManager unavailable — trying gRPC")
        }
        val grpc = GrpcVhalBackend()
        if (grpc.connect()) {
            Log.i(TAG, "accessMode=grpc (unprivileged VenusVehicleServer)")
            return grpc
        }
        val fallback = CarPropertyBackend(context)
        Log.w(
            TAG,
            "gRPC unavailable — falling back to car_property (reads may be denied)",
        )
        return fallback
    }
}
