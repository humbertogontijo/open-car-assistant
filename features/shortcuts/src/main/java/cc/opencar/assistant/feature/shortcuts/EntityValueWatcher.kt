package cc.opencar.assistant.feature.shortcuts

import android.util.Log
import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.api.VehicleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Watches bound entity values for [ShortcutTrigger.EntityState] / conditions.
 *
 * Prefers [VehicleEvent.EntityValueChanged] from the session when available;
 * always keeps a poll loop so android / location / media entities (no session
 * push) keep updating after the first VHAL edge arrives.
 */
class EntityValueWatcher(
    private val store: ShortcutStore,
    private val readEntity: suspend (String) -> String?,
    private val onChanged: (entityId: String, value: String?) -> Unit,
    private val events: Flow<VehicleEvent>? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private var job: Job? = null
    private val last = mutableMapOf<String, String?>()
    @Volatile private var watched: Set<String> = emptySet()

    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                while (coroutineContext.isActive) {
                    watched = loadWatchedIds()
                    delay(2_000)
                }
            }
            val eventsFlow = events
            if (eventsFlow != null) {
                launch {
                    eventsFlow.collect { ev ->
                        if (ev !is VehicleEvent.EntityValueChanged) return@collect
                        onSessionEntity(ev.entityId, ev.value)
                    }
                }
            }
            while (coroutineContext.isActive) {
                pollOnce()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun pollOnce() {
        val ids = loadWatchedIds()
        watched = ids
        for (id in ids) {
            val value = runCatching { readEntity(id) }.getOrNull()
            applyChange(id, value)
        }
    }

    /**
     * Session events use platform binding keys (`hvac_power`). For composites,
     * re-read the product id so watchers see HVAC mode, not attr-raw.
     */
    private suspend fun onSessionEntity(entityId: String, value: String?) {
        val ids = watched
        val product = EntityRegistry.resolveBinding(entityId)
        if (product != null && product.isComposite) {
            if (product.id in ids) {
                applyChange(product.id, runCatching { readEntity(product.id) }.getOrNull())
            }
            if (entityId in ids) applyChange(entityId, value)
            return
        }
        if (ids.isEmpty() || entityId in ids) {
            applyChange(entityId, value)
        }
    }

    private fun applyChange(entityId: String, value: String?) {
        if (!last.containsKey(entityId)) {
            last[entityId] = value
            return
        }
        if (last[entityId] != value) {
            last[entityId] = value
            Log.i(TAG, "entity $entityId -> $value")
            onChanged(entityId, value)
        }
    }

    private suspend fun loadWatchedIds(): Set<String> {
        val ids = linkedSetOf<String>()
        for (s in store.list().filter { it.enabled }) {
            for (t in s.triggers) {
                if (t is ShortcutTrigger.EntityState) ids += t.entityId
            }
            for (c in s.conditions) {
                if (c is ShortcutCondition.EntityEquals) ids += c.entityId
            }
        }
        for (r in store.listRoutines().filter { it.enabled }) {
            for (c in r.conditions) {
                if (c is ShortcutCondition.EntityEquals) ids += c.entityId
            }
        }
        return ids
    }

    companion object {
        private const val TAG = "EntityValueWatcher"
        private const val POLL_MS = 2_000L
    }
}
