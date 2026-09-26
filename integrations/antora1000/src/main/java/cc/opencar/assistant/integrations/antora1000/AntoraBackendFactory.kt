package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import android.util.Log
import cc.opencar.assistant.integrations.aaos.CarPropertyBackend
import cc.opencar.assistant.integrations.aaos.VehiclePropertyBackend

/**
 * Antora VHAL transport: VenusVehicleServer gRPC (user-space `/data` install).
 * Falls back to CarPropertyManager only if gRPC is unreachable (reads may be denied).
 */
object AntoraBackendFactory {
    private const val TAG = "OaaAntoraBackend"

    fun create(context: Context): VehiclePropertyBackend {
        val grpc = GrpcVhalBackend()
        if (grpc.connect()) {
            Log.i(TAG, "accessMode=grpc (VenusVehicleServer)")
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
