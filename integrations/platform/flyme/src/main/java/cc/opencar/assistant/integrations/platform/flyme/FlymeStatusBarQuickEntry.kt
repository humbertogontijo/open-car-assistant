package cc.opencar.assistant.integrations.platform.flyme

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import cc.opencar.assistant.api.QuickEntry

/**
 * Flyme Auto status-bar app icon via notification extras (`flag_status_icon_*`).
 * Appears inside TopCarSystemBar (touchable).
 */
class FlymeStatusBarQuickEntry(
    private val iconRes: Int = R.drawable.ic_status_bar_entry,
    private val describe: String = "OCA",
) : QuickEntry {
    override val style: String = STYLE

    @Volatile private var visible: Boolean = true
    @Volatile private var started: Boolean = false

    override fun start(context: Context, listener: QuickEntry.Listener) {
        clickListener = listener
        started = true
        Log.i(TAG, "started id=$ICON_ID")
    }

    override fun stop() {
        if (clickListener != null) clickListener = null
        started = false
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
    }

    override fun notificationContentIntent(context: Context): PendingIntent =
        clickPendingIntent(context)

    override fun decorateNotification(context: Context, notification: Notification, visible: Boolean) {
        this.visible = visible
        putExtras(notification.extras, context, visible)
        notification.contentIntent = clickPendingIntent(context)
    }

    override fun status(): Map<String, Any?> = mapOf(
        "attached" to started,
        "style" to style,
        "statusIconId" to ICON_ID,
        "visible" to visible,
    )

    private fun putExtras(extras: android.os.Bundle, context: Context, visible: Boolean) {
        val icon = Icon.createWithResource(context, iconRes)
        extras.putBoolean(EXTRA_NOTIFICATION, true)
        extras.putInt(EXTRA_ID, ICON_ID)
        extras.putString(EXTRA_DESCRIBE, describe)
        extras.putParcelable(EXTRA_ICON, icon)
        extras.putParcelable(EXTRA_PRESSED_ICON, icon)
        extras.putBoolean(EXTRA_HIDE, !visible)
        // false → SystemUI skips empty plugin panel; contentIntent still fires
        extras.putBoolean(EXTRA_IS_PICK_ON, false)
        extras.putInt(EXTRA_SPACE_X, 1)
        extras.putInt(EXTRA_RANK, ICON_ID)
        extras.putInt(EXTRA_SPECIFIC_WIDTH, 0)
    }

    companion object {
        private const val TAG = "FlymeStatusBarEntry"
        const val STYLE = "flyme_status_icon"

        /** Unique id — must not collide with system icons (≤0x20) or other app slots. */
        const val ICON_ID = 0xC9 // 201

        const val EXTRA_NOTIFICATION = "flag_status_icon_notification"
        const val EXTRA_ID = "flag_status_icon_id"
        const val EXTRA_DESCRIBE = "flag_status_icon_describe"
        const val EXTRA_ICON = "flag_status_icon_icon"
        const val EXTRA_PRESSED_ICON = "flag_status_icon_pressed_icon"
        const val EXTRA_HIDE = "flag_status_icon_hide"
        const val EXTRA_IS_PICK_ON = "flag_status_icon_is_pick_on"
        const val EXTRA_SPACE_X = "flag_status_icon_space_x"
        const val EXTRA_RANK = "flag_status_icon_rank"
        const val EXTRA_SPECIFIC_WIDTH = "flag_status_icon_specific_width"

        const val EXTRA_ICON_LEFT = "extra_icon_left"
        const val EXTRA_ICON_TOP = "extra_icon_top"
        const val EXTRA_ICON_RIGHT = "extra_icon_right"
        const val EXTRA_ICON_BOTTOM = "extra_icon_bottom"

        const val ACTION_ICON_CLICK = "cc.opencar.assistant.STATUS_ICON_CLICK"

        @Volatile
        var clickListener: QuickEntry.Listener? = null

        fun clickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_ICON_CLICK)
                .setClass(context, FlymeStatusBarClickReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, ICON_ID, intent, flags)
        }

        fun dispatchClick(left: Int, top: Int, right: Int, bottom: Int) {
            val anchor = if (right > left && bottom > top) {
                Rect(left, top, right, bottom)
            } else {
                null
            }
            clickListener?.onActivated(anchor)
                ?: Log.w(TAG, "click with no listener (entry not started)")
        }
    }
}
