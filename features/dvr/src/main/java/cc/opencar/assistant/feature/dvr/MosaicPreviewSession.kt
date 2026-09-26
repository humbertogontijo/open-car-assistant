package cc.opencar.assistant.feature.dvr

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns concurrent camera opens for the shared mosaic. Composition and encode
 * live in [SharedH264Pipeline] (GLES → MediaCodec).
 *
 * Mosaic canvas size and fps are derived from open camera preview sizes / rates.
 */
class MosaicPreviewSession(
    private val cameras: CameraPreviewSession,
) {
    private val running = AtomicBoolean(false)
    private var orderedIds: List<String> = emptyList()
    @Volatile private var captureMode: String = "idle"
    @Volatile private var mosaicW: Int = 640
    @Volatile private var mosaicH: Int = 480
    @Volatile private var sourceFps: Int = DEFAULT_FPS
    @Volatile var onStopped: (() -> Unit)? = null
    var lastError: String? = null
        private set

    fun isRunning(): Boolean = running.get()
    fun cameraIds(): List<String> = orderedIds
    fun camerasSession(): CameraPreviewSession = cameras
    fun captureMode(): String = captureMode
    fun mosaicWidth(): Int = mosaicW
    fun mosaicHeight(): Int = mosaicH
    /** Encoder / draw hint from camera preview fps ranges. */
    fun sourceFps(): Int = sourceFps

    fun frameIntervalMs(): Long =
        (1000L / sourceFps.coerceIn(MIN_FPS, MAX_FPS)).coerceAtLeast(33L)

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
        val sizes = cameras.previewSizes(ids)
        val (w, h) = MosaicLayout.canvasSize(sizes)
        mosaicW = w
        mosaicH = h
        sourceFps = cameras.previewFps(ids).coerceIn(MIN_FPS, MAX_FPS)
        captureMode = "concurrent"
        running.set(true)
        lastError = null
        Log.i(TAG, "mosaic cameras ready (${ids.size}) ${w}x${h}@${sourceFps}fps")
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
        "width" to mosaicWidth(),
        "height" to mosaicHeight(),
        "fps" to sourceFps,
        "frameIntervalMs" to frameIntervalMs(),
        "format" to "h264",
        "lastError" to lastError,
        "camera" to cameras.status(),
        "tileSizes" to orderedIds.map { id ->
            val (w, h) = cameras.previewSize(id) ?: (0 to 0)
            mapOf("id" to id, "width" to w, "height" to h)
        },
    )

    private fun stopInternal() {
        running.set(false)
        onStopped?.invoke()
        cameras.stop()
        orderedIds = emptyList()
        captureMode = "idle"
    }

    companion object {
        private const val TAG = "OaaMosaic"
        const val DEFAULT_FPS = 15
        const val MIN_FPS = 1
        const val MAX_FPS = 30
    }
}
