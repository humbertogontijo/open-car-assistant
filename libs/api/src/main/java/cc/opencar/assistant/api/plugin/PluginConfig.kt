package cc.opencar.assistant.api.plugin

/**
 * Declarative settings form for a plugin. Feature-web renders this without
 * knowing the concrete plugin type.
 */
data class PluginConfigSchema(
    val fields: List<PluginConfigField>,
)

data class PluginConfigField(
    val key: String,
    /** One of: `text`, `password`, `bool`. */
    val type: String,
    val label: String,
    val optional: Boolean = true,
    /** Hint / placeholder shown in the input. */
    val placeholder: String? = null,
    /** Longer helper text under the field. */
    val description: String? = null,
)

object PluginConfigFieldTypes {
    const val TEXT = "text"
    const val PASSWORD = "password"
    const val BOOL = "bool"
}
