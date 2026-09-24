package cc.opencar.assistant.api

data class PlatformVariant(
    val id: String,
    val label: String,
    val extraCapabilities: Set<Capability> = emptySet(),
    val propertyOverrides: Map<String, VehicleProperty> = emptyMap(),
)

data class TelemetrySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val gear: Int? = null,
    val speedKmh: Float? = null,
    val evBatteryPercent: Float? = null,
    val fuelCapacityMl: Float? = null,
    val fuelPercent: Float? = null,
    val rangeKm: Float? = null,
    val rangeEvKm: Float? = null,
    val rangeFuelKm: Float? = null,
    val odometerKm: Float? = null,
    val hvacPower: Boolean? = null,
    val hvacTempC: Float? = null,
    val hvacFan: Int? = null,
    val tempAmbientC: Float? = null,
    val tempIndoorC: Float? = null,
    val batteryTempC: Float? = null,
    val hybridSocPercent: Float? = null,
    val chargeCurrentA: Float? = null,
    val chargePlugConnected: Boolean? = null,
    val chargeEstimatedTimeMin: Float? = null,
    val chargeEnergyKwh: Float? = null,
    val chargeWorkCurrentA: Float? = null,
    val chargeWorkVoltageV: Float? = null,
    val dischargeSocPercent: Float? = null,
    val avgEnergyKwh100km: Float? = null,
    val avgFuelL100km: Float? = null,
    val energyFlowDriving: Float? = null,
    val energyFlowBattery: Float? = null,
    val energyFlowClimate: Float? = null,
    val maintenanceMileageKm: Float? = null,
    val sinceMaintenanceKm: Float? = null,
    val driveMode: String? = null,
    val regenLevel: Int? = null,
    val ignitionState: Int? = null,
    val extras: Map<String, String> = emptyMap(),
)

sealed class VehicleEvent {
    data object Boot : VehicleEvent()
    data object ScreenOn : VehicleEvent()
    data class GearChanged(val gear: Int) : VehicleEvent()
    data class IgnitionChanged(val state: Int) : VehicleEvent()
    /** Steering-wheel hard key press edge (platform-specific key id, e.g. "custom"). */
    data class WheelKeyPressed(val key: String) : VehicleEvent()
    /** Steering-wheel hard key held past the long-press threshold. */
    data class WheelKeyLongPressed(val key: String) : VehicleEvent()
    /** Connected Wi‑Fi SSID changed (null = disconnected / unknown). */
    data class WifiSsidChanged(val ssid: String?) : VehicleEvent()
    /** Bound control/sensor value changed (entity id + new display value). */
    data class EntityValueChanged(val entityId: String, val value: String?) : VehicleEvent()
}

data class CameraSource(
    val id: String,
    val label: String,
    val cameraId: String,
)
