package cc.opencar.assistant.feature.dvr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LAN preview capture. Antora exposes legacy Camera HAL devices — Camera1
 * preview callbacks are far more reliable there than Camera2+ImageReader alone.
 *
 * Frames are stored **per cameraId**. Only one Camera1 device can be open at a
 * time on most HUs; [snapshot] / round-robin open→grab→close still fills the
 * per-id map so callers never confuse one camera's JPEG with another's.
 */
class CameraPreviewSession(private val context: Context) : AutoCloseable {
    /** Latest JPEG for each camera that has successfully produced a frame. */
    private val latestByCamera = ConcurrentHashMap<String, ByteArray>()
    private val running = AtomicBoolean(false)
    private var cameraId: String? = null
    private var camera: Camera? = null
    private var surfaceTexture: SurfaceTexture? = null
    var lastError: String? = null
        private set

    fun isRunning(): Boolean = running.get()
    fun activeCameraId(): String? = cameraId

    /** Frame for [cameraId], or the currently open camera if null. */
    fun latestJpeg(cameraId: String? = null): ByteArray? {
        val id = cameraId ?: this.cameraId ?: return null
        return latestByCamera[id]
    }

    fun latestByCamera(): Map<String, ByteArray> = latestByCamera.toMap()

    @Synchronized
    fun start(preferredCameraId: String? = null): Boolean {
        if (running.get() && (preferredCameraId == null || preferredCameraId == cameraId)) {
            return true
        }
        stopHardware()
        val pmState = context.checkSelfPermission(Manifest.permission.CAMERA)
        Log.i(TAG, "CAMERA checkSelfPermission=$pmState (granted=${PackageManager.PERMISSION_GRANTED})")
        val id = preferredCameraId ?: "0"
        val camIndex = resolveCamera1Index(id)
        return try {
            @Suppress("DEPRECATION")
            val cam = Camera.open(camIndex)
            camera = cam
            val params = cam.parameters
            val (pw, ph) = choosePreviewSize(params.supportedPreviewSizes)
            params.setPreviewSize(pw, ph)
            params.previewFormat = ImageFormat.NV21
            runCatching { cam.parameters = params }
            val st = SurfaceTexture(10 + camIndex)
            st.setDefaultBufferSize(pw, ph)
            surfaceTexture = st
            cam.setPreviewTexture(st)
            cameraId = id
            running.set(true)
            // Clear only this camera's slot so we wait for a fresh frame from
            // *this* device, without wiping other cameras' cached tiles.
            latestByCamera.remove(id)
            cam.setPreviewCallback { data, _ ->
                if (data == null) return@setPreviewCallback
                if (!running.get() || cameraId != id) return@setPreviewCallback
                runCatching {
                    val yuv = YuvImage(data, ImageFormat.NV21, pw, ph, null)
                    val out = ByteArrayOutputStream()
                    yuv.compressToJpeg(Rect(0, 0, pw, ph), 70, out)
                    latestByCamera[id] = out.toByteArray()
                }
            }
            cam.startPreview()
            val deadline = System.currentTimeMillis() + 2500
            while (latestByCamera[id] == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(40)
            }
            lastError = if (latestByCamera[id] == null) {
                "Preview running but no frames yet (cam=$id idx=$camIndex ${pw}x${ph})"
            } else {
                null
            }
            Log.i(TAG, "Camera1 preview cam=$id idx=$camIndex ${pw}x${ph} frame=${latestByCamera[id]?.size}")
            true
        } catch (t: Throwable) {
            lastError = t.message
            Log.e(TAG, "preview start failed cam=$id idx=$camIndex", t)
            stopHardware()
            false
        }
    }

    @Synchronized
    fun stop() {
        stopHardware()
    }

    /** Release hardware but keep per-camera JPEG cache for mosaic tiles. */
    fun stopHardwareOnly() = stopHardware()

    fun clearFrames() {
        latestByCamera.clear()
    }

    fun snapshot(preferredCameraId: String? = null): ByteArray? {
        val id = preferredCameraId ?: "0"
        if (!start(id)) return null
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            latestByCamera[id]?.let { return it }
            Thread.sleep(40)
        }
        return latestByCamera[id]
    }

    fun status(): Map<String, Any?> = mapOf(
        "previewRunning" to running.get(),
        "previewCameraId" to cameraId,
        "cachedCameras" to latestByCamera.keys.toList(),
        "hasFrame" to (latestJpeg() != null),
        "frameBytes" to (latestJpeg()?.size),
        "lastError" to lastError,
    )

    override fun close() {
        stopHardware()
        latestByCamera.clear()
    }

    private fun stopHardware() {
        running.set(false)
        runCatching {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
        }
        camera = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
        cameraId = null
    }

    @Suppress("DEPRECATION")
    private fun choosePreviewSize(sizes: List<Camera.Size>?): Pair<Int, Int> {
        if (sizes.isNullOrEmpty()) return 640 to 480
        val best = sizes
            .filter { it.width <= 1280 && it.height <= 720 }
            .minByOrNull { kotlin.math.abs(it.width * it.height - 640 * 480) }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: sizes.first()
        return best.width to best.height
    }

    /**
     * Map a Camera2-style id (or Camera1 index string) onto Camera.open(index).
     * Prefer an exact integer id in range; otherwise use the id's position in
     * CameraManager.cameraIdList so non-numeric HAL ids still round-robin.
     */
    @Suppress("DEPRECATION")
    private fun resolveCamera1Index(id: String): Int {
        val n = Camera.getNumberOfCameras().coerceAtLeast(1)
        id.toIntOrNull()?.takeIf { it in 0 until n }?.let { return it }
        val listed = runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList
        }.getOrNull()
        val byList = listed?.indexOf(id)?.takeIf { it >= 0 }
        if (byList != null && byList < n) return byList
        return 0
    }

    companion object {
        private const val TAG = "OcaCamPreview"
    }
}
