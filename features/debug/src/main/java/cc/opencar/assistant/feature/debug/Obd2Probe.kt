package cc.opencar.assistant.feature.debug

import android.util.Log
import cc.opencar.assistant.api.PropertyValue
import cc.opencar.assistant.api.ReadOutcome
import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.api.VehicleSession
import java.util.concurrent.atomic.AtomicReference

/**
 * Lab probe for AOSP OBD2 VHAL frames (live / freeze). Read-only; empty frames are normal
 * when the ECU does not populate diagnostic data on this build.
 */
class Obd2Probe(
    private val session: VehicleSession,
) {
    data class FrameRow(
        val name: String,
        val key: String,
        val nativeIdHex: String,
        val family: String = "obd2",
        val status: String,
        val permission: String? = null,
        val value: String? = null,
        val message: String? = null,
        val byteLength: Int? = null,
    )

    data class Report(
        val timestampMs: Long,
        val integrationId: String,
        val results: List<FrameRow>,
        val available: Boolean,
    ) {
        fun summary(): Map<String, Any?> = mapOf(
            "timestampMs" to timestampMs,
            "integrationId" to integrationId,
            "available" to available,
            "counts" to results.groupingBy { it.status }.eachCount(),
            "source" to "OBD2_LIVE_FRAME / OBD2_FREEZE_FRAME (VHAL)",
        )
    }

    private val cache = AtomicReference<Report?>(null)

    fun cached(): Report? = cache.get()

    suspend fun run(force: Boolean = false): Report {
        if (!force) cache.get()?.let { return it }
        Log.i(TAG, "OBD2 probe starting…")
        val results = FRAMES.map { frame -> probeFrame(frame) }
        val available = results.any { it.status == "ok" && (it.byteLength ?: 0) > 0 }
        val report = Report(
            timestampMs = System.currentTimeMillis(),
            integrationId = session.integrationId,
            results = results,
            available = available,
        )
        cache.set(report)
        LogRingBuffer.append(
            "Obd2Probe done available=$available ok=${results.count { it.status == "ok" }}",
        )
        return report
    }

    private suspend fun probeFrame(frame: FrameDef): FrameRow {
        val prop = VehicleProperty(EntityRegistry.NS, frame.key, nativeId = frame.nativeId)
        return when (val out = session.diagnose(prop)) {
            is ReadOutcome.Ok -> {
                val bytes = (out.value as? PropertyValue.BytesVal)?.value
                val preview = when (val v = out.value) {
                    is PropertyValue.BytesVal -> {
                        if (v.value.isEmpty()) "(empty frame)"
                        else "bytes[${v.value.size}] " + v.value.take(24).joinToString("") {
                            "%02x".format(it)
                        } + if (v.value.size > 24) "…" else ""
                    }
                    else -> v?.display()
                }
                FrameRow(
                    name = frame.name,
                    key = frame.key,
                    nativeIdHex = "0x${frame.nativeId.toString(16)}",
                    status = if (bytes != null && bytes.isEmpty()) "unavailable" else "ok",
                    value = preview,
                    byteLength = bytes?.size,
                    message = if (bytes != null && bytes.isEmpty()) {
                        "Frame present but empty on this build"
                    } else null,
                )
            }
            is ReadOutcome.Denied -> FrameRow(
                name = frame.name,
                key = frame.key,
                nativeIdHex = "0x${frame.nativeId.toString(16)}",
                status = "denied",
                permission = out.permission,
                message = out.message,
            )
            is ReadOutcome.Failed -> FrameRow(
                name = frame.name,
                key = frame.key,
                nativeIdHex = "0x${frame.nativeId.toString(16)}",
                status = "failed",
                message = out.message,
            )
            is ReadOutcome.Unavailable -> FrameRow(
                name = frame.name,
                key = frame.key,
                nativeIdHex = "0x${frame.nativeId.toString(16)}",
                status = "unavailable",
                message = "Property not exposed or unreadable",
            )
        }
    }

    private data class FrameDef(val key: String, val name: String, val nativeId: Long)

    companion object {
        private const val TAG = "OaaObd2"

        /** Observed on Antora EX5 EM-i car_service dump. */
        private val FRAMES = listOf(
            FrameDef("obd2_live_frame", "OBD2_LIVE_FRAME", 0x11e00d00L),
            FrameDef("obd2_freeze_frame", "OBD2_FREEZE_FRAME", 0x11e00d01L),
            FrameDef("obd2_freeze_frame_info", "OBD2_FREEZE_FRAME_INFO", 0x11e00d02L),
        )
    }
}
