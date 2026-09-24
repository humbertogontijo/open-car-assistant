package cc.opencar.assistant.integrations.common

import android.content.Context
import cc.opencar.assistant.api.Capability
import cc.opencar.assistant.api.PlatformVariant
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.api.WellKnownProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Declarative platform definition loaded from `platform.json` in an integration's assets.
 */
data class PlatformConfig(
    val id: String,
    val displayName: String,
    val backend: String,
    val match: List<String>,
    val capabilities: Set<Capability>,
    val variants: List<VariantDef>,
    val bindings: Map<String, Binding>,
    val writableAllowlist: Set<Int>,
    val driveModeEnum: Map<Int, String> = emptyMap(),
) {
    data class Binding(val nativeId: Int, val areaId: Int = 0, val functionId: Int? = null)
    data class VariantDef(
        val id: String,
        val label: String,
        val extraCapabilities: Set<Capability>,
        val detect: String? = null,
    )

    fun bindingFor(prop: VehicleProperty): Pair<Int, Int>? {
        val b = bindings[prop.key] ?: return null
        return b.nativeId to b.areaId
    }

    fun toPlatformVariant(id: String): PlatformVariant {
        val v = variants.firstOrNull { it.id == id } ?: return PlatformVariant(id, id)
        return PlatformVariant(v.id, v.label, v.extraCapabilities)
    }

    companion object {
        fun load(context: Context, assetPath: String = "platform.json"): PlatformConfig {
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            return parse(text)
        }

        fun parse(json: String): PlatformConfig {
            val root = JSONObject(json)
            val match = root.optJSONArray("match")?.toStringList() ?: emptyList()
            val caps = root.optJSONArray("capabilities")?.toStringList()
                ?.mapNotNull { runCatching { Capability.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
            val variants = mutableListOf<VariantDef>()
            val vArr = root.optJSONArray("variants")
            if (vArr != null) {
                for (i in 0 until vArr.length()) {
                    val o = vArr.getJSONObject(i)
                    val extra = o.optJSONArray("extraCapabilities")?.toStringList()
                        ?.mapNotNull { runCatching { Capability.valueOf(it) }.getOrNull() }
                        ?.toSet()
                        ?: emptySet()
                    variants += VariantDef(
                        id = o.getString("id"),
                        label = o.optString("label", o.getString("id")),
                        extraCapabilities = extra,
                        detect = if (o.has("detect")) o.optString("detect") else null,
                    )
                }
            }
            val bindings = mutableMapOf<String, Binding>()
            val bObj = root.optJSONObject("bindings")
            if (bObj != null) {
                val keys = bObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val o = bObj.getJSONObject(key)
                    val native = parseId(o.opt("nativeId") ?: o.opt("id"))
                    val area = o.optInt("areaId", 0)
                    val fn = if (o.has("functionId")) parseId(o.get("functionId")) else null
                    bindings[key] = Binding(native, area, fn)
                }
            }
            val allow = mutableSetOf<Int>()
            val aArr = root.optJSONArray("writableAllowlist")
            if (aArr != null) {
                for (i in 0 until aArr.length()) {
                    allow += parseId(aArr.get(i))
                }
            }
            val enumMap = mutableMapOf<Int, String>()
            val eObj = root.optJSONObject("driveModeEnum")
            if (eObj != null) {
                val keys = eObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    enumMap[k.toInt()] = eObj.getString(k)
                }
            }
            return PlatformConfig(
                id = root.getString("id"),
                displayName = root.optString("displayName", root.getString("id")),
                backend = root.optString("backend", "vhal"),
                match = match,
                capabilities = caps,
                variants = variants,
                bindings = bindings,
                writableAllowlist = allow,
                driveModeEnum = enumMap,
            )
        }

        private fun parseId(raw: Any?): Int {
            when (raw) {
                is Number -> return raw.toInt()
                is String -> {
                    val s = raw.trim()
                    return if (s.startsWith("0x") || s.startsWith("0X")) {
                        s.substring(2).toLong(16).toInt()
                    } else {
                        s.toLong().toInt()
                    }
                }
                else -> error("bad id: $raw")
            }
        }

        private fun JSONArray.toStringList(): List<String> {
            val out = mutableListOf<String>()
            for (i in 0 until length()) out += getString(i)
            return out
        }
    }
}

/** Resolve well-known property key string to VehicleProperty. */
fun wellKnownByKey(key: String): VehicleProperty? {
    return try {
        val field = WellKnownProperties::class.java.getDeclaredField(key.uppercase().replace('-', '_'))
        field.isAccessible = true
        field.get(WellKnownProperties) as? VehicleProperty
    } catch (_: Throwable) {
        // try lowercase field names via reflection on known vals
        WellKnownProperties::class.java.declaredFields
            .firstOrNull {
                it.type == VehicleProperty::class.java &&
                    (it.get(null) as VehicleProperty).key == key
            }?.get(null) as? VehicleProperty
    }
}

fun loadCatalogTsv(context: Context, asset: String = "vhal_named_ids.tsv"): List<Pair<Long, String>> {
    val out = mutableListOf<Pair<Long, String>>()
    try {
        context.assets.open(asset).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 3) {
                        val id = parts[0].toLongOrNull() ?: return@forEach
                        out += id to parts[2]
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    return out
}
