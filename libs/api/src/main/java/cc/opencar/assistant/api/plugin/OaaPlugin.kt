package cc.opencar.assistant.api.plugin

import android.content.Context
import cc.opencar.assistant.api.VehicleSession
import kotlinx.coroutines.CoroutineScope

/**
 * External bridge / product extension (Home Assistant, …).
 * Distinct from [cc.opencar.assistant.api.VehicleIntegration] (HU platforms).
 */
interface OaaPlugin {
    val id: String
    val displayName: String

    /** Optional shortcut action contribution (e.g. HA `call_service`). */
    val actionHandler: ShortcutActionHandler? get() = null

    /** Optional live trigger contribution (e.g. HA `entity_state`). */
    val triggerSource: ShortcutTriggerSource? get() = null

    suspend fun start(host: PluginHost)
    fun stop()

    /** Safe status for `/api/status` — never include secrets. */
    fun status(): Map<String, Any?>

    /**
     * Optional settings schema for the Plugins UI. When null, the plugin has
     * no configurable surface beyond [status].
     */
    fun configSchema(): PluginConfigSchema? = null

    /** Safe config snapshot for GET — never include secrets. */
    fun configSnapshot(): Map<String, Any?> = emptyMap()

    /** Apply a generic key/value map from POST `/api/plugins/{id}`. */
    fun applyConfig(values: Map<String, Any?>) {}
}

interface PluginHost {
    val context: Context
    val session: VehicleSession
    val scope: CoroutineScope
}

interface PluginRegistry {
    fun all(): List<OaaPlugin>
    fun get(id: String): OaaPlugin? = all().firstOrNull { it.id == id }
}
