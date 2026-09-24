package cc.opencar.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import cc.opencar.assistant.api.PendingWake

/**
 * Starts OCA on boot / package replace, and catches AOSP screen edges when the
 * process was not running. Vendor Flyme / ECARX intents live in
 * [cc.opencar.assistant.integrations.platform.flyme.FlymeWakeReceiver].
 *
 * Mirrors Surcamx BootReceiver + KeepAliveReceiver AOSP filters: directBootAware,
 * LOCKED_BOOT, USER_UNLOCKED, DREAMING_STOPPED.
 *
 * Cold boot often never re-emits SCREEN_ON (display already interactive) — so
 * BOOT_COMPLETED / USER_UNLOCKED also count as a screen-on edge for shortcuts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "action=$action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_UNLOCKED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            -> {
                // Display is typically already on after boot — synthesize screen-on
                // so shortcuts with a screen/on trigger still run.
                val source = "manifest_$action"
                PendingWake.persist(context, "on", source)
                startServiceAndRuntime(context)
                (context.applicationContext as? OcaApp)?.runtime?.notifyScreenOn(source)
            }
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_DREAMING_STOPPED,
            -> {
                val source = "manifest_$action"
                PendingWake.persist(context, "on", source)
                startServiceAndRuntime(context)
                (context.applicationContext as? OcaApp)?.runtime?.notifyScreenOn(source)
            }
            Intent.ACTION_SCREEN_OFF -> {
                val source = "manifest_$action"
                PendingWake.persist(context, "off", source)
                startServiceAndRuntime(context)
                (context.applicationContext as? OcaApp)?.runtime?.notifyScreenOff(source)
            }
        }
    }

    private fun startServiceAndRuntime(context: Context) {
        try {
            PendingWake.startAssistantService(context)
        } catch (t: Throwable) {
            Log.w(TAG, "start service failed: ${t.message}")
        }
        val unlocked = runCatching {
            context.getSystemService(UserManager::class.java)?.isUserUnlocked == true
        }.getOrDefault(true)
        if (!unlocked) {
            Log.i(TAG, "user locked — FGS queued; deferring runtime until unlock")
            return
        }
        (context.applicationContext as? OcaApp)?.runtime?.startAsync()
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
