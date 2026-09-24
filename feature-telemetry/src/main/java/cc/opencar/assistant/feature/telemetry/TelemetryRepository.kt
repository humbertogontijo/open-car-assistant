package cc.opencar.assistant.feature.telemetry

import cc.opencar.assistant.api.TelemetrySnapshot
import cc.opencar.assistant.api.VehicleSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TelemetryRepository(private val session: VehicleSession) {
    fun snapshots(): Flow<TelemetrySnapshot> = session.telemetry()

    fun statusLines(): Flow<List<String>> = snapshots().map { s ->
        buildList {
            add("Gear: ${s.gear ?: "—"}")
            add("Speed: ${s.speedKmh?.let { "%.1f km/h".format(it) } ?: "—"}")
            add("EV SOC: ${s.evBatteryPercent?.let { "%.0f%%".format(it) } ?: "—"}")
            add("Hybrid SOC: ${s.hybridSocPercent?.let { "%.0f%%".format(it) } ?: "—"}")
            add("Range: ${s.rangeKm?.let { "%.0f km".format(it) } ?: "—"}")
            add("Drive: ${s.driveMode ?: "—"}")
            add("Regen: ${s.regenLevel ?: "—"}")
            add("HVAC: ${if (s.hvacPower == true) "ON" else if (s.hvacPower == false) "OFF" else "—"} ${s.hvacTempC?.let { "%.1f°C".format(it) } ?: ""}")
            add("Charge: ${s.chargeCurrentA?.let { "%.0f A".format(it) } ?: "—"} plug=${s.chargePlugConnected}")
        }
    }
}
