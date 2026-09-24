package cc.opencar.assistant.feature.shortcuts

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Polls bound entity values used by [ShortcutTrigger.EntityState] / conditions
 * and reports changes via [onChanged].
 */
class EntityValueWatcher(
    private val store: ShortcutStore,
    private val readEntity: suspend (String) -> String?,
    private val onChanged: (entityId: String, value: String?) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private var job: Job? = null
    private val last = mutableMapOf<String, String?>()

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (coroutineContext.isActive) {
                val ids = watchedIds()
                for (id in ids) {
                    val value = runCatching { readEntity(id) }.getOrNull()
                    if (!last.containsKey(id)) {
                        last[id] = value
                        continue
                    }
                    if (last[id] != value) {
                        last[id] = value
                        Log.i(TAG, "entity $id -> $value")
                        onChanged(id, value)
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun watchedIds(): Set<String> {
        val ids = linkedSetOf<String>()
        for (s in store.list().filter { it.enabled }) {
            for (t in s.triggers) {
                if (t is ShortcutTrigger.EntityState) ids += t.entityId
            }
            for (c in s.conditions) {
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
