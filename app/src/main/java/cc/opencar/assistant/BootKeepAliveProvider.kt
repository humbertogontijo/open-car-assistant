package cc.opencar.assistant

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import cc.opencar.assistant.api.PendingWake

/**
 * Early process hook: ContentProviders run before [OcaApp.onCreate] finishes
 * heavy work. When any component brings the process up (boot / vendor wake),
 * this starts the FGS so we stay alive even if later init races.
 */
class BootKeepAliveProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        Log.i(TAG, "onCreate — ensuring AssistantService")
        try {
            PendingWake.startAssistantService(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "start service failed: ${t.message}")
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "BootKeepAliveProvider"
    }
}
