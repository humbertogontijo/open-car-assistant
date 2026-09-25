package cc.opencar.assistant.feature.web

import cc.opencar.assistant.api.TelemetrySnapshot
import cc.opencar.assistant.support.I18nBundle

/** Shared shape for `/api/status` telemetry and `/api/events` telemetry frames. */
internal fun telemetryPayload(snap: TelemetrySnapshot, i18n: I18nBundle): Map<String, Any?> {
    val driveModeLabel = i18n.resolveMaybe(snap.driveMode)
        ?: i18n.valueLabel("drive_mode", snap.driveMode)
        ?: snap.driveMode
    return mapOf(
        "gear" to snap.gear,
        "gearLabel" to snap.gear?.let { i18n.valueLabel("gear", it) },
        "speedKmh" to snap.speedKmh,
        "evBatteryPercent" to snap.evBatteryPercent,
        "hybridSocPercent" to snap.hybridSocPercent,
        "rangeKm" to snap.rangeKm,
        "driveMode" to driveModeLabel,
        "driveModeKey" to snap.driveMode,
        "energyMode" to (snap.extras["energyMode"]?.let { i18n.resolveMaybe(it) ?: it }),
        "energyModeKey" to snap.extras["energyMode"],
        "regenLevel" to snap.regenLevel,
        "regenLabel" to snap.regenLevel?.let { i18n.valueLabel("regen", it) },
        "hvacPower" to snap.hvacPower,
        "hvacTempC" to snap.hvacTempC,
        "hvacFan" to snap.hvacFan,
        "chargeCurrentA" to snap.chargeCurrentA,
        "chargePlugConnected" to snap.chargePlugConnected,
        "ignitionState" to snap.ignitionState,
        "ignitionLabel" to snap.ignitionState?.let { i18n.valueLabel("ignition", it) },
        "model" to snap.extras["model"],
        "parkingBrake" to snap.extras["parkingBrake"],
        "parkingBrakeLabel" to snap.extras["parkingBrake"]?.let { i18n.valueLabel("parking_brake", it) },
        "extras" to snap.extras,
    )
}
