package cc.opencar.assistant.feature.debug

import android.content.Context
import android.util.Log
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.api.ReadOutcome
import cc.opencar.assistant.api.VehicleSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Walks the vehicle catalog and records readable / permission-denied / failed props.
 */
class CatalogProbe(
    private val context: Context,
    private val session: VehicleSession,
) {
    data class PropResult(
        val name: String,
        val key: String,
        val nativeIdHex: String?,
        val family: String,
        val writable: Boolean,
        val status: String, // ok | denied | unavailable | failed
        val permission: String? = null,
        val areaId: Int? = null,
        val value: String? = null,
        val message: String? = null,
    )

    data class ProbeReport(
        val timestampMs: Long,
        val integrationId: String,
        val catalogSize: Int,
        val results: List<PropResult>,
    ) {
        fun summary(): Map<String, Any> {
            val byStatus = results.groupingBy { it.status }.eachCount()
            val byFamily = results.groupBy { it.family }.mapValues { (_, list) ->
                mapOf(
                    "total" to list.size,
                    "ok" to list.count { it.status == "ok" },
                    "denied" to list.count { it.status == "denied" },
                    "unavailable" to list.count { it.status == "unavailable" },
                    "failed" to list.count { it.status == "failed" },
                )
            }
            return mapOf(
                "timestampMs" to timestampMs,
                "integrationId" to integrationId,
                "catalogSize" to catalogSize,
                "counts" to byStatus,
                "families" to byFamily,
            )
        }

        fun toJsonArray(): JSONArray {
            val arr = JSONArray()
            results.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("name", r.name)
                        .put("key", r.key)
                        .put("id", r.nativeIdHex)
                        .put("family", r.family)
                        .put("writable", r.writable)
                        .put("status", r.status)
                        .put("permission", r.permission)
                        .put("areaId", r.areaId)
                        .put("value", r.value)
                        .put("message", r.message),
                )
            }
            return arr
        }
    }

    private val cache = AtomicReference<ProbeReport?>(null)

    fun cached(): ProbeReport? = cache.get() ?: loadFromDisk()

    suspend fun run(force: Boolean = false): ProbeReport {
        if (!force) cached()?.let { return it }
        Log.i(TAG, "Catalog probe starting…")
        LogRingBuffer.append("CatalogProbe start catalog=${session.catalog().size}")
        val results = mutableListOf<PropResult>()
        for (entry in session.catalog()) {
            results += probeOne(entry)
        }
        val report = ProbeReport(
            timestampMs = System.currentTimeMillis(),
            integrationId = session.integrationId,
            catalogSize = session.catalog().size,
            results = results,
        )
        cache.set(report)
        saveToDisk(report)
        LogRingBuffer.append(
            "CatalogProbe done ok=${results.count { it.status == "ok" }} " +
                "denied=${results.count { it.status == "denied" }}",
        )
        return report
    }

    private suspend fun probeOne(entry: CatalogEntry): PropResult {
        val family = familyOf(entry.name)
        val outcome = session.diagnose(entry.property)
        return when (outcome) {
            is ReadOutcome.Ok -> PropResult(
                name = entry.name,
                key = entry.property.key,
                nativeIdHex = entry.property.nativeId?.toString(16),
                family = family,
                writable = entry.writable,
                status = "ok",
                areaId = outcome.areaId,
                value = outcome.value?.display()?.let { redact(entry.name, it) },
            )
            is ReadOutcome.Denied -> PropResult(
                name = entry.name,
                key = entry.property.key,
                nativeIdHex = entry.property.nativeId?.toString(16),
                family = family,
                writable = entry.writable,
                status = "denied",
                permission = outcome.permission,
                areaId = outcome.areaId,
                message = outcome.message,
            )
            is ReadOutcome.Failed -> PropResult(
                name = entry.name,
                key = entry.property.key,
                nativeIdHex = entry.property.nativeId?.toString(16),
                family = family,
                writable = entry.writable,
                status = "failed",
                areaId = outcome.areaId,
                message = outcome.message,
            )
            is ReadOutcome.Unavailable -> PropResult(
                name = entry.name,
                key = entry.property.key,
                nativeIdHex = entry.property.nativeId?.toString(16),
                family = family,
                writable = entry.writable,
                status = "unavailable",
                areaId = outcome.areaId,
            )
        }
    }

    private fun cacheFile(): File =
        File(context.filesDir, "oca_catalog_probe.json")

    private fun saveToDisk(report: ProbeReport) {
        runCatching {
            val root = JSONObject()
                .put("timestampMs", report.timestampMs)
                .put("integrationId", report.integrationId)
                .put("catalogSize", report.catalogSize)
                .put("results", report.toJsonArray())
            cacheFile().writeText(root.toString())
        }
    }

    private fun loadFromDisk(): ProbeReport? {
        return runCatching {
            val root = JSONObject(cacheFile().readText())
            val arr = root.getJSONArray("results")
            val list = mutableListOf<PropResult>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += PropResult(
                    name = o.optString("name"),
                    key = o.optString("key"),
                    nativeIdHex = o.optString("id").ifBlank { null },
                    family = o.optString("family", "other"),
                    writable = o.optBoolean("writable"),
                    status = o.optString("status"),
                    permission = o.optString("permission").ifBlank { null },
                    areaId = if (o.has("areaId") && !o.isNull("areaId")) o.getInt("areaId") else null,
                    value = o.optString("value").ifBlank { null },
                    message = o.optString("message").ifBlank { null },
                )
            }
            ProbeReport(
                timestampMs = root.optLong("timestampMs"),
                integrationId = root.optString("integrationId"),
                catalogSize = root.optInt("catalogSize"),
                results = list,
            ).also { cache.set(it) }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OcaProbe"

        fun familyOf(name: String): String {
            val n = name.uppercase()
            return when {
                n.startsWith("SETTING_FUNC_") || n.startsWith("SETTING_") -> "setting"
                n.startsWith("HYBRID_") -> "hybrid"
                n.startsWith("CHARGE_") -> "charge"
                n.startsWith("HVAC_") -> "hvac"
                n.startsWith("DM_") || n.contains("DRIVE_MODE") -> "drive"
                n.contains("LANE") || n.contains("AEB") || n.contains("COLLISION") ||
                    n.contains("CROSS_TRAFFIC") || n.contains("FCW") -> "adas"
                n.contains("LOCK") || n.contains("UNLOCK") -> "lock"
                n.contains("HUD") -> "hud"
                n.contains("AMBIENCE") -> "ambience"
                n.contains("BRIGHTNESS") || n.contains("BACKLIGHT") -> "brightness"
                n.contains("WINDOW") -> "window"
                n.contains("SEAT") -> "seat"
                n.contains("MIRROR") -> "mirror"
                n.startsWith("INFO_") || n.startsWith("PERF_") || n.contains("GEAR") ||
                    n.contains("IGNITION") || n.contains("RANGE") || n.contains("BATTERY") -> "telemetry"
                else -> "other"
            }
        }

        private fun redact(name: String, value: String): String {
            if (name.contains("VIN", ignoreCase = true)) {
                return if (value.length < 8) "[redacted]" else value.take(3) + "****" + value.takeLast(4)
            }
            return value.take(80)
        }
    }
}
