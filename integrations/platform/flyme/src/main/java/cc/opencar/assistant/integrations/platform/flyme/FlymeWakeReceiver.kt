package cc.opencar.assistant.integrations.platform.flyme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cc.opencar.assistant.api.PendingWake

/**
 * Manifest-registered keepalive for Flyme / ECARX wake & sleep.
 *
 * Lives in `:integrations:platform:flyme` (library manifest merges into the app).
 * Dynamic receivers in the app FGS miss these when the process is dead during STR
 * (ACC_ON / STR_RESUME and related vendor actions).
 *
 * Does not reference app types: persists [PendingWake] and starts the FGS by class name.
 */
class FlymeWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val signals = FlymePlatform.wakeSignals()
        val kind = when (action) {
            in signals.wakeActions -> "on"
            in signals.sleepActions -> "off"
            else -> return
        }
        val source = "manifest_$action"
        Log.i(TAG, "vendor $kind from $action")
        PendingWake.persist(context, kind, source)
        try {
            PendingWake.startAssistantService(context, kind = kind, source = source)
        } catch (t: Throwable) {
            Log.w(TAG, "start service failed: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "FlymeWakeReceiver"
    }
}
