package cc.opencar.assistant.feature.shortcuts

/**
 * Shared AND evaluation for flow and routine [ShortcutCondition] lists.
 * Empty list always passes.
 */
object ConditionEvaluator {
    suspend fun conditionsPass(
        conditions: List<ShortcutCondition>,
        readEntity: (suspend (String) -> String?)? = null,
        readGear: (suspend () -> Int?)? = null,
        readWifiSsid: (() -> String?)? = null,
    ): Boolean {
        if (conditions.isEmpty()) return true
        for (c in conditions) {
            when (c) {
                is ShortcutCondition.EntityEquals -> {
                    val actual = readEntity?.invoke(c.entityId)
                    if (actual == null || actual != c.value) return false
                }
                is ShortcutCondition.GearEquals -> {
                    val gear = readGear?.invoke()
                    if (gear == null || gear != c.gear) return false
                }
                is ShortcutCondition.WifiSsid -> {
                    val ssid = readWifiSsid?.invoke() ?: return false
                    val ok = if (c.contains) {
                        ssid.contains(c.ssid, ignoreCase = true)
                    } else {
                        ssid.equals(c.ssid, ignoreCase = true)
                    }
                    if (!ok) return false
                }
            }
        }
        return true
    }
}
