package cc.opencar.assistant.feature.dvr

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import cc.opencar.assistant.api.CameraSource
import cc.opencar.assistant.api.VehicleSession
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class DvrController(
    private val context: Context,
    private val session: VehicleSession,
) {
    private var recorder: MediaRecorder? = null
    private val recording = AtomicBoolean(false)
    private var activeCameraId: String? = null
    private var activeFile: File? = null
    private var segmentJobStartedAt: Long = 0L
    private var storageId: String = STORAGE_APP
    private val singlePreview = CameraPreviewSession(context)
    /** Round-robin grab fills per-camera JPEGs; mosaic owns the unified frame. */
    private val mosaic = MosaicPreviewSession { id ->
        try {
            singlePreview.snapshot(id)
        } finally {
            // Keep cached tiles; only release the Camera1 device.
            singlePreview.stopHardwareOnly()
        }
    }
    private var mosaicWriter: Future<*>? = null
    private val writerExec = Executors.newSingleThreadExecutor()
    var lastError: String? = null
        private set

    fun cameras(): List<CameraSource> = session.cameras().ifEmpty {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.mapIndexed { i, id -> CameraSource(id, "Cam $i", id) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isRecording(): Boolean = recording.get()

    fun storageTargets(): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        val app = File(context.getExternalFilesDir(null), "dvr").also { it.mkdirs() }
        out += mapOf(
            "id" to STORAGE_APP,
            "labelKey" to "cameras.storage.app",
            "label" to "App storage",
            "path" to app.absolutePath,
            "writable" to app.canWrite(),
        )
        val primary = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        runCatching {
            val dir = File(primary, "OpenCarAssistant").also { it.mkdirs() }
            out += mapOf(
                "id" to STORAGE_PRIMARY,
                "labelKey" to "cameras.storage.primary",
                "label" to "Internal shared",
                "path" to dir.absolutePath,
                "writable" to dir.canWrite(),
            )
        }
        try {
            val sm = context.getSystemService(StorageManager::class.java)
            sm.storageVolumes.forEachIndexed { i, vol ->
                if (vol.isPrimary) return@forEachIndexed
                val desc = vol.getDescription(context) ?: "vol$i"
                val state = vol.state
                val path = if (Build.VERSION.SDK_INT >= 30) {
                    vol.directory
                } else {
                    @Suppress("DEPRECATION")
                    vol.javaClass.methods
                        .firstOrNull { it.name == "getPathFile" }
                        ?.invoke(vol) as? File
                }
                if (path != null && state == Environment.MEDIA_MOUNTED) {
                    val dir = File(path, "OpenCarAssistant/dvr").also { it.mkdirs() }
                    out += mapOf(
                        "id" to "vol_$i",
                        "labelKey" to "cameras.storage.sd",
                        "label" to desc,
                        "path" to dir.absolutePath,
                        "writable" to dir.canWrite(),
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "storageVolumes: ${t.message}")
        }
        return out
    }

    fun setStorage(id: String?): Boolean {
        val targets = storageTargets()
        val match = targets.firstOrNull { it["id"] == id } ?: targets.firstOrNull() ?: return false
        storageId = match["id"] as String
        return true
    }

    fun outputDir(): File {
        val path = storageTargets().firstOrNull { it["id"] == storageId }?.get("path") as? String
        val dir = if (path != null) File(path) else File(context.getExternalFilesDir(null), "dvr")
        dir.mkdirs()
        return dir
    }

    /** Merged mosaic live preview across all cameras. */
    fun startPreview(cameraId: String? = null): Boolean {
        return if (cameraId != null) {
            mosaic.stop()
            singlePreview.start(cameraId)
        } else {
            singlePreview.stop()
            val ids = cameras().map { it.cameraId }
            mosaic.start(ids)
        }
    }

    fun stopPreview() {
        mosaic.stop()
        singlePreview.stop()
        singlePreview.clearFrames()
    }

    /** Unified mosaic JPEG when merged preview is active; else the open camera's frame. */
    fun latestPreviewJpeg(): ByteArray? = mosaic.latestJpeg() ?: singlePreview.latestJpeg()

    /** One camera's JPEG, or the unified mosaic when [cameraId] is null. */
    fun snapshotJpeg(cameraId: String? = null): ByteArray? {
        return if (cameraId != null) {
            singlePreview.snapshot(cameraId)
        } else {
            startPreview(null)
            mosaic.latestJpeg()
        }
    }

    fun latestJpegForCamera(cameraId: String): ByteArray? =
        mosaic.latestTile(cameraId) ?: singlePreview.latestJpeg(cameraId)

    fun previewStatus(): Map<String, Any?> {
        val m = mosaic.status()
        return if (m["previewRunning"] == true) m else singlePreview.status() + mapOf("merged" to false)
    }

    fun start(cameraId: String? = null): Boolean {
        if (recording.get()) return true
        // Always record merged feed (ignore single-cam for product UX)
        if (!startPreview(null) && latestPreviewJpeg() == null) {
            lastError = mosaic.lastError ?: singlePreview.lastError ?: "Preview failed"
            // still allow segment marker
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(outputDir(), "oca_merged_$stamp.mjpeg")
        return try {
            val fos = FileOutputStream(file)
            recording.set(true)
            activeCameraId = "merged"
            activeFile = file
            segmentJobStartedAt = System.currentTimeMillis()
            lastError = null
            mosaicWriter = writerExec.submit {
                try {
                    while (recording.get()) {
                        val jpeg = latestPreviewJpeg()
                        if (jpeg != null) {
                            fos.write(jpeg)
                            // simple concatenated JPEG stream (playable as MJPEG)
                        }
                        Thread.sleep(100)
                    }
                } catch (t: Throwable) {
                    lastError = t.message
                    Log.w(TAG, "mjpeg write: ${t.message}")
                } finally {
                    runCatching { fos.flush(); fos.close() }
                }
            }
            true
        } catch (t: Throwable) {
            lastError = t.message
            Log.e(TAG, "start failed", t)
            val marker = File(outputDir(), "oca_merged_$stamp.seg")
            runCatching {
                marker.writeText(
                    "OCA merged DVR segment\ncameras=${cameras().map { it.cameraId }}\nerror=${t.message}\n",
                )
                activeCameraId = "merged"
                activeFile = marker
                segmentJobStartedAt = System.currentTimeMillis()
                recording.set(true)
                lastError = "Fallback segment: ${t.message}"
                return true
            }
            false
        }
    }

    fun stop() {
        recording.set(false)
        mosaicWriter?.cancel(false)
        mosaicWriter = null
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null
        activeFile?.takeIf { it.extension == "seg" }?.appendText(
            "stopped=${System.currentTimeMillis()}\ndurationMs=${System.currentTimeMillis() - segmentJobStartedAt}\n",
        )
        activeCameraId = null
        activeFile = null
        segmentJobStartedAt = 0L
    }

    fun toggleRecording(): Map<String, Any?> {
        return if (recording.get()) {
            stop()
            mapOf("ok" to true, "recording" to false, "status" to status())
        } else {
            val ok = start(null)
            mapOf("ok" to ok, "recording" to recording.get(), "status" to status())
        }
    }

    fun status(): Map<String, Any?> = mapOf(
        "recording" to recording.get(),
        "cameraId" to (activeCameraId ?: "merged"),
        "merged" to true,
        "cameras" to cameras().map { mapOf("id" to it.cameraId, "label" to it.label) },
        "storageId" to storageId,
        "storages" to storageTargets(),
        "outputDir" to outputDir().absolutePath,
        "activeFile" to activeFile?.absolutePath,
        "elapsedMs" to if (recording.get() && segmentJobStartedAt > 0) {
            System.currentTimeMillis() - segmentJobStartedAt
        } else {
            null
        },
        "lastError" to lastError,
        "preview" to previewStatus(),
    )

    companion object {
        private const val TAG = "OcaDvr"
        const val STORAGE_APP = "app"
        const val STORAGE_PRIMARY = "primary"
    }
}