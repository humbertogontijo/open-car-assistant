package cc.opencar.assistant.api.plugin

/**
 * Runs a plugin-backed shortcut action (`ShortcutAction.Plugin`).
 */
interface ShortcutActionHandler {
    val pluginId: String
    val actionTypes: Set<String>

    suspend fun run(type: String, params: Map<String, Any?>): Result<Unit>
}

/**
 * Emits plugin-backed shortcut trigger events (`ShortcutTrigger.Plugin`).
 */
interface ShortcutTriggerSource {
    val pluginId: String
    val triggerTypes: Set<String>

    fun start(listener: ShortcutTriggerListener)
    fun stop()
}

fun interface ShortcutTriggerListener {
    /**
     * @param triggerType contribution type (e.g. `entity_state`)
     * @param eventParams event payload used to match stored trigger params
     *   (e.g. `entity_id`, `from`, `to`)
     */
    fun onTrigger(pluginId: String, triggerType: String, eventParams: Map<String, Any?>)
}
