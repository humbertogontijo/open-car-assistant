package cc.opencar.assistant.feature.shortcuts

import android.content.Context
import android.util.Log
import cc.opencar.assistant.api.plugin.ShortcutActionHandler
import kotlinx.coroutines.delay

/**
 * Executes a shortcut's action sequence. Control writes go through [setControl];
 * app launches through [AppLauncher]; plugin actions through [actionHandlers].
 */
class ShortcutRunner(
    private val context: Context,
    private val setControl: suspend (entityId: String, value: String) -> Result<Unit>,
    private val launcher: AppLauncher = AppLauncher(context),
    private val actionHandlers: Map<String, ShortcutActionHandler> = emptyMap(),
) {
    suspend fun run(shortcut: Shortcut): Map<String, Any?> {
        if (!shortcut.enabled) {
            return mapOf("ok" to false, "error" to "disabled")
        }
        val results = mutableListOf<Map<String, Any?>>()
        for ((index, action) in shortcut.actions.withIndex()) {
            val step = runAction(action)
            results.add(mapOf("index" to index, "action" to action.toMap()) + step)
            if (step["ok"] != true) {
                Log.w(TAG, "shortcut ${shortcut.id} step $index failed: ${step["error"]}")
                return mapOf(
                    "ok" to false,
                    "id" to shortcut.id,
                    "error" to step["error"],
                    "steps" to results,
                )
            }
        }
        return mapOf("ok" to true, "id" to shortcut.id, "steps" to results)
    }

    private suspend fun runAction(action: ShortcutAction): Map<String, Any?> {
        return when (action) {
            is ShortcutAction.SetControl -> {
                val r = setControl(action.entityId, action.value)
                if (r.isSuccess) mapOf("ok" to true)
                else mapOf("ok" to false, "error" to (r.exceptionOrNull()?.message ?: "set failed"))
            }
            is ShortcutAction.LaunchApp -> {
                val ok = launcher.launch(action.packageName)
                if (ok) mapOf("ok" to true)
                else mapOf("ok" to false, "error" to "launch failed")
            }
            is ShortcutAction.DelayMs -> {
                delay(action.ms.coerceIn(0L, ShortcutAction.MAX_DELAY_MS))
                mapOf("ok" to true)
            }
            is ShortcutAction.Plugin -> {
                val handler = actionHandlers[action.pluginId]
                    ?: return mapOf("ok" to false, "error" to "plugin not available: ${action.pluginId}")
                if (action.action !in handler.actionTypes) {
                    return mapOf("ok" to false, "error" to "unsupported action: ${action.action}")
                }
                val r = handler.run(action.action, action.params)
                if (r.isSuccess) mapOf("ok" to true)
                else mapOf("ok" to false, "error" to (r.exceptionOrNull()?.message ?: "plugin action failed"))
            }
        }
    }

    companion object {
        private const val TAG = "ShortcutRunner"
    }
}
