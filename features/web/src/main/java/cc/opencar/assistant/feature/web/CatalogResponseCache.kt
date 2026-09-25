package cc.opencar.assistant.feature.web

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Short TTL coalescing for /api/entities and /api/controls.
 * The on-HU WebView soft-polls every few seconds; overlapping builds of the full
 * catalog (VHAL diagnose × N + Gson) peg DefaultDispatcher and GC.
 */
internal object CatalogResponseCache {
    private const val TTL_MS = 1_500L
    private val mutex = Mutex()

    private class Slot {
        @Volatile var at = 0L
        @Volatile var value: List<Map<String, Any?>>? = null
    }

    private val entitiesSlot = Slot()
    private val controlsSlot = Slot()

    fun invalidate() {
        entitiesSlot.value = null
        controlsSlot.value = null
        entitiesSlot.at = 0L
        controlsSlot.at = 0L
    }

    suspend fun entities(build: suspend () -> List<Map<String, Any?>>): List<Map<String, Any?>> =
        cached(entitiesSlot, build)

    suspend fun controls(build: suspend () -> List<Map<String, Any?>>): List<Map<String, Any?>> =
        cached(controlsSlot, build)

    private suspend fun cached(
        slot: Slot,
        build: suspend () -> List<Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        val now = System.currentTimeMillis()
        slot.value?.let { if (now - slot.at < TTL_MS) return it }
        return mutex.withLock {
            val t = System.currentTimeMillis()
            slot.value?.let { if (t - slot.at < TTL_MS) return it }
            build().also {
                slot.value = it
                slot.at = System.currentTimeMillis()
            }
        }
    }
}
