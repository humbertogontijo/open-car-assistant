package cc.opencar.assistant.feature.web

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fan-out bus for `/api/events` WebSocket clients (catalog invalidation +
 * optional targeted entity deltas after writes).
 *
 * Composites use catalog invalidation only — never patch product `value` with
 * binding attr-raw (see [EntityContract.UPDATE_CATALOG]).
 */
internal object WebEventHub {
    private val _bus = MutableSharedFlow<Map<String, Any?>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val bus: SharedFlow<Map<String, Any?>> = _bus.asSharedFlow()

    @Volatile private var lastCatalogMs: Long = 0L

    fun emitCatalog(reason: String) {
        val now = System.currentTimeMillis()
        // Coalesce rapid composite attr edges (many VHAL props → one product).
        if (now - lastCatalogMs < 300L) return
        lastCatalogMs = now
        _bus.tryEmit(mapOf("t" to "catalog", "reason" to reason))
    }

    fun emitEntity(id: String, value: Any?, status: String? = null) {
        val msg = mutableMapOf<String, Any?>("t" to "entity", "id" to id, "value" to value)
        if (status != null) msg["status"] = status
        _bus.tryEmit(msg)
    }
}
