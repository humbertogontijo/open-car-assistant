package cc.opencar.assistant.feature.shortcuts

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Process-wide singleton — DataStore forbids two instances on the same file.
 * Boot races (OcaApp + BootReceiver + FGS) used to create two [ShortcutsController]s.
 */
class ShortcutStore private constructor(context: Context) {
    private val store = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("oca_shortcuts")
    }

    val shortcuts: Flow<List<Shortcut>> = store.data.map { prefs ->
        parseList(prefs[KEY_LIST])
    }

    /** Fixed pin slots 0..7 → shortcut id (or absent). */
    val slots: Flow<Map<Int, String>> = store.data.map { prefs ->
        parseSlots(prefs[KEY_SLOTS])
    }

    val overlayEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_OVERLAY] != false
    }

    suspend fun list(): List<Shortcut> = shortcuts.first()

    suspend fun get(id: String): Shortcut? = list().firstOrNull { it.id == id }

    suspend fun slotMap(): Map<Int, String> = slots.first()

    suspend fun isOverlayEnabled(): Boolean = overlayEnabled.first()

    suspend fun setOverlayEnabled(enabled: Boolean) {
        store.edit { it[KEY_OVERLAY] = enabled }
    }

    suspend fun setSlot(slot: Int, shortcutId: String?) {
        val s = slot.coerceIn(0, 7)
        store.edit { prefs ->
            val map = parseSlots(prefs[KEY_SLOTS]).toMutableMap()
            if (shortcutId.isNullOrBlank()) {
                map.remove(s)
            } else {
                // One shortcut per slot; also clear other slots pointing at same id
                val keys = map.filterValues { it == shortcutId }.keys.toList()
                keys.forEach { map.remove(it) }
                map[s] = shortcutId
            }
            prefs[KEY_SLOTS] = serializeSlots(map)
        }
    }

    suspend fun upsert(shortcut: Shortcut): Shortcut {
        val normalized = normalize(shortcut)
        store.edit { prefs ->
            val current = parseList(prefs[KEY_LIST]).toMutableList()
            val idx = current.indexOfFirst { it.id == normalized.id }
            if (idx >= 0) current[idx] = normalized else current.add(normalized)
            prefs[KEY_LIST] = serialize(current)
        }
        return get(normalized.id) ?: normalized
    }

    suspend fun delete(id: String): Boolean {
        var removed = false
        store.edit { prefs ->
            val current = parseList(prefs[KEY_LIST]).toMutableList()
            removed = current.removeAll { it.id == id }
            prefs[KEY_LIST] = serialize(current)
            val map = parseSlots(prefs[KEY_SLOTS]).toMutableMap()
            val cleared = map.filterValues { it != id }
            prefs[KEY_SLOTS] = serializeSlots(cleared)
        }
        return removed
    }

    /** Cap action list length on save. */
    private fun normalize(s: Shortcut): Shortcut =
        s.copy(actions = s.actions.take(ShortcutAction.MAX_ACTIONS))

    companion object {
        private val KEY_LIST = stringPreferencesKey("shortcuts_json")
        private val KEY_SLOTS = stringPreferencesKey("pin_slots_json")
        private val KEY_OVERLAY = booleanPreferencesKey("overlay_enabled")

        @Volatile
        private var instance: ShortcutStore? = null

        fun get(context: Context): ShortcutStore {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: ShortcutStore(context.applicationContext).also { instance = it }
            }
        }

        fun parseSlots(raw: String?): Map<Int, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return try {
                val o = JSONObject(raw)
                buildMap {
                    val keys = o.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val slot = k.toIntOrNull() ?: continue
                        val id = o.optString(k, null) ?: continue
                        if (id.isNotBlank() && slot in 0..7) put(slot, id)
                    }
                }
            } catch (_: Throwable) {
                emptyMap()
            }
        }

        fun serializeSlots(map: Map<Int, String>): String {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k.toString(), v) }
            return o.toString()
        }

        fun parseList(raw: String?): List<Shortcut> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        fromJson(obj)?.let { add(it) }
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        fun serialize(list: List<Shortcut>): String {
            val arr = JSONArray()
            for (s in list) arr.put(toJson(s))
            return arr.toString()
        }

        private fun toJson(s: Shortcut): JSONObject {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("icon", s.icon)
            o.put("enabled", s.enabled)
            val actions = JSONArray()
            for (a in s.actions) {
                actions.put(mapToJson(a.toMap()))
            }
            o.put("actions", actions)
            val triggers = JSONArray()
            for (t in s.triggers) {
                triggers.put(mapToJson(t.toMap()))
            }
            o.put("triggers", triggers)
            return o
        }

        private fun fromJson(o: JSONObject): Shortcut? {
            val map = mutableMapOf<String, Any?>()
            map["id"] = o.optString("id", null) ?: return null
            map["name"] = o.optString("name", null) ?: return null
            map["icon"] = o.optString("icon", "drive")
            map["enabled"] = o.optBoolean("enabled", true)
            map["actions"] = jsonArrayToMaps(o.optJSONArray("actions"))
            map["triggers"] = jsonArrayToMaps(o.optJSONArray("triggers"))
            return Shortcut.fromMap(map)
        }

        private fun jsonArrayToMaps(arr: JSONArray?): List<Map<String, Any?>> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(jsonObjectToMap(obj))
                }
            }
        }

        /** Nested JSONObject/JSONArray → Map/List so plugin `params` round-trip. */
        private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val m = mutableMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                m[k] = jsonToAny(obj.opt(k))
            }
            return m
        }

        private fun jsonToAny(v: Any?): Any? = when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> jsonObjectToMap(v)
            is JSONArray -> (0 until v.length()).map { jsonToAny(v.opt(it)) }
            else -> v
        }

        private fun mapToJson(map: Map<String, Any?>): JSONObject {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k, anyToJson(v)) }
            return o
        }

        private fun anyToJson(v: Any?): Any = when (v) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().also { o ->
                v.forEach { (k, vv) ->
                    if (k != null) o.put(k.toString(), anyToJson(vv))
                }
            }
            is List<*> -> JSONArray().also { a ->
                v.forEach { a.put(anyToJson(it)) }
            }
            is Boolean, is Number, is String -> v
            else -> v.toString()
        }
    }
}
