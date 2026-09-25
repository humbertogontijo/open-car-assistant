package cc.opencar.assistant.feature.shortcuts

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Activates / deactivates scenes: snapshot → write onValues; restore or force off.
 *
 * External writes to a target entity while a scene is active deactivate that scene.
 * [restoreAllActive] runs the off-path for every active scene (used on boot).
 */
class SceneEngine(
    private val store: SceneStore,
    private val setControl: suspend (entityId: String, value: String) -> Result<Unit>,
    private val readEntity: suspend (String) -> String?,
) {
    /** Depth of scene-owned writes — external conflict detection is suppressed. */
    private val applying = AtomicInteger(0)

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

    /**
     * Deactivate every scene currently marked active (restore / force-off targets).
     * Used on boot so a previous session's active flag does not leave cabin state ambiguous.
     */
    suspend fun restoreAllActive(): Map<String, Any?> {
        val ids = store.activeIds().toList()
        if (ids.isEmpty()) {
            return mapOf("ok" to true, "restored" to emptyList<String>())
        }
        Log.i(TAG, "boot restore active scenes=$ids")
        val steps = mutableListOf<Map<String, Any?>>()
        for (id in ids) {
            steps.add(setActive(id, active = false))
        }
        return mapOf("ok" to true, "restored" to ids, "steps" to steps)
    }

    /**
     * A control was written outside this engine. If it is a target of an active scene,
     * deactivate that scene (user left the mode).
     */
    suspend fun onExternalWrite(entityId: String) {
        if (entityId.isBlank()) return
        if (applying.get() > 0) return
        val activeIds = store.activeIds()
        if (activeIds.isEmpty()) return
        for (id in activeIds) {
            val scene = store.get(id) ?: continue
            if (!scene.enabled) continue
            if (scene.targets.none { it.entityId == entityId }) continue
            Log.i(TAG, "external write entity=$entityId → deactivate scene=$id")
            deactivate(scene)
        }
    }

    private suspend fun activate(scene: Scene): Map<String, Any?> {
        val snapshot = linkedMapOf<String, String>()
        for (t in scene.targets) {
            val current = runCatching { readEntity(t.entityId) }.getOrNull()
            snapshot[t.entityId] = current ?: ""
        }
        val steps = mutableListOf<Map<String, Any?>>()
        var wrote = 0
        withApplying {
            for (t in scene.targets) {
                val r = setControl(t.entityId, t.onValue)
                if (r.isFailure) {
                    // Skip unbound / unavailable targets so builtin scenes stay portable
                    // (missing entity ≠ abort), but report ok=false so callers see the failure.
                    Log.w(TAG, "scene ${scene.id} skip ${t.entityId}: ${r.exceptionOrNull()?.message}")
                    steps.add(
                        mapOf(
                            "entityId" to t.entityId,
                            "value" to t.onValue,
                            "ok" to false,
                            "skipped" to true,
                            "error" to r.exceptionOrNull()?.message,
                        ),
                    )
                    continue
                }
                wrote++
                steps.add(
                    mapOf(
                        "entityId" to t.entityId,
                        "value" to t.onValue,
                        "ok" to true,
                    ),
                )
            }
        }
        val allOk = steps.all { it["ok"] == true }
        // Mark active whenever at least one target applied (or the scene has no targets).
        // Partial apply still arms conflict/boot restore for the targets that stuck.
        val active = wrote > 0 || scene.targets.isEmpty()
        if (active) {
            store.markActive(scene.id, active = true, snapshot = snapshot)
        }
        return mapOf(
            "ok" to allOk,
            "id" to scene.id,
            "active" to active,
            "wrote" to wrote,
            "steps" to steps,
            "error" to if (!allOk) {
                steps.firstOrNull { it["ok"] == false }?.get("error") ?: "set failed"
            } else {
                null
            },
        )
    }

    private suspend fun deactivate(scene: Scene): Map<String, Any?> {
        val snapshot = store.snapshotFor(scene.id)
        val steps = mutableListOf<Map<String, Any?>>()
        withApplying {
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
        }
        store.markActive(scene.id, active = false, snapshot = null)
        return mapOf("ok" to true, "id" to scene.id, "active" to false, "steps" to steps)
    }

    private suspend fun <T> withApplying(block: suspend () -> T): T {
        applying.incrementAndGet()
        return try {
            block()
        } finally {
            applying.decrementAndGet()
        }
    }

    companion object {
        private const val TAG = "SceneEngine"
    }
}
