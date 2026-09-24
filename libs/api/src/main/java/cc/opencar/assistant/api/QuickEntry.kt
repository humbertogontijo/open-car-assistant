package cc.opencar.assistant.api

import android.app.Notification
import android.content.Context
import android.graphics.Rect

/**
 * Platform-specific persistent entry into OCA (status-bar icon, float chip, …).
 * Feature code owns the dropdown / actions; platforms only present the affordance
 * and report activation via [Listener].
 */
interface QuickEntry {
    /** Stable style id for status APIs / UI (`flyme_status_icon`, `float_chip`, …). */
    val style: String

    fun start(context: Context, listener: Listener)
    fun stop()

    /** Show / hide the entry (user toggle in Shortcuts). */
    fun setVisible(visible: Boolean)

    /**
     * Optional: decorate the foreground-service notification (e.g. Flyme status-bar
     * extras + contentIntent). Default is a no-op.
     */
    fun decorateNotification(context: Context, notification: Notification, visible: Boolean) = Unit

    /** Preferred contentIntent for the FG notification, if any. */
    fun notificationContentIntent(context: Context): android.app.PendingIntent? = null

    fun status(): Map<String, Any?>

    fun interface Listener {
        /** User activated the entry. [anchor] is screen bounds when known (status icon). */
        fun onActivated(anchor: Rect?)
    }
}
