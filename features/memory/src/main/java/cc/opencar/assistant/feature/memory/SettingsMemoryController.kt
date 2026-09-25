package cc.opencar.assistant.feature.memory

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import cc.opencar.assistant.api.Capability
import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.api.VehicleEvent
import cc.opencar.assistant.api.VehicleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Non-VHAL settings (Wi‑Fi / BT / cabin volume) applied outside [EntityRegistry]. */
interface ExternalSettingsApplier {
    suspend fun apply(id: String, value: String): Boolean
    suspend fun read(id: String): String?
}

/**
 * Per-control boot / gear persistence (pin). Values are reapplied through
 * [applyControl] (shared with shortcuts / HTTP) so climate composites and
 * atomics use one write path.
 */
class SettingsMemoryController(
    context: Context,
    private val session: VehicleSession,
    private val hasWrite: Boolean,
    /** Product write path shared with shortcuts / HTTP. */
    private val applyControl: suspend (id: String, raw: String) -> Result<Unit>,
    /** Live value for capture / snapshot. */
    private val readControl: suspend (id: String) -> String?,
    /** Extra pin ids not in [EntityRegistry] (android_*, cabin_vol_*, …). */
    extraPinIds: Collection<String> = emptyList(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = memoryStore(context)
    @Volatile private var lastReapplyMs: Long = 0L

    /** Writable registry entities + external Android / cabin ids. */
    private val pinIds: Set<String> =
        EntityRegistry.ALL.filter { it.writable }.map { it.id }.toSet() + extraPinIds

    fun start() {
        if (!hasWrite) return
        scope.launch {
            migrateLegacyClimatePins()
            reapply()
            session.events().collect { event ->
                when (event) {
                    is VehicleEvent.Boot -> reapply()
                    is VehicleEvent.GearChanged -> reapply()
                    else -> Unit
                }
            }
        }
    }

    /** Fold old atomic hvac_* pins into climate when climate is not yet pinned. */
    private suspend fun migrateLegacyClimatePins() {
        val prefs = store.data.first()
        if (prefs[booleanPreferencesKey(pinKey("climate"))] == true) return
        val powerPinned = prefs[booleanPreferencesKey(pinKey("hvac_power"))] == true
        val tempPinned = prefs[booleanPreferencesKey(pinKey("hvac_temp"))] == true
        if (!powerPinned && !tempPinned) return
        val mode = when {
            powerPinned -> {
                val raw = prefs[stringPreferencesKey(valueKey("hvac_power"))]
                if (raw == "0" || raw.equals("false", true) || raw.equals("off", true)) "off" else "on"
            }
            else -> null
        }
        val temp = if (tempPinned) prefs[stringPreferencesKey(valueKey("hvac_temp"))] else null
        store.edit { e ->
            e[booleanPreferencesKey(pinKey("climate"))] = true
            when {
                mode != null && temp != null ->
                    e[stringPreferencesKey(valueKey("climate"))] = "hvac_mode:$mode;temperature:$temp"
                mode != null -> e[stringPreferencesKey(valueKey("climate"))] = mode
                temp != null -> e[stringPreferencesKey(valueKey("climate"))] = "temperature:$temp"
            }
            for (legacy in LEGACY_CLIMATE_IDS) {
                e.remove(booleanPreferencesKey(pinKey(legacy)))
                e.remove(stringPreferencesKey(valueKey(legacy)))
            }
        }
    }

    suspend fun isPinned(id: String): Boolean {
        val prefs = store.data.first()
        return prefs[booleanPreferencesKey(pinKey(id))] == true
    }

    suspend fun pinnedValue(id: String): String? {
        val prefs = store.data.first()
        return prefs[stringPreferencesKey(valueKey(id))]
    }

    suspend fun persistSnapshot(): Map<String, Map<String, Any?>> {
        val prefs = store.data.first()
        val out = mutableMapOf<String, Map<String, Any?>>()
        for (id in pinIds) {
            val pinned = prefs[booleanPreferencesKey(pinKey(id))] == true
            val value = prefs[stringPreferencesKey(valueKey(id))]
            if (pinned || value != null) {
                out[id] = mapOf("enabled" to pinned, "value" to value)
            }
        }
        return out
    }

    suspend fun setPersist(id: String, enabled: Boolean?, value: String?): Map<String, Any?> {
        if (id !in pinIds) {
            return mapOf("ok" to false, "error" to "unknown control")
        }
        store.edit { e ->
            if (enabled != null) {
                e[booleanPreferencesKey(pinKey(id))] = enabled
                if (!enabled) {
                    e.remove(stringPreferencesKey(valueKey(id)))
                }
            }
            if (value != null) {
                e[stringPreferencesKey(valueKey(id))] = value
            }
        }
        return mapOf(
            "ok" to true,
            "id" to id,
            "enabled" to isPinned(id),
            "value" to pinnedValue(id),
        )
    }

    /** Bulk: pin all tracked with current live values. */
    suspend fun captureFromVehicle(): Map<String, String?> {
        val captured = mutableMapOf<String, String?>()
        store.edit { e ->
            for (id in pinIds) {
                val live = readControl(id) ?: continue
                e[booleanPreferencesKey(pinKey(id))] = true
                e[stringPreferencesKey(valueKey(id))] = live
                captured[id] = live
            }
        }
        return captured
    }

    /**
     * Debounced reapply for wake storms (SCREEN_ON + USER_PRESENT + display + poll).
     * Boot / gear paths still call [reapply] directly.
     */
    suspend fun reapplyOnWake() {
        val now = System.currentTimeMillis()
        if (now - lastReapplyMs < WAKE_REAPPLY_DEBOUNCE_MS) return
        reapply()
    }

    suspend fun reapply() {
        if (!hasWrite) return
        lastReapplyMs = System.currentTimeMillis()
        val prefs = store.data.first()
        for (id in pinIds) {
            if (prefs[booleanPreferencesKey(pinKey(id))] != true) continue
            val raw = prefs[stringPreferencesKey(valueKey(id))] ?: continue
            // Multi-token climate pins: "hvac_mode:on;temperature:22"
            if (id == "climate" && raw.contains(';')) {
                for (token in raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                    applyControl(id, token)
                }
            } else {
                applyControl(id, raw)
            }
        }
    }

    private fun pinKey(id: String) = "pin_$id"
    private fun valueKey(id: String) = "val_$id"

    companion object {
        fun requiredCapability(): Capability = Capability.WRITE_SETTINGS
        /** Match shortcut screen debounce so wake storms do not reapply repeatedly. */
        const val WAKE_REAPPLY_DEBOUNCE_MS = 5_000L

        private val LEGACY_CLIMATE_IDS = listOf(
            "hvac_power", "hvac_ac", "hvac_auto", "hvac_recirc",
            "hvac_max_defrost", "hvac_max_ac", "hvac_eco", "hvac_temp",
            "hvac_fan", "hvac_fan_direction", "hvac_auto_dry",
            "hvac_rapid_cool", "hvac_rapid_heat",
        )

        @Volatile
        private var storeInstance: DataStore<Preferences>? = null

        private fun memoryStore(context: Context) =
            storeInstance ?: synchronized(this) {
                storeInstance ?: PreferenceDataStoreFactory.create {
                    context.applicationContext.preferencesDataStoreFile("oca_settings_memory")
                }.also { storeInstance = it }
            }
    }
}
