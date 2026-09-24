package cc.opencar.assistant.feature.dvr

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import cc.opencar.assistant.api.CameraSource
import cc.opencar.assistant.api.VehicleSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DvrController(
    private val context: Context,
    private val session: VehicleSession,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val recording = AtomicBoolean(false)
    private var activeCameraId: String? = null
    private var activeFile: File? = null
    /** Wall-clock start of the current recording session (segment or DVR). */
    private var recordingStartedAt: Long = 0L
    /** Wall-clock start of the current output file (rotates with each clip). */
    private var segmentJobStartedAt: Long = 0L
    private val segmentBytes = AtomicLong(0L)
    @Volatile private var mode: String = MODE_OFF
    @Volatile private var storageId: String = STORAGE_APP
    @Volatile private var maxTotalMb: Int = DEFAULT_MAX_TOTAL_MB
    @Volatile private var maxAgeDays: Int = DEFAULT_MAX_AGE_DAYS
    @Volatile private var storageNote: String? = null

    @Volatile private var cachedTargets: List<Map<String, Any?>>? = null
    @Volatile private var cachedTargetsAt: Long = 0L

    private val singlePreview = CameraPreviewSession(context)
    private val mosaic = MosaicPreviewSession(singlePreview)
    private val hub = SharedMosaicHub(mosaic) {
        ensureMosaicStarted()
    }
    /** Extra hub seat held while the DVR writer is running. */
    private var recordingHubHeld = false
    private var mosaicWriter: Future<*>? = null
    private val writerExec = Executors.newSingleThreadExecutor { r ->
        Thread(r, "oca-dvr-writer").apply { isDaemon = true }
    }
    private var h264: SharedH264Pipeline? = null
    @Volatile private var streamFormat: String = "off"
    @Volatile private var camera2ProbeReport: Map<String, Any?>? = null
    var lastError: String? = null
        private set

    init {
        storageId = prefs.getString(KEY_STORAGE, STORAGE_APP) ?: STORAGE_APP
        maxTotalMb = prefs.getInt(KEY_MAX_TOTAL_MB, DEFAULT_MAX_TOTAL_MB).coerceIn(256, 65536)
        maxAgeDays = prefs.getInt(KEY_MAX_AGE_DAYS, DEFAULT_MAX_AGE_DAYS).coerceAtLeast(0)
        applyPlatformStreamConfig()
        mosaic.onStopped = {
            h264?.stop()
            h264 = null
            streamFormat = "off"
        }
        val savedMode = prefs.getString(KEY_MODE, MODE_OFF) ?: MODE_OFF
        // Segment is transient; only DVR persists across restarts.
        mode = if (savedMode == MODE_DVR) MODE_DVR else MODE_OFF
        ensureStorageMounted()
        writerExec.execute { runCamera2Probe() }
    }

    /** Pull fps / mosaic height from the active platform (not user prefs). */
    private fun applyPlatformStreamConfig() {
        val cfg = session.dvrStreamConfig()
        mosaic.applyQuality(cfg.fps, cfg.mosaicHeight)
    }

    private fun ensureMosaicStarted(): Boolean {
        val ids = cameras().map { it.cameraId }
        if (ids.isEmpty()) {
            lastError = "No cameras"
            return false
        }
        if (!mosaic.start(ids)) {
            lastError = mosaic.lastError ?: "Mosaic start failed"
            return false
        }
        if (!startH264()) {
            mosaic.stop()
            return false
        }
        return true
    }

    private fun startH264(): Boolean {
        if (h264?.isRunning() == true) return true
        val pipe = SharedH264Pipeline(
            mosaic.mosaicWidth(),
            mosaic.mosaicHeight(),
            mosaic.targetFps,
        )
        val ids = mosaic.cameraIds()
        val ok = pipe.start(ids.size) { textures ->
            singlePreview.rebindPreviewTextures(ids, textures)
        }
        if (!ok) {
            lastError = pipe.lastError ?: "H264 GL pipeline failed"
            Log.e(TAG, "H264 start failed: $lastError")
            pipe.stop()
            return false
        }
        h264 = pipe
        streamFormat = "h264"
        lastError = null
        return true
    }

    private fun runCamera2Probe() {
        runCatching {
            val report = Camera2ConcurrentProbe(context).probe()
            camera2ProbeReport = Camera2ConcurrentProbe(context).statusMap(report)
        }.onFailure {
            camera2ProbeReport = mapOf("error" to it.message)
            Log.w(TAG, "Camera2 probe failed: ${it.message}")
        }
    }

    fun fmp4InitSegment(): ByteArray? = h264?.fmp4Init()
    fun fmp4Fragment(seq: Long): ByteArray? = h264?.fmp4Fragment(seq)
    fun hlsPlaylist(): String? = h264?.hlsPlaylist()
    fun hlsPlaylistBlocking(msn: Long?, timeoutMs: Long = 3_000L): String? =
        h264?.hlsPlaylistBlocking(msn, timeoutMs)

    fun cameras(): List<CameraSource> = session.cameras().ifEmpty {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.mapIndexed { i, id -> CameraSource(id, "Cam $i", id) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isRecording(): Boolean = recording.get()

    fun mode(): String = mode

    fun storageTargets(forceRefresh: Boolean = false): List<Map<String, Any?>> {
        val now = System.currentTimeMillis()
        val cached = cachedTargets
        if (!forceRefresh && cached != null && now - cachedTargetsAt < STORAGE_CACHE_MS) {
            return cached
        }
        val out = mutableListOf<Map<String, Any?>>()
        val app = File(context.getExternalFilesDir(null), "dvr").also { it.mkdirs() }
        out += targetMap(
            id = STORAGE_APP,
            labelKey = "cameras.storage.app",
            label = "App storage",
            dir = app,
            kind = "app",
        )
        val primary = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        runCatching {
            val dir = File(primary, "OpenCarAssistant").also { it.mkdirs() }
            out += targetMap(
                id = STORAGE_PRIMARY,
                labelKey = "cameras.storage.primary",
                label = "Internal shared",
                dir = dir,
                kind = "primary",
            )
        }
        try {
            val sm = context.getSystemService(StorageManager::class.java)
            sm.storageVolumes.forEachIndexed { i, vol ->
                if (vol.isPrimary) return@forEachIndexed
                val desc = vol.getDescription(context) ?: "vol$i"
                val state = vol.state
                val path = volumePath(vol)
                val usb = looksLikeUsb(desc, vol)
                if (path != null && state == Environment.MEDIA_MOUNTED) {
                    val dir = File(path, "OpenCarAssistant/dvr")
                    val created = runCatching { dir.mkdirs(); true }.getOrDefault(false)
                    val writable = created && dir.canWrite()
                    out += targetMap(
                        id = "vol_$i",
                        labelKey = if (usb) "cameras.storage.usb" else "cameras.storage.sd",
                        label = desc,
                        dir = dir,
                        kind = if (usb) "usb" else "sd",
                        writableOverride = writable,
                        available = writable,
                    )
                } else if (usb || isRemovableNonPrimary(vol)) {
                    // Known removable / USB volume that is not mounted — show disabled.
                    out += mapOf(
                        "id" to "vol_$i",
                        "labelKey" to if (usb) "cameras.storage.usb" else "cameras.storage.sd",
                        "label" to desc,
                        "path" to (path?.absolutePath ?: ""),
                        "kind" to if (usb) "usb" else "sd",
                        "writable" to false,
                        "available" to false,
                        "totalBytes" to 0L,
                        "freeBytes" to 0L,
                        "usableBytes" to 0L,
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "storageVolumes: ${t.message}")
        }
        // Always expose a USB slot so users know flash drives are supported.
        if (out.none { it["kind"] == "usb" }) {
            out += mapOf(
                "id" to STORAGE_USB_PLACEHOLDER,
                "labelKey" to "cameras.storage.usb.none",
                "label" to "USB / Flash",
                "path" to "",
                "kind" to "usb",
                "writable" to false,
                "available" to false,
                "totalBytes" to 0L,
                "freeBytes" to 0L,
                "usableBytes" to 0L,
            )
        }
        cachedTargets = out
        cachedTargetsAt = now
        return out
    }

    /** Flat volume list for Android section (same StatFs fields). */
    fun volumeStats(forceRefresh: Boolean = false): List<Map<String, Any?>> =
        storageTargets(forceRefresh)

    fun setStorage(id: String?): Boolean {
        val targets = storageTargets(forceRefresh = true)
        val match = targets.firstOrNull { it["id"] == id } ?: targets.firstOrNull() ?: return false
        storageId = match["id"] as String
        prefs.edit().putString(KEY_STORAGE, storageId).apply()
        storageNote = null
        if (match["writable"] != true) {
            storageNote = "Storage not writable"
            return false
        }
        writerExec.execute { prune() }
        return true
    }

    fun setPolicy(maxTotalMb: Int?, maxAgeDays: Int?): Map<String, Any?> {
        if (maxTotalMb != null) {
            this.maxTotalMb = maxTotalMb.coerceIn(256, 65536)
            prefs.edit().putInt(KEY_MAX_TOTAL_MB, this.maxTotalMb).apply()
        }
        if (maxAgeDays != null) {
            this.maxAgeDays = maxAgeDays.coerceAtLeast(0)
            prefs.edit().putInt(KEY_MAX_AGE_DAYS, this.maxAgeDays).apply()
        }
        writerExec.execute { prune() }
        return status()
    }

    fun frameIntervalMs(): Long = mosaic.frameIntervalMs()

    /**
     * Set recording mode.
     * - [MODE_OFF]: stop writing
     * - [MODE_SEGMENT]: start one-shot segment (does not persist across restarts)
     * - [MODE_DVR]: enable continuous DVR (persisted; wake/sleep lifecycle)
     */
    fun setMode(next: String?): Map<String, Any?> {
        val m = when (next?.lowercase(Locale.US)) {
            MODE_SEGMENT -> MODE_SEGMENT
            MODE_DVR -> MODE_DVR
            else -> MODE_OFF
        }
        when (m) {
            MODE_OFF -> {
                mode = MODE_OFF
                prefs.edit().putString(KEY_MODE, MODE_OFF).apply()
                stop()
            }
            MODE_SEGMENT -> {
                mode = MODE_SEGMENT
                // Segment is transient — do not persist as segment.
                prefs.edit().putString(KEY_MODE, MODE_OFF).apply()
                if (!recording.get()) {
                    val ok = startInternal()
                    if (!ok) {
                        mode = MODE_OFF
                    }
                }
            }
            MODE_DVR -> {
                mode = MODE_DVR
                prefs.edit().putString(KEY_MODE, MODE_DVR).apply()
                if (!recording.get()) {
                    startInternal()
                }
            }
        }
        return mapOf("ok" to true, "mode" to mode, "recording" to recording.get(), "status" to status())
    }

    /** ACC/boot wake: start only when DVR mode is enabled. */
    fun onVehicleWake(source: String = "wake") {
        if (mode != MODE_DVR && prefs.getString(KEY_MODE, MODE_OFF) != MODE_DVR) return
        mode = MODE_DVR
        if (!recording.get()) {
            Log.i(TAG, "DVR auto-start source=$source")
            startInternal()
        }
    }

    /** Screen-off / sleep: stop only when in DVR mode (leave Segment alone). */
    fun onVehicleSleep(source: String = "sleep") {
        if (mode != MODE_DVR) return
        if (recording.get()) {
            Log.i(TAG, "DVR auto-stop source=$source")
            stop()
        }
    }

    fun outputDir(): File {
        ensureStorageMounted()
        val path = storageTargets().firstOrNull { it["id"] == storageId }?.get("path") as? String
        val dir = if (path != null) File(path) else File(context.getExternalFilesDir(null), "dvr")
        dir.mkdirs()
        return dir
    }

    fun startPreview(cameraId: String? = null): Boolean {
        // Merged H.264 mosaic only — single-camera JPEG preview is gone.
        if (cameraId != null) {
            Log.i(TAG, "startPreview(cam=$cameraId): using merged mosaic")
        }
        return hub.ensureStarted()
    }

    fun stopPreview() {
        if (!recording.get() && hub.refCount() == 0) {
            mosaic.stop()
        }
    }

    fun previewStatus(): Map<String, Any?> {
        if (hub.isRunning()) {
            return hub.status() + mapOf(
                "merged" to true,
                "previewRunning" to true,
                "format" to streamFormat,
                "h264" to h264?.status(),
            )
        }
        return mapOf(
            "previewRunning" to false,
            "merged" to true,
            "format" to streamFormat,
            "h264" to h264?.status(),
        )
    }

    /** @deprecated Prefer [setMode]; kept for older clients. */
    fun start(cameraId: String? = null): Boolean {
        if (mode == MODE_OFF) mode = MODE_SEGMENT
        return startInternal()
    }

    private fun startInternal(): Boolean {
        if (recording.get()) return true
        mosaicWriter?.let { prev ->
            recording.set(false)
            runCatching { prev.get() }
            mosaicWriter = null
        }
        ensureStorageMounted()
        if (!recordingHubHeld) {
            if (!hub.acquire()) {
                lastError = hub.lastError() ?: lastError ?: "Mosaic failed"
                return false
            }
            recordingHubHeld = true
        }
        if (h264?.isRunning() != true) {
            lastError = lastError ?: "H264 pipeline not running"
            if (recordingHubHeld) {
                hub.release()
                recordingHubHeld = false
            }
            return false
        }
        recording.set(true)
        recordingStartedAt = System.currentTimeMillis()
        activeCameraId = "merged"
        lastError = null
        mosaicWriter = writerExec.submit { writeLoopH264() }
        return true
    }

    private fun writeLoopH264() {
        val pipe = h264
        if (pipe == null || !pipe.isRunning()) {
            lastError = "H264 pipeline not running"
            recording.set(false)
            if (recordingHubHeld) {
                hub.release()
                recordingHubHeld = false
            }
            return
        }
        try {
            while (recording.get()) {
                if (pipe.activeFile() == null) {
                    val file = newRecordingFile("mp4")
                    if (!pipe.openMp4(file)) {
                        lastError = pipe.lastError ?: "mp4 open failed"
                        break
                    }
                    activeFile = file
                    segmentBytes.set(0L)
                    segmentJobStartedAt = System.currentTimeMillis()
                }
                segmentBytes.set(pipe.bytesWritten())
                val elapsed = System.currentTimeMillis() - segmentJobStartedAt
                val hitLimit =
                    pipe.bytesWritten() >= SEGMENT_MAX_BYTES || elapsed >= SEGMENT_MAX_MS
                if (hitLimit) {
                    val closed = pipe.closeMp4()
                    activeFile = null
                    segmentBytes.set(0L)
                    closed?.let {
                        writeSegmentMetaMp4(it, elapsed.coerceAtLeast(frameIntervalMs()))
                    }
                    prune()
                    if (mode == MODE_SEGMENT) {
                        recording.set(false)
                        mode = MODE_OFF
                        break
                    }
                }
                try {
                    Thread.sleep(frameIntervalMs())
                } catch (_: InterruptedException) {
                    break
                }
            }
        } catch (t: Throwable) {
            lastError = t.message
            Log.w(TAG, "writeLoopH264: ${t.message}")
        } finally {
            val closed = pipe.closeMp4()
            activeFile = null
            val startedAt = segmentJobStartedAt
            segmentJobStartedAt = 0L
            recordingStartedAt = 0L
            segmentBytes.set(0L)
            closed?.let {
                val elapsed = (System.currentTimeMillis() - startedAt)
                    .coerceAtLeast(frameIntervalMs())
                writeSegmentMetaMp4(it, elapsed)
            }
            prune()
            if (mode == MODE_SEGMENT) mode = MODE_OFF
            recording.set(false)
            if (recordingHubHeld) {
                hub.release()
                recordingHubHeld = false
            }
        }
    }

    private fun writeSegmentMetaMp4(file: File, durationMs: Long) {
        runCatching {
            metaFileFor(file).writeText(
                "{\"durationMs\":$durationMs,\"format\":\"mp4\"}\n",
            )
        }
    }

    private fun newRecordingFile(ext: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(outputDir(), "oca_merged_$stamp.$ext")
    }

    fun stop() {
        recording.set(false)
        mosaicWriter?.cancel(false)
        mosaicWriter = null
        // writeLoop finally releases the hub seat + prunes
        activeCameraId = null
    }

    fun toggleRecording(): Map<String, Any?> {
        return if (recording.get()) {
            setMode(MODE_OFF)
        } else {
            setMode(MODE_SEGMENT)
        }
    }

    fun listRecordings(): List<Map<String, Any?>> {
        storageTargets(forceRefresh = true)
        prune()
        return recordingFiles()
            .sortedByDescending { it.lastModified() }
            .map { f ->
                mapOf(
                    "name" to f.name,
                    "size" to f.length(),
                    "mtime" to f.lastModified(),
                    "path" to f.absolutePath,
                    "locked" to isLocked(f),
                    "durationMs" to durationMsFor(f),
                    "frameCount" to frameCountFor(f),
                    "format" to when {
                        f.name.endsWith(".mp4") -> "mp4"
                        f.name.endsWith(".mjpeg") -> "mjpeg"
                        else -> "other"
                    },
                )
            }
    }

    fun recordingFile(name: String): File? {
        if (name.isBlank() || name.contains("..") || name.contains('/') || name.contains('\\')) {
            return null
        }
        if (name.endsWith(".lock") || name.endsWith(".meta")) return null
        val f = File(outputDir(), name)
        if (!f.isFile || !f.canonicalPath.startsWith(outputDir().canonicalPath)) return null
        if (!f.name.startsWith("oca_merged_")) return null
        if (!f.name.endsWith(".mjpeg") && !f.name.endsWith(".mp4") && !f.name.endsWith(".seg")) {
            return null
        }
        return f
    }

    fun deleteRecording(name: String): Boolean {
        val f = recordingFile(name) ?: return false
        lockFileFor(f).delete()
        metaFileFor(f).delete()
        return f.delete()
    }

    fun setLocked(name: String, locked: Boolean): Boolean {
        val f = recordingFile(name) ?: return false
        val lock = lockFileFor(f)
        return if (locked) {
            runCatching { lock.writeText("locked\n"); true }.getOrDefault(false)
        } else {
            !lock.exists() || lock.delete()
        }
    }

    fun prune() {
        val dir = outputDir()
        val active = activeFile?.canonicalPath
        val files = recordingFiles().filter { it.canonicalPath != active }
        if (files.isEmpty()) return

        val cutoff = if (maxAgeDays > 0) {
            System.currentTimeMillis() - maxAgeDays * 86_400_000L
        } else {
            0L
        }
        if (cutoff > 0) {
            files.filter { !isLocked(it) && it.lastModified() < cutoff }.forEach { f ->
                Log.i(TAG, "prune age ${f.name}")
                lockFileFor(f).delete()
                metaFileFor(f).delete()
                f.delete()
            }
        }

        val remaining = recordingFiles().filter { it.canonicalPath != active }
        val unlocked = remaining.filter { !isLocked(it) }.sortedBy { it.lastModified() }
        var total = remaining.sumOf { it.length() }
        val cap = maxTotalMb.toLong() * 1024L * 1024L
        for (f in unlocked) {
            if (total <= cap) break
            Log.i(TAG, "prune size ${f.name}")
            total -= f.length()
            lockFileFor(f).delete()
            metaFileFor(f).delete()
            f.delete()
        }
    }

    fun status(): Map<String, Any?> {
        val targets = storageTargets()
        val selected = targets.firstOrNull { it["id"] == storageId }
        val usage = usageOnCurrentStorage()
        return mapOf(
            "recording" to recording.get(),
            "mode" to mode,
            "format" to streamFormat,
            "cameraId" to (activeCameraId ?: "merged"),
            "merged" to true,
            "stream" to hub.status() + mapOf(
                "format" to streamFormat,
                "h264" to h264?.status(),
                "camera2Probe" to camera2ProbeReport,
            ),
            "cameras" to cameras().map { mapOf("id" to it.cameraId, "label" to it.label) },
            "storageId" to storageId,
            "storages" to targets,
            "storageNote" to storageNote,
            "outputDir" to outputDir().absolutePath,
            "activeFile" to activeFile?.absolutePath,
            "segmentBytes" to if (recording.get()) segmentBytes.get() else 0L,
            "segmentElapsedMs" to if (recording.get() && segmentJobStartedAt > 0) {
                System.currentTimeMillis() - segmentJobStartedAt
            } else {
                0L
            },
            // Session wall-clock (does not reset when DVR rotates files).
            "elapsedMs" to if (recording.get() && recordingStartedAt > 0) {
                System.currentTimeMillis() - recordingStartedAt
            } else {
                null
            },
            "recordingStartedAt" to recordingStartedAt.takeIf { recording.get() && it > 0 },
            "segmentMaxBytes" to SEGMENT_MAX_BYTES,
            "segmentMaxMs" to SEGMENT_MAX_MS,
            "policy" to mapOf(
                "maxTotalMb" to maxTotalMb,
                "maxAgeDays" to maxAgeDays,
            ),
            "streamConfig" to mapOf(
                "fps" to mosaic.targetFps,
                "mosaicHeight" to mosaic.targetHeight,
                "frameIntervalMs" to frameIntervalMs(),
                "source" to "platform",
            ),
            "usageBytes" to usage.first,
            "usageCount" to usage.second,
            "selectedFreeBytes" to selected?.get("freeBytes"),
            "selectedTotalBytes" to selected?.get("totalBytes"),
            "selectedUsableBytes" to selected?.get("usableBytes"),
            "lastError" to lastError,
            "preview" to previewStatus(),
        )
    }

    private fun usageOnCurrentStorage(): Pair<Long, Int> {
        val files = recordingFiles()
        return files.sumOf { it.length() } to files.size
    }

    private fun recordingFiles(): List<File> {
        val dir = outputDir()
        return dir.listFiles { f ->
            f.isFile &&
                f.name.startsWith("oca_merged_") &&
                (f.name.endsWith(".mjpeg") || f.name.endsWith(".mp4") || f.name.endsWith(".seg"))
        }?.toList().orEmpty()
    }

    /**
     * Stream a saved concatenated-JPEG recording as multipart MJPEG (same wire
     * format as live preview). Skips frames before [fromMs]. Paces at the clip's
     * recorded interval (falls back to platform FPS).
     */
    fun streamRecordingMultipart(
        file: File,
        fromMs: Long,
        out: java.io.OutputStream,
        cancelled: () -> Boolean = { false },
    ) {
        val interval = readMetaLong(file, "frameIntervalMs")?.takeIf { it > 0 } ?: frameIntervalMs()
        val startFrame = ((fromMs.coerceAtLeast(0L)) / interval).toInt()
        var index = 0
        file.inputStream().buffered(64 * 1024).use { input ->
            while (!cancelled()) {
                val jpeg = readNextJpeg(input) ?: break
                if (index++ < startFrame) continue
                out.write(PART_HEADER)
                out.write(jpeg.size.toString().toByteArray())
                out.write(PART_MID)
                out.write(jpeg)
                out.write(PART_END)
                out.flush()
                try {
                    Thread.sleep(interval)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun writeSegmentMeta(file: File, frameCount: Int) {
        if (!file.name.endsWith(".mjpeg") || !file.isFile || frameCount <= 0) return
        val interval = frameIntervalMs()
        val durationMs = frameCount * interval
        runCatching {
            metaFileFor(file).writeText(
                "{\"durationMs\":$durationMs,\"frameCount\":$frameCount," +
                    "\"frameIntervalMs\":$interval,\"format\":\"mjpeg\"}\n",
            )
        }
    }

    private fun durationMsFor(file: File): Long {
        if (file.name.endsWith(".mp4")) {
            // Legacy H.264 clips from earlier builds.
            readMetaLong(file, "durationMs")?.let { if (it > 0) return it }
            return runCatching {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                } finally {
                    runCatching { r.release() }
                }
            }.getOrNull() ?: 0L
        }
        readMetaLong(file, "durationMs")?.let { if (it > 0) return it }
        val frames = readMetaLong(file, "frameCount")?.toInt()?.takeIf { it > 0 }
            ?: countJpegFrames(file).also { if (it > 0) writeSegmentMeta(file, it) }
        if (frames > 0) {
            val interval = readMetaLong(file, "frameIntervalMs")?.takeIf { it > 0 }
                ?: frameIntervalMs()
            return frames * interval
        }
        return 0L
    }

    private fun frameCountFor(file: File): Int {
        if (file.name.endsWith(".mp4")) return 0
        readMetaLong(file, "frameCount")?.toInt()?.let { if (it > 0) return it }
        val frames = countJpegFrames(file)
        if (frames > 0) writeSegmentMeta(file, frames)
        return frames
    }

    private fun readMetaLong(file: File, key: String): Long? {
        val meta = metaFileFor(file)
        if (!meta.isFile) return null
        val text = runCatching { meta.readText() }.getOrNull() ?: return null
        val re = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return re.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun countJpegFrames(file: File): Int {
        var n = 0
        runCatching {
            file.inputStream().buffered(64 * 1024).use { input ->
                while (readNextJpeg(input) != null) n++
            }
        }
        return n
    }

    private fun metaFileFor(f: File): File = File(f.absolutePath + ".meta")

    private fun lockFileFor(f: File): File = File(f.absolutePath + ".lock")

    private fun isLocked(f: File): Boolean = lockFileFor(f).isFile

    /** Pull one JPEG (SOI…EOI) from a concatenated stream; null at EOF. */
    private fun readNextJpeg(input: java.io.InputStream): ByteArray? {
        val bout = java.io.ByteArrayOutputStream(64 * 1024)
        var prev = -1
        var inImage = false
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (inImage && bout.size() > 2) bout.toByteArray() else null
            }
            if (!inImage) {
                if (prev == 0xff && b == 0xd8) {
                    inImage = true
                    bout.write(0xff)
                    bout.write(0xd8)
                }
                prev = b
                continue
            }
            bout.write(b)
            if (prev == 0xff && b == 0xd9) {
                return bout.toByteArray()
            }
            prev = b
        }
    }

    private fun ensureStorageMounted() {
        val targets = storageTargets(forceRefresh = true)
        val match = targets.firstOrNull { it["id"] == storageId }
        if (match == null || match["writable"] != true) {
            storageNote = if (match == null) "Storage unavailable; using app" else "Storage not writable; using app"
            storageId = STORAGE_APP
            prefs.edit().putString(KEY_STORAGE, STORAGE_APP).apply()
        }
    }

    private fun targetMap(
        id: String,
        labelKey: String,
        label: String,
        dir: File,
        kind: String,
        writableOverride: Boolean? = null,
        available: Boolean? = null,
    ): Map<String, Any?> {
        val space = statFs(dir)
        val writable = writableOverride ?: dir.canWrite()
        return mapOf(
            "id" to id,
            "labelKey" to labelKey,
            "label" to label,
            "path" to dir.absolutePath,
            "kind" to kind,
            "writable" to writable,
            "available" to (available ?: writable),
            "totalBytes" to space[0],
            "freeBytes" to space[1],
            "usableBytes" to space[2],
        )
    }

    private fun isRemovableNonPrimary(vol: StorageVolume): Boolean {
        return try {
            !vol.isPrimary && vol.isRemovable
        } catch (_: Throwable) {
            false
        }
    }

    private fun statFs(dir: File): LongArray {
        return try {
            val s = StatFs(dir.absolutePath)
            longArrayOf(s.totalBytes, s.freeBytes, s.availableBytes)
        } catch (_: Throwable) {
            longArrayOf(0L, 0L, 0L)
        }
    }

    private fun volumePath(vol: StorageVolume): File? {
        return if (Build.VERSION.SDK_INT >= 30) {
            vol.directory
        } else {
            @Suppress("DEPRECATION")
            vol.javaClass.methods
                .firstOrNull { it.name == "getPathFile" }
                ?.invoke(vol) as? File
        }
    }

    private fun looksLikeUsb(desc: String, vol: StorageVolume): Boolean {
        val d = desc.lowercase(Locale.US)
        if (d.contains("usb") || d.contains("flash") || d.contains("otg") || d.contains("pendrive")) {
            return true
        }
        // Removable non-emulated volumes are often USB on HUs.
        return try {
            !vol.isEmulated && vol.isRemovable
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "OcaDvr"
        private const val PREFS = "oca_dvr"
        private const val KEY_STORAGE = "dvr_storage_id"
        private const val KEY_MODE = "dvr_mode"
        private const val KEY_MAX_TOTAL_MB = "dvr_max_total_mb"
        private const val KEY_MAX_AGE_DAYS = "dvr_max_age_days"

        const val STORAGE_APP = "app"
        const val STORAGE_PRIMARY = "primary"
        const val STORAGE_USB_PLACEHOLDER = "usb"
        const val MODE_OFF = "off"
        const val MODE_SEGMENT = "segment"
        const val MODE_DVR = "dvr"

        private const val STORAGE_CACHE_MS = 30_000L
        private const val DEFAULT_MAX_TOTAL_MB = 2048
        private const val DEFAULT_MAX_AGE_DAYS = 0
        private const val SEGMENT_MAX_BYTES = 100L * 1024L * 1024L
        private const val SEGMENT_MAX_MS = 5L * 60L * 1000L
        private val PART_HEADER =
            "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ".toByteArray()
        private val PART_MID = "\r\n\r\n".toByteArray()
        private val PART_END = "\r\n".toByteArray()
    }
}
