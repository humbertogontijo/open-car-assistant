package cc.opencar.assistant.feature.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

class ApkInstaller(private val context: Context) {
    data class InstallResult(val ok: Boolean, val message: String, val sha256: String? = null)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun install(apk: File, expectedSha256: String? = null): InstallResult {
        if (!apk.exists()) return InstallResult(false, "APK not found")
        val hash = sha256(apk)
        if (expectedSha256 != null && !expectedSha256.equals(hash, ignoreCase = true)) {
            return InstallResult(false, "SHA-256 mismatch", hash)
        }
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            session.openWrite("oca.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(ACTION_INSTALL_COMPLETE).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
            session.close()
            InstallResult(true, "Install session committed", hash)
        } catch (t: Throwable) {
            Log.e(TAG, "install failed", t)
            InstallResult(false, t.message ?: "install failed", hash)
        }
    }

    fun installDir(): File =
        File(context.getExternalFilesDir(null), "incoming-apks").also { it.mkdirs() }

    companion object {
        const val ACTION_INSTALL_COMPLETE = "cc.opencar.assistant.INSTALL_COMPLETE"
        private const val TAG = "OcaInstall"
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i("OcaInstall", "status=$status msg=$msg")
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (confirm != null) context.startActivity(confirm)
        }
    }
}
