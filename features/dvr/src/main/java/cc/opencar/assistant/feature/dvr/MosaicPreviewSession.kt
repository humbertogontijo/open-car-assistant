package cc.opencar.assistant.feature.dvr

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns concurrent camera opens for the shared mosaic. Composition and encode
 * live in [SharedH264Pipeline] (GLES → MediaCodec).
 */
class MosaicPreviewSession(
    private val cameras: CameraPreviewSession,
) {
    private val running = AtomicBoolean(false)
    private var orderedIds: List<String> = emptyList()
    @Volatile private var captureMode: String = "idle"
    @Volatile var targetHeight: Int = DEFAULT_HEIGHT
    @Volatile var targetFps: Int = DEFAULT_FPS
    @Volatile var onStopped: (() -> Unit)? = null
    var lastError: String? = null
        private set

    fun isRunning(): Boolean = running.get()
    fun cameraIds(): List<String> = orderedIds
    fun camerasSession(): CameraPreviewSession = cameras
    fun captureMode(): String = captureMode
    fun mosaicWidth(): Int = normalizeHeight(targetHeight) * 16 / 9
    fun mosaicHeight(): Int = normalizeHeight(targetHeight)

    fun frameIntervalMs(): Long =
        (1000L / targetFps.coerceIn(MIN_FPS, MAX_FPS)).coerceAtLeast(66L)

    fun applyQuality(fps: Int, height: Int) {
        targetFps = fps.coerceIn(MIN_FPS, MAX_FPS)
        targetHeight = normalizeHeight(height)
    }

    @Synchronized
    fun start(ids: List<String>): Boolean {
        if (ids.isEmpty()) {
            lastError = "No cameras"
            return false
        }
        if (running.get() && orderedIds == ids) return true
        stopInternal()
        orderedIds = ids
        if (!cameras.startAll(ids)) {
            lastError = cameras.lastError ?: "Concurrent open failed"
            orderedIds = emptyList()
            return false
        }
        captureMode = "concurrent"
        running.set(true)
        lastError = null
        Log.i(TAG, "mosaic cameras ready (${ids.size})")
        return true
    }

    @Synchronized
    fun stop() = stopInternal()

    fun status(): Map<String, Any?> = mapOf(
        "previewRunning" to running.get(),
        "merged" to true,
        "captureMode" to captureMode,
        "cameras" to orderedIds,
        "size" to "${mosaicWidth()}x${mosaicHeight()}",
        "fps" to targetFps,
        "mosaicHeight" to targetHeight,
        "frameIntervalMs" to frameIntervalMs(),
        "format" to "h264",
        "lastError" to lastError,
        "camera" to cameras.status(),
    )

    private fun stopInternal() {
        running.set(false)
        onStopped?.invoke()
        cameras.stop()
        orderedIds = emptyList()
        captureMode = "idle"
    }

    companion object {
        private const val TAG = "OcaMosaic"
        const val DEFAULT_FPS = 5
        const val DEFAULT_HEIGHT = 720
        const val MIN_FPS = 1
        const val MAX_FPS = 15

        fun normalizeHeight(h: Int): Int = when {
            h <= 480 -> 480
            h <= 720 -> 720
            else -> 1080
        }
    }
}
