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
import cc.opencar.assistant.api.PropertyValue
import cc.opencar.assistant.api.VehicleEvent
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.WellKnownProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Non-VHAL settings (Wi‑Fi / BT) applied through [ExternalSettingsApplier]. */
interface ExternalSettingsApplier {
    suspend fun apply(id: String, value: String): Boolean
    suspend fun read(id: String): String?
}

/**
 * Per-control boot / gear persistence (pin). Values are written to the vehicle on
 * Boot, session-ready, and gear changes.
 */
class SettingsMemoryController(
    context: Context,
    private val session: VehicleSession,
    private val hasWrite: Boolean,
    private val external: ExternalSettingsApplier? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = memoryStore(context)
    @Volatile private var lastReapplyMs: Long = 0L

    /** Control id → VHAL target, or null for external (android_*) ids. */
    private val catalog: Map<String, Pair<VehicleProperty, Boolean>?> = mapOf(
        "drive_mode" to (WellKnownProperties.DRIVE_MODE to false),
        "regen" to (WellKnownProperties.REGEN to false),
        "esc_sport" to (WellKnownProperties.ESC_SPORT to false),
        "auto_hold" to (WellKnownProperties.AUTO_HOLD to false),
        "hdc" to (WellKnownProperties.HDC to false),
        "cst" to (WellKnownProperties.CST to false),
        "steer_soft" to (WellKnownProperties.STEER_SOFT to false),
        "steer_medium" to (WellKnownProperties.STEER_MEDIUM to false),
        "steer_heavy" to (WellKnownProperties.STEER_HEAVY to false),
        "brake_pedal" to (WellKnownProperties.BRAKE_PEDAL_MODE to false),
        "hvac_power" to (WellKnownProperties.HVAC_POWER to false),
        "hvac_ac" to (WellKnownProperties.HVAC_AC to false),
        "hvac_auto" to (WellKnownProperties.HVAC_AUTO to false),
        "hvac_recirc" to (WellKnownProperties.HVAC_RECIRC to false),
        "hvac_max_defrost" to (WellKnownProperties.HVAC_MAX_DEFROST to false),
        "hvac_max_ac" to (WellKnownProperties.HVAC_MAX_AC to false),
        "hvac_eco" to (WellKnownProperties.HVAC_ECO to false),
        "hvac_temp" to (WellKnownProperties.HVAC_TEMP_C to true),
        "hvac_fan" to (WellKnownProperties.HVAC_FAN to false),
        "hvac_fan_direction" to (WellKnownProperties.HVAC_FAN_DIRECTION to false),
        "hvac_seat_vent" to (WellKnownProperties.HVAC_SEAT_VENT to false),
        "battery_hold" to (WellKnownProperties.BATTERY_HOLD to false),
        "battery_save" to (WellKnownProperties.BATTERY_SAVE to false),
        "battery_mode" to (WellKnownProperties.BATTERY_MODE to false),
        "charge_current" to (WellKnownProperties.CHARGE_CURRENT to true),
        "charge_limit" to (WellKnownProperties.CHARGE_LIMIT to false),
        "charge_switch" to (WellKnownProperties.CHARGE_SWITCH to false),
        "charge_soc_max" to (WellKnownProperties.CHARGE_SOC_MAX to true),
        "charge_soc_min" to (WellKnownProperties.CHARGE_SOC_MIN to true),
        "charge_discharge_soc" to (WellKnownProperties.CHARGE_DISCHARGE_SOC to true),
        "charge_v2l" to (WellKnownProperties.CHARGE_V2L to false),
        "charge_v2v" to (WellKnownProperties.CHARGE_V2V to false),
        "charge_parking" to (WellKnownProperties.CHARGE_PARKING to false),
        "parking_comfort" to (WellKnownProperties.PARKING_COMFORT to false),
        "lka" to (WellKnownProperties.LANE_KEEPING to false),
        "elka" to (WellKnownProperties.ELKA to false),
        "aeb" to (WellKnownProperties.AEB to false),
        "fcw" to (WellKnownProperties.FCW to false),
        "rcta" to (WellKnownProperties.RCTA to false),
        "rcw" to (WellKnownProperties.RCW to false),
        "speed_limit_warn" to (WellKnownProperties.SPEED_LIMIT_WARN to false),
        "lane_change_warn" to (WellKnownProperties.LANE_CHANGE_WARN to false),
        "dms" to (WellKnownProperties.DMS to false),
        "approach_unlock" to (WellKnownProperties.APPROACH_UNLOCK to false),
        "away_lock" to (WellKnownProperties.AWAY_LOCK to false),
        "central_lock" to (WellKnownProperties.CENTRAL_LOCK to false),
        "audible_lock" to (WellKnownProperties.AUDIBLE_LOCK to false),
        "auto_close_window" to (WellKnownProperties.AUTO_CLOSE_WINDOW to false),
        "sunroof_tilt" to (WellKnownProperties.SUNROOF_TILT to false),
        "courtesy_light" to (WellKnownProperties.COURTESY_LIGHT to false),
        "approach_light" to (WellKnownProperties.APPROACH_LIGHT to false),
        "day_mode" to (WellKnownProperties.DAY_MODE to false),
        "night_mode" to (WellKnownProperties.NIGHT_MODE to false),
        "ambience_main_color" to (WellKnownProperties.AMBIENCE_MAIN_COLOR to false),
        "ambience_intensity" to (WellKnownProperties.AMBIENCE_INTENSITY to false),
        "esm_volume" to (WellKnownProperties.ESM_VOLUME to false),
        "esm_sound" to (WellKnownProperties.ESM_SOUND to false),
        "usb_mode" to (WellKnownProperties.USB_MODE to false),
        "hud_active" to (WellKnownProperties.HUD_ACTIVE to false),
        "hud_snow" to (WellKnownProperties.HUD_SNOW to false),
        "hud_ar" to (WellKnownProperties.HUD_AR to false),
        "wheel_custom_key" to (WellKnownProperties.WHEEL_CUSTOM_KEY to false),
        // Android (non-VHAL) — applied via ExternalSettingsApplier
        "android_wifi" to null,
        "android_bluetooth" to null,
    )

    fun start() {
        if (!hasWrite) return
        scope.launch {
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
        for (id in catalog.keys) {
            val pinned = prefs[booleanPreferencesKey(pinKey(id))] == true
            val value = prefs[stringPreferencesKey(valueKey(id))]
            if (pinned || value != null) {
                out[id] = mapOf("enabled" to pinned, "value" to value)
            }
        }
        return out
    }

    suspend fun setPersist(id: String, enabled: Boolean?, value: String?): Map<String, Any?> {
        if (!catalog.containsKey(id)) {
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
            for (id in catalog.keys) {
                val live = readLive(id) ?: continue
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
        for ((id, meta) in catalog) {
            if (prefs[booleanPreferencesKey(pinKey(id))] != true) continue
            val raw = prefs[stringPreferencesKey(valueKey(id))] ?: continue
            if (meta == null) {
                external?.apply(id, raw)
                continue
            }
            val (prop, isFloat) = meta
            if (id == "drive_mode") {
                applyDriveMode(raw)
                continue
            }
            if (isFloat) {
                raw.toFloatOrNull()?.let { session.set(prop, PropertyValue.FloatVal(it)) }
            } else {
                val i = when {
                    raw.equals("true", true) || raw == "on" -> 1
                    raw.equals("false", true) || raw == "off" -> 0
                    else -> raw.toIntOrNull()
                }
                if (i != null) session.set(prop, PropertyValue.IntVal(i))
            }
        }
    }

    private suspend fun applyDriveMode(raw: String) {
        val mode = raw.toIntOrNull() ?: return
        session.set(WellKnownProperties.DRIVE_MODE, PropertyValue.IntVal(mode))
    }

    private suspend fun readLive(id: String): String? {
        val meta = catalog[id]
        if (meta == null) {
            return external?.read(id)
        }
        val (prop, isFloat) = meta
        if (id == "drive_mode") {
            return session.get(WellKnownProperties.DRIVE_MODE)?.asInt()?.toString()
        }
        val v = session.get(prop) ?: return null
        return if (isFloat) v.asFloat()?.toString() else v.asInt()?.toString()
            ?: v.display()
    }

    private fun pinKey(id: String) = "pin_$id"
    private fun valueKey(id: String) = "val_$id"

    companion object {
        fun requiredCapability(): Capability = Capability.WRITE_SETTINGS
        /** Match shortcut screen debounce so wake storms do not reapply repeatedly. */
        const val WAKE_REAPPLY_DEBOUNCE_MS = 5_000L

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
