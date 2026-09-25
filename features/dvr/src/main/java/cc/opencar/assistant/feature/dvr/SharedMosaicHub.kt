package cc.opencar.assistant.feature.dvr

/**
 * Refcounted handle around [MosaicPreviewSession] + H.264 pipeline lifetime.
 * Live preview and DVR recording each [acquire]/[release] a seat.
 */
class SharedMosaicHub(
    private val mosaic: MosaicPreviewSession,
    private val ensureMosaic: () -> Boolean,
) {
    private val refs = java.util.concurrent.atomic.AtomicInteger(0)

    fun isRunning(): Boolean = mosaic.isRunning()
    fun refCount(): Int = refs.get()
    fun lastError(): String? = mosaic.lastError
    fun status(): Map<String, Any?> = mosaic.status() + mapOf("refs" to refs.get())

    /** Start mosaic if needed; does not bump the refcount. Prefer [acquire] for clients. */
    fun ensureStarted(): Boolean {
        if (mosaic.isRunning()) return true
        return ensureMosaic()
    }

    /** Acquire a seat (live client or recorder). Starts mosaic on first ref. */
    fun acquire(): Boolean {
        refs.incrementAndGet()
        if (!ensureStarted()) {
            release()
            return false
        }
        return true
    }

    fun release() {
        if (refs.decrementAndGet() <= 0) {
            refs.set(0)
            mosaic.stop()
        }
    }
}
