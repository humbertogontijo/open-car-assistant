package cc.opencar.assistant.feature.web

import android.content.Context
import android.os.Build
import android.util.Log
import cc.opencar.assistant.support.I18nBundle
import java.io.File

/**
 * On-device privileged install via `su` (userdebug HUs). Host CLI remains the fallback.
 */
class PrivilegeElevator(
    private val context: Context,
    private val integrationId: String? = null,
) {
    private fun i18n(): I18nBundle = I18nBundle.load(context, "common")

    private fun platformId(): String =
        integrationId?.takeIf { it.isNotBlank() } ?: "PLATFORM_ID"

    fun status(): Map<String, Any?> {
        val pkg = context.packageName
        val codePath = try {
            context.packageManager.getApplicationInfo(pkg, 0).sourceDir
        } catch (_: Exception) {
            null
        }
        val privileged = codePath?.contains("/system") == true ||
            codePath?.contains("/priv-app") == true
        return mapOf(
            "package" to pkg,
            "codePath" to codePath,
            "alreadyPrivileged" to privileged,
            "suAvailable" to suAvailable(),
            "userdebug" to (Build.TYPE.contains("userdebug") || Build.TAGS?.contains("test-keys") == true),
            "integrationId" to integrationId,
            "hostCommand" to "./tools/oca-setup -i ${platformId()} -H CAR_IP setup --privileged",
        )
    }

    fun elevate(): Map<String, Any?> {
        val st = status()
        val bundle = i18n()
        if (st["alreadyPrivileged"] == true) {
            return mapOf(
                "ok" to true,
                "needsReboot" to false,
                "message" to bundle.t("elevate.already", "Already installed as privileged"),
            )
        }
        if (st["suAvailable"] != true) {
            return mapOf(
                "ok" to false,
                "fallback" to "host",
                "message" to bundle.t("elevate.no_su", "No su on this HU — use the PC command"),
                "hostCommand" to st["hostCommand"],
            )
        }
        val apk = st["codePath"] as? String
            ?: return mapOf(
                "ok" to false,
                "message" to bundle.t("elevate.unknown_apk", "Unknown APK path"),
            )
        val pkg = context.packageName
        val privDir = "/system/priv-app/OpenCarAssistant"
        val remoteApk = "$privDir/OpenCarAssistant.apk"
        val xmlDst = "/system/etc/permissions/privapp-permissions-opencar.xml"
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <permissions>
              <privapp-permissions package="$pkg">
                <permission name="android.car.permission.CAR_VENDOR_EXTENSION" />
                <permission name="android.car.permission.CONTROL_CAR_CLIMATE" />
                <permission name="android.car.permission.CAR_INFO" />
                <permission name="android.car.permission.CAR_POWERTRAIN" />
                <permission name="android.car.permission.CAR_ENERGY" />
                <permission name="android.car.permission.CAR_SPEED" />
              </privapp-permissions>
            </permissions>
        """.trimIndent()
        val tmpXml = File(context.cacheDir, "privapp-permissions-opencar.xml")
        tmpXml.writeText(xml)
        val script = """
            mount -o rw,remount /system || mount -o rw,remount /
            mkdir -p $privDir
            cp '$apk' $remoteApk
            chmod 644 $remoteApk
            cp '${tmpXml.absolutePath}' $xmlDst
            chmod 644 $xmlDst
            pm uninstall $pkg >/dev/null 2>&1 || true
            echo OK
        """.trimIndent()
        return try {
            val result = runSu(script)
            val ok = result.contains("OK")
            mapOf(
                "ok" to ok,
                "needsReboot" to ok,
                "message" to if (ok) {
                    bundle.t("elevate.ready", "priv-app ready — reboot the car")
                } else {
                    bundle.t("elevate.failed", "Failed: {detail}", mapOf("detail" to result.take(200)))
                },
                "log" to result.take(500),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "elevate failed", t)
            mapOf(
                "ok" to false,
                "fallback" to "host",
                "message" to (t.message ?: bundle.t("elevate.failed_generic", "elevate failed")),
                "hostCommand" to st["hostCommand"],
            )
        }
    }

    fun reboot(): Map<String, Any?> {
        val bundle = i18n()
        if (!suAvailable()) {
            return mapOf(
                "ok" to false,
                "message" to bundle.t("elevate.reboot_manual", "No su — reboot manually"),
            )
        }
        return try {
            runSu("reboot")
            mapOf("ok" to true)
        } catch (t: Throwable) {
            mapOf("ok" to false, "message" to (t.message ?: bundle.t("elevate.reboot_failed", "reboot failed")))
        }
    }

    private fun runSu(script: String): String {
        val p = Runtime.getRuntime().exec(arrayOf("su", "0", "sh", "-c", script))
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        val combined = (out + err).trim()
        if (combined.isNotBlank()) return combined
        val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
        val out2 = p2.inputStream.bufferedReader().readText()
        val err2 = p2.errorStream.bufferedReader().readText()
        p2.waitFor()
        return (out2 + err2).trim()
    }

    private fun suAvailable(): Boolean {
        return try {
            val probes = listOf(
                arrayOf("su", "0", "id"),
                arrayOf("su", "-c", "id"),
            )
            for (argv in probes) {
                try {
                    val p = Runtime.getRuntime().exec(argv)
                    val out = p.inputStream.bufferedReader().readText()
                    p.waitFor()
                    if (out.contains("uid=0")) return true
                } catch (_: Throwable) {
                    // try next
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "PrivilegeElevator"
    }
}
