package cc.opencar.assistant.integrations.platform.flyme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manifest-exported receiver for Flyme status-bar icon taps.
 * SystemUI delivers our PendingIntent here.
 */
class FlymeStatusBarClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != FlymeStatusBarQuickEntry.ACTION_ICON_CLICK) return
        val left = intent.getIntExtra(FlymeStatusBarQuickEntry.EXTRA_ICON_LEFT, -1)
        val top = intent.getIntExtra(FlymeStatusBarQuickEntry.EXTRA_ICON_TOP, -1)
        val right = intent.getIntExtra(FlymeStatusBarQuickEntry.EXTRA_ICON_RIGHT, -1)
        val bottom = intent.getIntExtra(FlymeStatusBarQuickEntry.EXTRA_ICON_BOTTOM, -1)
        Log.i(TAG, "status icon click bounds=[$left,$top,$right,$bottom]")
        FlymeStatusBarQuickEntry.dispatchClick(left, top, right, bottom)
    }

    companion object {
        private const val TAG = "FlymeStatusBarClick"
    }
}
