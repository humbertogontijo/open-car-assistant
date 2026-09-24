package cc.opencar.assistant.feature.web

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.support.I18nBundle
import kotlinx.coroutines.flow.first

/**
 * First-run readiness for the product shell.
 *
 * Default path is an unprivileged `/data` install:
 * Antora uses VenusVehicleServer gRPC for VHAL, so formal vendor/climate
 * grants are optional. Privileged (priv-app) switches to CarPropertyManager.
 */
object SetupStatus {
    data class PermCheck(
        val id: String,
        val label: String,
        val granted: Boolean,
        val kind: String, // runtime | install | privileged
        val hint: String,
    )

    private val CHECKS = listOf(
        Triple("android.permission.CAMERA", "setup.perm.camera", "runtime"),
        Triple("android.permission.RECORD_AUDIO", "setup.perm.mic", "runtime"),
        Triple("android.car.permission.CAR_SPEED", "setup.perm.speed", "runtime"),
        Triple("android.car.permission.CAR_ENERGY", "setup.perm.energy", "runtime"),
        Triple("android.car.permission.CAR_INFO", "setup.perm.info", "install"),
        Triple("android.car.permission.CAR_POWERTRAIN", "setup.perm.powertrain", "install"),
        Triple("android.car.permission.CAR_VENDOR_EXTENSION", "setup.perm.vendor", "privileged"),
        Triple("android.car.permission.CONTROL_CAR_CLIMATE", "setup.perm.climate", "privileged"),
    )

    suspend fun snapshot(
        context: Context,
        session: VehicleSession,
        prefs: android.content.SharedPreferences,
    ): Map<String, Any?> {
        val i18n = I18nBundle.load(context, session.integrationId)
        val permissions = CHECKS.map { (id, labelKey, kind) ->
            val granted = ContextCompat.checkSelfPermission(context, id) == PackageManager.PERMISSION_GRANTED
            PermCheck(
                id = id,
                label = i18n.t(labelKey),
                granted = granted,
                kind = kind,
                hint = when (kind) {
                    "runtime" -> i18n.t("setup.hint.runtime")
                    "install" -> i18n.t("setup.hint.install")
                    else -> i18n.t("setup.hint.privileged")
                },
            )
        }
        val runtimeOk = permissions.filter { it.kind == "runtime" }.all { it.granted }
        val privilegedOk = permissions.filter { it.kind == "privileged" }.all { it.granted }
        val installOk = permissions.filter { it.kind == "install" }.all { it.granted }
        val telemetry = session.telemetry().first()
        val hasBasicTelemetry =
            telemetry.gear != null || telemetry.speedKmh != null || telemetry.evBatteryPercent != null
        val dismissed = prefs.getBoolean("setup_dismissed", false)
        // Unprivileged-first: finish setup when runtime + telemetry work.
        val complete = (runtimeOk && hasBasicTelemetry) || dismissed
        val needsSetup = !complete

        return mapOf(
            "complete" to complete,
            "needsSetup" to needsSetup,
            "runtimeOk" to runtimeOk,
            "installOk" to installOk,
            "privilegedOk" to privilegedOk,
            "hasBasicTelemetry" to hasBasicTelemetry,
            "dismissed" to dismissed,
            "bridge" to (telemetry.extras["bridge"] ?: "unknown"),
            "accessMode" to (telemetry.extras["accessMode"] ?: "unknown"),
            "integration" to session.integrationId,
            "packageName" to context.packageName,
            "sdk" to Build.VERSION.SDK_INT,
            "locale" to i18n.locale,
            "permissions" to permissions.map {
                mapOf(
                    "id" to it.id,
                    "label" to it.label,
                    "granted" to it.granted,
                    "kind" to it.kind,
                    "hint" to it.hint,
                )
            },
            "steps" to listOf(
                mapOf(
                    "id" to "runtime",
                    "title" to i18n.t("setup.step.runtime"),
                    "done" to runtimeOk,
                    "detail" to i18n.t("setup.step.runtime.detail"),
                ),
                mapOf(
                    "id" to "telemetry",
                    "title" to i18n.t("setup.step.telemetry"),
                    "done" to hasBasicTelemetry,
                    "detail" to i18n.t("setup.step.telemetry.detail"),
                ),
                mapOf(
                    "id" to "privileged",
                    "title" to i18n.t("setup.step.privileged"),
                    "done" to privilegedOk,
                    "optional" to true,
                    "detail" to i18n.t("setup.step.privileged.detail"),
                ),
            ),
            "actions" to mapOf(
                "grant" to i18n.t("setup.action.grant"),
                "elevate" to i18n.t("setup.action.elevate"),
                "host" to i18n.t("setup.action.host"),
                "reboot" to i18n.t("setup.action.reboot"),
                "refresh" to i18n.t("setup.action.refresh"),
            ),
            "adbHints" to listOf(
                "./tools/oca-setup -i ${session.integrationId} -H <ip> setup",
                "./tools/oca-setup -i ${session.integrationId} -H <ip> setup --privileged",
                "adb shell pm grant --user 11 ${context.packageName} android.car.permission.CAR_SPEED",
                "adb shell pm grant --user 11 ${context.packageName} android.car.permission.CAR_ENERGY",
            ),
            "elevate" to PrivilegeElevator(context, session.integrationId).status(),
        )
    }
}
