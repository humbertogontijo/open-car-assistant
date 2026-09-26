package cc.opencar.assistant.integrations.platform.flyme

import cc.opencar.assistant.api.QuickEntry
import cc.opencar.assistant.api.WakeSignals

/**
 * Flyme Auto family helpers shared by Geely EX2 (IHU629G) and EX5 (Antora / SE1000).
 *
 * VenusVehicleServer gRPC stays in `:integrations:antora1000` — it is not a Flyme-wide transport.
 * True AOSP property IDs live in [cc.opencar.assistant.integrations.aaos.AospVehicleIds].
 */
object FlymePlatform {
    const val FAMILY = "flyme"

    /** Status-bar icon entry for Flyme Auto HUs. */
    fun createStatusBarQuickEntry(
        iconRes: Int = R.drawable.ic_status_bar_entry,
        describe: String = "OAA",
    ): QuickEntry = FlymeStatusBarQuickEntry(iconRes = iconRes, describe = describe)

    /**
     * ECARX / Geely / LynkCo vendor wake & sleep intents.
     * Flyme HUs often disable the lockscreen and never emit classic SCREEN_ON/OFF;
     * use this set for keepalive / resume instead.
     */
    fun wakeSignals(): WakeSignals = WakeSignals(
        wakeActions = setOf(
            "com.ecarx.intent.action.SYSTEM_READY",
            "com.ecarx.intent.action.ACC_ON",
            "com.ecarx.intent.action.IGN_ON",
            "com.ecarx.intent.action.WAKEUP",
            "com.ecarx.intent.action.STR_RESUME",
            "com.ecarx.intent.action.POWER_ON",
            "com.ecarx.systemui.SYSTEM_UI_READY",
            "com.lynkco.intent.action.SYSTEM_READY",
            "com.lynkco.intent.action.WAKEUP",
            "com.lynkco.intent.action.STR_RESUME",
            "com.geely.intent.action.SYSTEM_READY",
            "com.geely.intent.action.WAKEUP",
            "ecarx.intent.action.power.STRMODE",
            "ecarx.intent.action.DISPLAY_ON",
            "ecarx.intent.action.carsignal.AVNOFF_OFF",
            "ecarx.intent.action.TEST_EXIT_STR",
        ),
        sleepActions = setOf(
            "ecarx.intent.action.DISPLAY_OFF",
            "ecarx.intent.action.carsignal.AVNOFF_ON",
            "ecarx.intent.action.TEST_ENTER_STR",
            "ecarx.intent.action.power.SHUTDOWN",
            "com.ecarx.intent.action.ACC_OFF",
            "com.ecarx.intent.action.STR_ENTER",
            "com.geely.intent.action.STR_ENTER",
        ),
    )
}
