package cc.opencar.assistant.feature.shortcuts

data class Shortcut(
    val id: String,
    val name: String,
    val icon: String = "drive",
    val enabled: Boolean = true,
    val actions: List<ShortcutAction> = emptyList(),
    val triggers: List<ShortcutTrigger> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "icon" to icon,
        "enabled" to enabled,
        "actions" to actions.map { it.toMap() },
        "triggers" to triggers.map { it.toMap() },
    )

    companion object {
        fun fromMap(m: Map<*, *>): Shortcut? {
            val id = m["id"] as? String ?: return null
            val name = m["name"] as? String ?: return null
            val icon = m["icon"] as? String ?: "drive"
            val enabled = m["enabled"] as? Boolean ?: true
            @Suppress("UNCHECKED_CAST")
            val actionsRaw = m["actions"] as? List<Map<*, *>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val triggersRaw = m["triggers"] as? List<Map<*, *>> ?: emptyList()
            return Shortcut(
                id = id,
                name = name,
                icon = icon,
                enabled = enabled,
                actions = actionsRaw.mapNotNull { ShortcutAction.fromMap(it) },
                triggers = triggersRaw.mapNotNull { ShortcutTrigger.fromMap(it) },
            )
        }
    }
}

sealed class ShortcutAction {
    abstract fun toMap(): Map<String, Any?>

    data class SetControl(val entityId: String, val value: String) : ShortcutAction() {
        override fun toMap() = mapOf(
            "type" to "set_control",
            "entityId" to entityId,
            "value" to value,
        )
    }

    data class LaunchApp(val packageName: String) : ShortcutAction() {
        override fun toMap() = mapOf(
            "type" to "launch_app",
            "packageName" to packageName,
        )
    }

    data class DelayMs(val ms: Long) : ShortcutAction() {
        override fun toMap() = mapOf(
            "type" to "delay_ms",
            "ms" to ms.coerceIn(0L, MAX_DELAY_MS),
        )
    }

    /** Plugin-backed action (`pluginId` + contribution `action` + `params`). */
    data class Plugin(
        val pluginId: String,
        val action: String,
        val params: Map<String, Any?> = emptyMap(),
    ) : ShortcutAction() {
        override fun toMap() = mapOf(
            "type" to "plugin",
            "pluginId" to pluginId,
            "action" to action,
            "params" to params,
        )
    }

    companion object {
        const val MAX_DELAY_MS = 5_000L
        const val MAX_ACTIONS = 10

        fun fromMap(m: Map<*, *>): ShortcutAction? {
            return when (m["type"] as? String) {
                "set_control" -> {
                    val id = m["entityId"] as? String ?: return null
                    val value = m["value"]?.toString() ?: return null
                    SetControl(id, value)
                }
                "launch_app" -> {
                    val pkg = m["packageName"] as? String ?: return null
                    LaunchApp(pkg)
                }
                "delay_ms" -> {
                    val ms = when (val v = m["ms"]) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: return null
                        else -> return null
                    }
                    DelayMs(ms.coerceIn(0L, MAX_DELAY_MS))
                }
                "plugin" -> {
                    val pluginId = m["pluginId"] as? String ?: return null
                    val action = m["action"] as? String ?: return null
                    Plugin(pluginId, action, readParams(m["params"]))
                }
                else -> null
            }
        }
    }
}

sealed class ShortcutTrigger {
    abstract fun toMap(): Map<String, Any?>

    data object Boot : ShortcutTrigger() {
        override fun toMap() = mapOf("type" to "boot")
    }

    /** HU display wake (`on=true`) or sleep (`on=false`). */
    data class Screen(val on: Boolean) : ShortcutTrigger() {
        override fun toMap() = mapOf("type" to "screen", "on" to on)
    }

    data class Gear(val gear: Int? = null) : ShortcutTrigger() {
        override fun toMap() = mapOf("type" to "gear", "gear" to gear)
    }

    data class WheelKey(val key: String) : ShortcutTrigger() {
        override fun toMap() = mapOf("type" to "wheel_key", "key" to key)
    }

    /** Plugin-backed trigger (`pluginId` + contribution `trigger` + `params`). */
    data class Plugin(
        val pluginId: String,
        val trigger: String,
        val params: Map<String, Any?> = emptyMap(),
    ) : ShortcutTrigger() {
        override fun toMap() = mapOf(
            "type" to "plugin",
            "pluginId" to pluginId,
            "trigger" to trigger,
            "params" to params,
        )
    }

    companion object {
        fun fromMap(m: Map<*, *>): ShortcutTrigger? {
            return when (m["type"] as? String) {
                "boot" -> Boot
                "screen" -> Screen(on = parseScreenOn(m["on"]))
                "gear" -> {
                    val gear = when (val v = m["gear"]) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull()
                        null -> null
                        else -> null
                    }
                    Gear(gear)
                }
                "wheel_key" -> {
                    val key = m["key"] as? String ?: return null
                    WheelKey(key)
                }
                "plugin" -> {
                    val pluginId = m["pluginId"] as? String ?: return null
                    val trigger = m["trigger"] as? String ?: return null
                    Plugin(pluginId, trigger, readParams(m["params"]))
                }
                else -> null
            }
        }

        private fun parseScreenOn(raw: Any?): Boolean {
            return when (raw) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "off", "false", "0" -> false
                    else -> true
                }
                else -> true
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun readParams(raw: Any?): Map<String, Any?> {
    val m = raw as? Map<*, *> ?: return emptyMap()
    return m.entries.mapNotNull { (k, v) ->
        val key = k as? String ?: return@mapNotNull null
        key to v
    }.toMap()
}
