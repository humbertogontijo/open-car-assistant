package cc.opencar.assistant.feature.dvr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Round-robins Camera1 devices (most HUs allow only one open at a time) and
 * paints the latest tile frames into a single mosaic JPEG for preview/record.
 */
class MosaicPreviewSession(
    private val grabber: (cameraId: String) -> ByteArray?,
) {
    private val tiles = AtomicReference<Map<String, ByteArray>>(emptyMap())
    private val mosaicJpeg = AtomicReference<ByteArray?>(null)
    private val running = AtomicBoolean(false)
    private var cameraIds: List<String> = emptyList()
    private var job: Future<*>? = null
    private val exec = Executors.newSingleThreadExecutor()
    var lastError: String? = null
        private set

    fun isRunning(): Boolean = running.get()

    /** Single unified mosaic JPEG of all cameras. */
    fun latestJpeg(): ByteArray? = mosaicJpeg.get()

    /** Individual tile for one camera (last successful grab). */
    fun latestTile(cameraId: String): ByteArray? = tiles.get()[cameraId]

    fun latestTiles(): Map<String, ByteArray> = tiles.get()

    @Synchronized
    fun start(ids: List<String>): Boolean {
        if (ids.isEmpty()) {
            lastError = "No cameras"
            return false
        }
        if (running.get() && cameraIds == ids) return true
        stopInternal()
        cameraIds = ids
        running.set(true)
        job = exec.submit {
            var idx = 0
            while (running.get()) {
                val id = cameraIds[idx % cameraIds.size]
                idx++
                try {
                    val frame = grabber(id)
                    if (frame != null) {
                        val next = tiles.get().toMutableMap()
                        next[id] = frame
                        tiles.set(next)
                        mosaicJpeg.set(compose(next, cameraIds))
                        lastError = null
                    }
                } catch (t: Throwable) {
                    lastError = t.message
                    Log.w(TAG, "tile grab $id failed", t)
                }
                try {
                    Thread.sleep(if (cameraIds.size <= 1) 80 else 220)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        // Wait briefly for first mosaic
        val deadline = System.currentTimeMillis() + 4000
        while (mosaicJpeg.get() == null && System.currentTimeMillis() < deadline && running.get()) {
            Thread.sleep(50)
        }
        return mosaicJpeg.get() != null || running.get()
    }

    @Synchronized
    fun stop() = stopInternal()

    fun status(): Map<String, Any?> = mapOf(
        "previewRunning" to running.get(),
        "merged" to true,
        "cameras" to cameraIds,
        "tiles" to tiles.get().keys.toList(),
        "hasFrame" to (mosaicJpeg.get() != null),
        "frameBytes" to mosaicJpeg.get()?.size,
        "lastError" to lastError,
    )

    private fun stopInternal() {
        running.set(false)
        job?.cancel(true)
        job = null
        mosaicJpeg.set(null)
        tiles.set(emptyMap())
        cameraIds = emptyList()
    }

    private fun compose(frames: Map<String, ByteArray>, order: List<String>): ByteArray {
        val n = order.size.coerceAtLeast(1)
        val cols = if (n <= 1) 1 else if (n <= 4) 2 else 3
        val rows = (n + cols - 1) / cols
        val cellW = 640
        val cellH = 360
        val bmp = Bitmap.createBitmap(cols * cellW, rows * cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
        }
        order.forEachIndexed { i, id ->
            val col = i % cols
            val row = i / cols
            val dst = Rect(col * cellW, row * cellH, (col + 1) * cellW, (row + 1) * cellH)
            val jpeg = frames[id]
            if (jpeg != null) {
                val tile = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                if (tile != null) {
                    canvas.drawBitmap(tile, null, dst, null)
                    tile.recycle()
                }
            }
            canvas.drawText(id, dst.left + 12f, dst.top + 36f, labelPaint)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "OcaMosaic"
    }
}
