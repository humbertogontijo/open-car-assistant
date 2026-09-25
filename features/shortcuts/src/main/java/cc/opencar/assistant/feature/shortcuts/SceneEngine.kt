package cc.opencar.assistant.feature.shortcuts

import android.util.Log

/**
 * Activates / deactivates scenes: snapshot → write onValues; restore or force off.
 */
class SceneEngine(
    private val store: SceneStore,
    private val setControl: suspend (entityId: String, value: String) -> Result<Unit>,
    private val readEntity: suspend (String) -> String?,
) {
    suspend fun setActive(id: String, active: Boolean): Map<String, Any?> {
        val scene = store.get(id) ?: return mapOf("ok" to false, "error" to "not found")
        if (!scene.enabled) return mapOf("ok" to false, "error" to "disabled")
        val currently = store.isActive(id)
        if (active == currently) {
            return mapOf("ok" to true, "id" to id, "active" to active, "noop" to true)
        }
        return if (active) activate(scene) else deactivate(scene)
    }

    suspend fun toggle(id: String): Map<String, Any?> {
        val active = store.isActive(id)
        return setActive(id, !active)
    }

    private suspend fun activate(scene: Scene): Map<String, Any?> {
        val snapshot = linkedMapOf<String, String>()
        for (t in scene.targets) {
            val current = runCatching { readEntity(t.entityId) }.getOrNull()
            snapshot[t.entityId] = current ?: ""
        }
        val steps = mutableListOf<Map<String, Any?>>()
        for (t in scene.targets) {
            val r = setControl(t.entityId, t.onValue)
            steps.add(
                mapOf(
                    "entityId" to t.entityId,
                    "value" to t.onValue,
                    "ok" to r.isSuccess,
                    "error" to r.exceptionOrNull()?.message,
                ),
            )
            if (r.isFailure) {
                Log.w(TAG, "scene ${scene.id} activate failed on ${t.entityId}: ${r.exceptionOrNull()?.message}")
                return mapOf(
                    "ok" to false,
                    "id" to scene.id,
                    "active" to false,
                    "error" to (r.exceptionOrNull()?.message ?: "set failed"),
                    "steps" to steps,
                )
            }
        }
        store.markActive(scene.id, active = true, snapshot = snapshot)
        return mapOf("ok" to true, "id" to scene.id, "active" to true, "steps" to steps)
    }

    private suspend fun deactivate(scene: Scene): Map<String, Any?> {
        val snapshot = store.snapshotFor(scene.id)
        val steps = mutableListOf<Map<String, Any?>>()
        for (t in scene.targets) {
            val value = when (val off = t.off) {
                is SceneOff.Restore -> snapshot[t.entityId]
                is SceneOff.Set -> off.value
            }
            if (value == null) {
                steps.add(
                    mapOf(
                        "entityId" to t.entityId,
                        "ok" to true,
                        "skipped" to true,
                        "reason" to "no snapshot",
                    ),
                )
                continue
            }
            if (value.isEmpty() && t.off is SceneOff.Restore) {
                steps.add(
                    mapOf(
                        "entityId" to t.entityId,
                        "ok" to true,
                        "skipped" to true,
                        "reason" to "empty snapshot",
                    ),
                )
                continue
            }
            val r = setControl(t.entityId, value)
            steps.add(
                mapOf(
                    "entityId" to t.entityId,
                    "value" to value,
                    "ok" to r.isSuccess,
                    "error" to r.exceptionOrNull()?.message,
                ),
            )
            if (r.isFailure) {
                Log.w(TAG, "scene ${scene.id} deactivate failed on ${t.entityId}: ${r.exceptionOrNull()?.message}")
            }
        }
        store.markActive(scene.id, active = false, snapshot = null)
        return mapOf("ok" to true, "id" to scene.id, "active" to false, "steps" to steps)
    }

    companion object {
        private const val TAG = "SceneEngine"
    }
}
