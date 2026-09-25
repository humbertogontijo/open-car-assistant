package cc.opencar.assistant.feature.web

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fan-out bus for `/api/events` WebSocket clients (catalog invalidation +
 * optional targeted entity deltas after writes).
 */
internal object WebEventHub {
    private val _bus = MutableSharedFlow<Map<String, Any?>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val bus: SharedFlow<Map<String, Any?>> = _bus.asSharedFlow()

    fun emitCatalog(reason: String) {
        _bus.tryEmit(mapOf("t" to "catalog", "reason" to reason))
    }

    fun emitEntity(id: String, value: Any?, status: String? = null) {
        val msg = mutableMapOf<String, Any?>("t" to "entity", "id" to id, "value" to value)
        if (status != null) msg["status"] = status
        _bus.tryEmit(msg)
    }
}
