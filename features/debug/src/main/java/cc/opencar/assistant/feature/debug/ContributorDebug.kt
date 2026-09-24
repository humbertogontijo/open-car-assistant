package cc.opencar.assistant.feature.debug

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

object LogRingBuffer {
    private const val MAX = 2000
    private val lines = ConcurrentLinkedDeque<String>()

    fun append(line: String) {
        lines.addLast("${System.currentTimeMillis()} $line")
        while (lines.size > MAX) lines.pollFirst()
    }

    fun snapshot(): List<String> = lines.toList()

    fun clear() = lines.clear()
}

class ContributorDebugState(context: Context) {
    private val prefs = context.getSharedPreferences("oca_debug", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var contributorMode: Boolean
        get() = prefs.getBoolean("contributor", isUserDebugDevice())
        set(value) = prefs.edit().putBoolean("contributor", value).apply()

    val token: String
        get() {
            val existing = prefs.getString("token", null)
            if (existing != null) return existing
            val created = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("token", created).apply()
            return created
        }

    fun rotateToken(): String {
        val created = UUID.randomUUID().toString().take(8)
        prefs.edit().putString("token", created).apply()
        return created
    }

    fun checkToken(provided: String?): Boolean {
        if (!contributorMode) return false
        return provided != null && provided == token
    }

    fun wifiIp(): String {
        return try {
            val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff,
            )
        } catch (_: Exception) {
            "unknown"
        }
    }

    fun adbHint(port: Int = 5566): String = "adb connect ${wifiIp()}:$port"

    fun buildFingerprint(): String = Build.FINGERPRINT

    fun exportZip(
        integrationJson: String,
        telemetryJson: String,
        identityJson: String,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            put("logs.txt", LogRingBuffer.snapshot().joinToString("\n"))
            put("integration.json", integrationJson)
            put("telemetry.json", telemetryJson)
            put("identity.json", identityJson)
            put("device.txt", "fingerprint=${Build.FINGERPRINT}\nmodel=${Build.MODEL}\ndevice=${Build.DEVICE}\nhardware=${Build.HARDWARE}\n")
        }
        return baos.toByteArray()
    }

    private fun isUserDebugDevice(): Boolean =
        Build.TYPE.equals("userdebug", ignoreCase = true) ||
            Build.TAGS?.contains("test-keys") == true

    companion object {
        fun androidId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }
}

class OcaLog {
    fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
        LogRingBuffer.append("D/$tag: $msg")
    }

    fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
        LogRingBuffer.append("I/$tag: $msg")
    }

    fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
        LogRingBuffer.append("W/$tag: $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        android.util.Log.e(tag, msg, t)
        LogRingBuffer.append("E/$tag: $msg ${t?.message ?: ""}")
    }
}
