package cc.opencar.assistant.integrations.common

import android.content.Context
import cc.opencar.assistant.api.AndroidVolumeGroup
import cc.opencar.assistant.api.CameraRoleConfig
import cc.opencar.assistant.api.CameraSource
import cc.opencar.assistant.api.Capability
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.api.PlatformVariant
import cc.opencar.assistant.api.VehicleProperty
import org.json.JSONArray
import org.json.JSONObject

/**
 * Declarative platform definition loaded from `platform.json` in an integration's assets.
 *
 * Shared fragments live under `platform/<name>.json` (e.g. aosp, android) and are pulled in
 * via `"extends": ["aosp", "android"]`.
 */
data class PlatformConfig(
    val id: String,
    val displayName: String,
    val backend: String,
    val match: List<String>,
    val capabilities: Set<Capability>,
    val variants: List<VariantDef>,
    val properties: List<PropertyDef> = emptyList(),
    val android: AndroidConfig = AndroidConfig(),
    val driveModeEnum: Map<Int, String> = emptyMap(),
    /** Camera2 id → surround role map (`platform.json` → `cameras`). */
    val cameras: List<CameraRoleConfig> = emptyList(),
) {
    data class Binding(val nativeId: Int, val areaId: Int = 0, val functionId: Int? = null)

    data class PropertyDef(
        val id: Int,
        val key: String,
        /** Product/OCA access: `r`, `w`, or `rw`. Write allowlist is derived from this. */
        val access: String = "r",
        val changeMode: String? = null,
        val areas: List<Int> = listOf(0),
        val entity: String? = null,
        val functionId: Int? = null,
    ) {
        val canWrite: Boolean get() = access == "w" || access == "rw"
    }

    data class AndroidSettingDef(
        val settingsKey: String,
        val entity: String,
        val access: String = "rw",
    )

    data class VolumeGroupDef(
        val groupId: Int,
        val entity: String,
        val settingsKey: String? = null,
        val access: String = "r",
        val writeVia: String? = null,
    ) {
        fun toApi(): AndroidVolumeGroup = AndroidVolumeGroup(
            groupId = groupId,
            entityId = entity,
            settingsKey = settingsKey ?: "android.car.VOLUME_GROUP/$groupId",
            access = access,
            writeVia = writeVia,
        )
    }

    data class AndroidConfig(
        val volumeGroups: List<VolumeGroupDef> = emptyList(),
        val settings: List<AndroidSettingDef> = emptyList(),
    )

    data class VariantDef(
        val id: String,
        val label: String,
        val extraCapabilities: Set<Capability>,
        val detect: String? = null,
    )

    /** Product entity id → native binding (derived from [properties] with `entity`). */
    val bindings: Map<String, Binding> by lazy {
        properties.mapNotNull { p ->
            val entity = p.entity ?: return@mapNotNull null
            entity to Binding(p.id, p.areas.firstOrNull() ?: 0, p.functionId)
        }.toMap()
    }

    /** Product write allowlist (derived from [properties] with `access` `w` / `rw`). */
    val writableAllowlist: Set<Int> by lazy {
        properties.filter { it.canWrite }.map { it.id }.toSet()
    }

    fun bindingFor(prop: VehicleProperty): Pair<Int, Int>? {
        val b = bindings[prop.key] ?: return null
        return b.nativeId to b.areaId
    }

    fun toPlatformVariant(id: String): PlatformVariant {
        val v = variants.firstOrNull { it.id == id } ?: return PlatformVariant(id, id)
        return PlatformVariant(v.id, v.label, v.extraCapabilities)
    }

    fun catalogEntries(): List<CatalogEntry> =
        properties.map { p ->
            CatalogEntry(
                property = VehicleProperty(
                    namespace = "vhal",
                    key = p.key,
                    nativeId = p.id.toLong() and 0xffff_ffffL,
                    defaultAreaId = p.areas.firstOrNull() ?: 0,
                ),
                name = p.key,
                writable = p.canWrite,
                areaIds = p.areas.ifEmpty { listOf(0) },
            )
        }

    fun androidVolumeGroups(): List<AndroidVolumeGroup> =
        android.volumeGroups.map { it.toApi() }

    /**
     * Bind available Camera2 ids to product roles from [cameras].
     * When [cameras] is empty, falls back to unlabeled enumeration (compat).
     * Role order for mosaic tiles: front, right, rear, left, then any extras.
     */
    fun resolveCameras(availableIds: Collection<String>): List<CameraSource> {
        if (cameras.isEmpty()) {
            return availableIds.mapIndexed { i, id ->
                CameraSource(
                    id = id,
                    label = "Camera $i ($id)",
                    cameraId = id,
                )
            }
        }
        val available = availableIds.toSet()
        val byRole = cameras.associateBy { it.role }
        val ordered = (CAMERA_ROLE_ORDER.mapNotNull { byRole[it] } +
            cameras.filter { it.role !in CAMERA_ROLE_ORDER_SET })
            .distinctBy { it.role }
        return ordered.filter { it.cameraId in available }.map { cfg ->
            val label = cfg.role.replaceFirstChar { c -> c.uppercase() }
            CameraSource(
                id = cfg.entityId,
                label = label,
                cameraId = cfg.cameraId,
                role = cfg.role,
            )
        }
    }

    companion object {
        val CAMERA_ROLE_ORDER = listOf("front", "right", "rear", "left")
        private val CAMERA_ROLE_ORDER_SET = CAMERA_ROLE_ORDER.toSet()

        fun load(context: Context, assetPath: String = "platform.json"): PlatformConfig {
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            return parse(text) { name ->
                context.assets.open("platform/$name.json").bufferedReader().use { it.readText() }
            }
        }

        /**
         * Parse a platform document. When [parentResolver] is set, resolves `"extends"` names
         * to JSON text (e.g. asset or filesystem) and merges parents left→right, then child.
         */
        fun parse(json: String, parentResolver: ((String) -> String)? = null): PlatformConfig {
            val root = JSONObject(json)
            val merged = if (parentResolver != null) {
                mergeExtends(root, parentResolver)
            } else {
                root
            }
            return fromMerged(merged)
        }

        fun mergeExtends(child: JSONObject, parentResolver: (String) -> String): JSONObject {
            val extends = child.optJSONArray("extends")?.toStringList() ?: emptyList()
            var acc = JSONObject()
            for (name in extends) {
                val parent = JSONObject(parentResolver(name))
                // Parents may themselves extend — resolve one level of nesting.
                val nested = parent.optJSONArray("extends")?.toStringList().orEmpty()
                if (nested.isNotEmpty()) {
                    acc = deepMerge(acc, mergeExtends(parent, parentResolver))
                } else {
                    acc = deepMerge(acc, parent)
                }
            }
            return deepMerge(acc, child)
        }

        internal fun deepMerge(base: JSONObject, overlay: JSONObject): JSONObject {
            val out = JSONObject()
            val keys = linkedSetOf<String>()
            base.keys().forEach { keys += it }
            overlay.keys().forEach { keys += it }
            for (key in keys) {
                if (key == "extends") continue
                val b = if (base.has(key)) base.get(key) else null
                val o = if (overlay.has(key)) overlay.get(key) else null
                when {
                    o == null -> out.put(key, b)
                    b == null -> out.put(key, o)
                    key == "properties" && b is JSONArray && o is JSONArray ->
                        out.put(key, mergeProperties(b, o))
                    key == "android" && b is JSONObject && o is JSONObject ->
                        out.put(key, mergeAndroid(b, o))
                    o is JSONObject && b is JSONObject ->
                        out.put(key, deepMerge(b, o))
                    o is JSONArray && b is JSONArray && key in META_ARRAY_REPLACE ->
                        out.put(key, o)
                    else -> out.put(key, o)
                }
            }
            return out
        }

        private val META_ARRAY_REPLACE = setOf(
            "match", "capabilities", "variants", "writableAllowlist",
        )

        private fun mergeProperties(base: JSONArray, overlay: JSONArray): JSONArray {
            val byId = linkedMapOf<Int, JSONObject>()
            fun ingest(arr: JSONArray) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = parseId(o.opt("id") ?: o.opt("nativeId"))
                    val prev = byId[id]
                    byId[id] = if (prev == null) JSONObject(o.toString()) else deepMerge(prev, o)
                }
            }
            ingest(base)
            ingest(overlay)
            val out = JSONArray()
            for (v in byId.values) out.put(v)
            return out
        }

        private fun mergeAndroid(base: JSONObject, overlay: JSONObject): JSONObject {
            val out = JSONObject()
            for (key in listOf("volumeGroups", "settings")) {
                val b = base.optJSONArray(key) ?: JSONArray()
                val o = overlay.optJSONArray(key) ?: JSONArray()
                if (b.length() == 0 && o.length() == 0) continue
                out.put(key, mergeAndroidList(b, o))
            }
            // Pass through any other android keys (overlay wins).
            val keys = linkedSetOf<String>()
            base.keys().forEach { keys += it }
            overlay.keys().forEach { keys += it }
            for (key in keys) {
                if (key == "volumeGroups" || key == "settings") continue
                if (overlay.has(key)) out.put(key, overlay.get(key))
                else if (base.has(key)) out.put(key, base.get(key))
            }
            return out
        }

        private fun mergeAndroidList(base: JSONArray, overlay: JSONArray): JSONArray {
            val byEntity = linkedMapOf<String, JSONObject>()
            fun ingest(arr: JSONArray) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val entity = o.optString("entity", null)
                        ?: o.optString("settingsKey", null)
                        ?: o.opt("groupId")?.toString()
                        ?: "idx-$i"
                    val prev = byEntity[entity]
                    byEntity[entity] = if (prev == null) JSONObject(o.toString()) else deepMerge(prev, o)
                }
            }
            ingest(base)
            ingest(overlay)
            val out = JSONArray()
            for (v in byEntity.values) out.put(v)
            return out
        }

        private fun fromMerged(root: JSONObject): PlatformConfig {
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

            val properties = mutableListOf<PropertyDef>()
            val pArr = root.optJSONArray("properties")
            if (pArr != null) {
                for (i in 0 until pArr.length()) {
                    properties += parseProperty(pArr.getJSONObject(i))
                }
            } else {
                // Legacy: bindings + writableAllowlist (migration / FALLBACK).
                properties += parseLegacyBindings(root)
            }

            val android = parseAndroid(root.optJSONObject("android"))

            val enumMap = mutableMapOf<Int, String>()
            val eObj = root.optJSONObject("driveModeEnum")
            if (eObj != null) {
                val keys = eObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    enumMap[k.toInt()] = eObj.getString(k)
                }
            }
            val cameras = mutableListOf<CameraRoleConfig>()
            val camArr = root.optJSONArray("cameras")
            if (camArr != null) {
                for (i in 0 until camArr.length()) {
                    val o = camArr.optJSONObject(i) ?: continue
                    val role = o.optString("role", "").trim()
                    val cameraId = o.optString("cameraId", "").trim()
                    if (role.isNotEmpty() && cameraId.isNotEmpty()) {
                        cameras += CameraRoleConfig(role = role, cameraId = cameraId)
                    }
                }
            }
            return PlatformConfig(
                id = root.getString("id"),
                displayName = root.optString("displayName", root.getString("id")),
                backend = root.optString("backend", "vhal"),
                match = match,
                capabilities = caps,
                variants = variants,
                properties = properties,
                android = android,
                driveModeEnum = enumMap,
                cameras = cameras,
            )
        }

        private fun parseProperty(o: JSONObject): PropertyDef {
            val id = parseId(o.opt("id") ?: o.opt("nativeId"))
            val areas = mutableListOf<Int>()
            val aArr = o.optJSONArray("areas")
            if (aArr != null) {
                for (i in 0 until aArr.length()) {
                    areas += parseId(aArr.get(i))
                }
            } else if (o.has("areaId")) {
                areas += o.optInt("areaId", 0)
            }
            if (areas.isEmpty()) areas += 0
            val access = normalizeAccess(o.opt("access"))
            val changeMode = when {
                o.has("changeMode") -> normalizeChangeMode(o.opt("changeMode"))
                else -> null
            }
            val fn = if (o.has("functionId")) parseId(o.get("functionId")) else null
            return PropertyDef(
                id = id,
                key = o.optString("key", o.optString("name", "0x${Integer.toHexString(id)}")),
                access = access,
                changeMode = changeMode,
                areas = areas,
                entity = o.optString("entity", null)?.takeIf { it.isNotBlank() },
                functionId = fn,
            )
        }

        private fun parseLegacyBindings(root: JSONObject): List<PropertyDef> {
            val allow = mutableSetOf<Int>()
            val aArr = root.optJSONArray("writableAllowlist")
            if (aArr != null) {
                for (i in 0 until aArr.length()) {
                    allow += parseId(aArr.get(i))
                }
            }
            val out = mutableListOf<PropertyDef>()
            val bObj = root.optJSONObject("bindings") ?: return out
            val keys = bObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val o = bObj.getJSONObject(key)
                val native = parseId(o.opt("nativeId") ?: o.opt("id"))
                val area = o.optInt("areaId", 0)
                val fn = if (o.has("functionId")) parseId(o.get("functionId")) else null
                out += PropertyDef(
                    id = native,
                    key = key,
                    access = if (native in allow) "rw" else "r",
                    areas = listOf(area),
                    entity = key,
                    functionId = fn,
                )
            }
            return out
        }

        private fun parseAndroid(obj: JSONObject?): AndroidConfig {
            if (obj == null) return AndroidConfig()
            val groups = mutableListOf<VolumeGroupDef>()
            val gArr = obj.optJSONArray("volumeGroups")
            if (gArr != null) {
                for (i in 0 until gArr.length()) {
                    val o = gArr.getJSONObject(i)
                    val groupId = o.getInt("groupId")
                    groups += VolumeGroupDef(
                        groupId = groupId,
                        entity = o.getString("entity"),
                        settingsKey = o.optString("settingsKey", null)
                            ?.takeIf { it.isNotBlank() }
                            ?: "android.car.VOLUME_GROUP/$groupId",
                        access = normalizeAccess(o.opt("access")),
                        writeVia = o.optString("writeVia", null)?.takeIf { it.isNotBlank() },
                    )
                }
            }
            val settings = mutableListOf<AndroidSettingDef>()
            val sArr = obj.optJSONArray("settings")
            if (sArr != null) {
                for (i in 0 until sArr.length()) {
                    val o = sArr.getJSONObject(i)
                    settings += AndroidSettingDef(
                        settingsKey = o.getString("settingsKey"),
                        entity = o.getString("entity"),
                        access = normalizeAccess(o.opt("access")),
                    )
                }
            }
            return AndroidConfig(volumeGroups = groups, settings = settings)
        }

        private fun normalizeAccess(raw: Any?): String {
            when (raw) {
                null -> return "r"
                is Number -> return when (raw.toInt()) {
                    1 -> "r"
                    2 -> "w"
                    3 -> "rw"
                    else -> "r"
                }
                is String -> {
                    val s = raw.trim().lowercase()
                    return when (s) {
                        "r", "read", "read_only" -> "r"
                        "w", "write", "write_only" -> "w"
                        "rw", "read_write", "read-write" -> "rw"
                        else -> "r"
                    }
                }
                else -> return "r"
            }
        }

        private fun normalizeChangeMode(raw: Any?): String? {
            when (raw) {
                null -> return null
                is Number -> return when (raw.toInt()) {
                    0 -> "static"
                    1 -> "on_change"
                    2 -> "continuous"
                    else -> raw.toString()
                }
                is String -> {
                    val s = raw.trim().lowercase().replace('-', '_')
                    return when (s) {
                        "static" -> "static"
                        "on_change", "onchange" -> "on_change"
                        "continuous" -> "continuous"
                        else -> s
                    }
                }
                else -> return raw.toString()
            }
        }

        fun parseId(raw: Any?): Int {
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

fun wellKnownByKey(key: String): VehicleProperty? {
    if (key.isBlank()) return null
    // Any platform.json binding key is a valid logical property.
    return EntityRegistry.property(key)
}
