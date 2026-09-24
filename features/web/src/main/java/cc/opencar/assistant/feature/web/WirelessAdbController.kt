package cc.opencar.assistant.feature.web

import android.content.Context
import android.os.Build
import android.provider.Settings
import cc.opencar.assistant.feature.debug.ContributorDebugState
import cc.opencar.assistant.feature.debug.OcaLog

/**
 * Toggle wireless (TCP) ADB on userdebug HUs.
 *
 * Antora (and similar eng builds) expose `setprop` / `ctl.restart` to the app
 * without root. Their `/system/xbin/su` is shell-group only and uses AOSP
 * `su 0 <cmd>` syntax — Magisk-style `su -c` fails with "invalid uid/gid '-c'".
 */
class WirelessAdbController(
    private val context: Context,
    private val debug: ContributorDebugState,
    private val defaultPort: Int = 5566,
    private val log: OcaLog = OcaLog(),
) {
    fun status(): Map<String, Any?> {
        val portProp = getProp("service.adb.tcp.port")
        val wifiSetting = runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", -1)
        }.getOrDefault(-1)
        val portNum = portProp.toIntOrNull()
        // Prefer explicit tcp.port: -1/0 means off even if adb_wifi_enabled is stuck at 1
        // (Settings.Global writes often need WRITE_SECURE_SETTINGS).
        val enabled = when {
            portNum != null && portNum > 0 -> true
            portNum != null && portNum <= 0 -> false
            portProp == "-1" || portProp == "0" -> false
            wifiSetting == 1 -> true
            else -> false
        }
        val ip = debug.wifiIp()
        val activePort = portNum?.takeIf { it > 0 }
        return mapOf(
            "enabled" to enabled,
            "port" to activePort,
            "portProp" to portProp,
            "adbWifiEnabled" to wifiSetting,
            "ip" to ip,
            "connectHint" to if (enabled && activePort != null) "adb connect $ip:$activePort" else null,
            "canToggle" to (isUserDebug() || suAvailableCached()),
            "suAvailable" to suAvailableCached(),
            "userdebug" to isUserDebug(),
        )
    }

    fun setEnabled(enable: Boolean, port: Int = defaultPort): Map<String, Any?> {
        val p = if (port > 0) port else defaultPort
        log.i(TAG, "setEnabled enable=$enable port=$p")
        return try {
            // Prefer direct setprop/ctl (works on Antora eng without su). Fall back to su.
            val direct = runCatching { toggleDirect(enable, p) }
            val result = if (direct.isSuccess) {
                direct.getOrThrow()
            } else if (suAvailableCached()) {
                log.w(TAG, "direct toggle failed: ${direct.exceptionOrNull()?.message}; trying su")
                toggleViaSu(enable, p)
            } else {
                return mapOf(
                    "ok" to false,
                    "message" to (direct.exceptionOrNull()?.message
                        ?: "Cannot toggle wireless ADB (no setprop/ctl access and no su)"),
                    "status" to status(),
                )
            }
            // adbd restart is async; wait until property + service settle
            Thread.sleep(900)
            val st = status()
            val ok = if (enable) st["enabled"] == true else st["enabled"] != true
            val message = when {
                ok && enable -> "Wireless ADB enabled on :$p"
                ok -> "Wireless ADB disabled"
                else -> "Toggle may have failed: $result (portProp=${st["portProp"]})"
            }
            log.i(TAG, "setEnabled done ok=$ok msg=$message log=$result")
            mapOf(
                "ok" to ok,
                "message" to message,
                "log" to result.take(400),
                "status" to st,
            )
        } catch (t: Throwable) {
            log.e(TAG, "setEnabled failed", t)
            mapOf("ok" to false, "message" to (t.message ?: "failed"), "status" to status())
        }
    }

    private fun toggleDirect(enable: Boolean, port: Int): String {
        val portValue = if (enable) port.toString() else "-1"
        setProp("service.adb.tcp.port", portValue)
        runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                "adb_wifi_enabled",
                if (enable) 1 else 0,
            )
        }
        // stop/start need root on Antora; ctl.restart works without su
        setProp("ctl.restart", "adbd")
        val after = getProp("service.adb.tcp.port")
        return "direct port=$after ctl.restart=adbd"
    }

    private fun toggleViaSu(enable: Boolean, port: Int): String {
        val script = if (enable) {
            """
            setprop service.adb.tcp.port $port
            settings put global adb_wifi_enabled 1 >/dev/null 2>&1 || true
            setprop ctl.restart adbd
            getprop service.adb.tcp.port
            echo OK
            """.trimIndent()
        } else {
            """
            setprop service.adb.tcp.port -1
            settings put global adb_wifi_enabled 0 >/dev/null 2>&1 || true
            setprop ctl.restart adbd
            getprop service.adb.tcp.port
            echo OK
            """.trimIndent()
        }
        return runSu(script)
    }

    private fun getProp(name: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        } catch (_: Throwable) {
            ""
        }
    }

    private fun setProp(name: String, value: String) {
        val p = Runtime.getRuntime().exec(arrayOf("setprop", name, value))
        p.waitFor()
    }

    private fun isUserDebug(): Boolean =
        Build.TYPE.contains("userdebug") || Build.TAGS?.contains("test-keys") == true

    private fun suAvailableCached(): Boolean {
        synchronized(suLock) {
            suCached?.let { return it }
            val v = probeSu()
            suCached = v
            return v
        }
    }

    private fun probeSu(): Boolean {
        for (argv in suProbeArgvs()) {
            try {
                val p = Runtime.getRuntime().exec(argv)
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                if (out.contains("uid=0")) return true
            } catch (_: Throwable) {
                // try next syntax
            }
        }
        return false
    }

    private fun runSu(script: String): String {
        var last = ""
        for (prefix in suCommandPrefixes()) {
            try {
                val argv = prefix + arrayOf(script)
                val p = Runtime.getRuntime().exec(argv)
                val out = p.inputStream.bufferedReader().readText()
                val err = p.errorStream.bufferedReader().readText()
                p.waitFor()
                last = (out + err).trim()
                if (last.contains("OK") || last.contains("uid=0")) return last
            } catch (t: Throwable) {
                last = t.message ?: "exec failed"
            }
        }
        return last
    }

    private fun suProbeArgvs(): List<Array<String>> = listOf(
        arrayOf("su", "0", "id"),
        arrayOf("su", "root", "id"),
        arrayOf("su", "-c", "id"),
    )

    private fun suCommandPrefixes(): List<Array<String>> = listOf(
        arrayOf("su", "0", "sh", "-c"),
        arrayOf("su", "root", "sh", "-c"),
        arrayOf("su", "-c"),
    )

    companion object {
        private const val TAG = "WirelessAdb"
        private val suLock = Any()
        @Volatile private var suCached: Boolean? = null
    }
}
