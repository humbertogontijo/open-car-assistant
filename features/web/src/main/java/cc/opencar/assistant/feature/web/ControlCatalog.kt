package cc.opencar.assistant.feature.web

import android.content.Context
import cc.opencar.assistant.api.DeviceClass
import cc.opencar.assistant.api.EntityType
import cc.opencar.assistant.api.PropertyValue
import cc.opencar.assistant.api.ReadOutcome
import cc.opencar.assistant.api.UnitOfMeasurement
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.WellKnownProperties
import cc.opencar.assistant.feature.memory.SettingsMemoryController
import cc.opencar.assistant.support.I18nBundle
import cc.opencar.assistant.support.LastKnownStore
import kotlinx.coroutines.flow.first

/**
 * Curated product controls. [input] drives typed UI; [icon] maps to SVG sprite keys.
 * [deviceClass] / [unitOfMeasurement] mirror HA semantics; display uses localized [unitLabel].
 */
object ControlCatalog {
    data class ControlDef(
        val id: String,
        val group: String,
        val entity: EntityType,
        val property: VehicleProperty,
        /** bool | choice | int | float | text | sensor */
        val input: String,
        val optionKeys: List<Pair<String, Int>>? = null,
        val writable: Boolean = true,
        val deviceClass: DeviceClass? = null,
        val unitOfMeasurement: UnitOfMeasurement? = null,
        val acronym: String? = null,
        val lastKnown: Boolean = false,
        val valueMapId: String? = null,
        val labelKey: String? = null,
        val hintKey: String? = null,
        val icon: String? = null,
        val min: Float? = null,
        val max: Float? = null,
        val step: Float? = null,
        /** Opt-in for local HA-style entity history recording. */
        val history: Boolean = false,
    ) {
        fun resolvedLabelKey(): String = labelKey ?: "control.$id"
        fun resolvedHintKey(): String = hintKey ?: "control.$id.hint"
        fun resolvedValueMapId(): String = valueMapId ?: id
        fun resolvedIcon(): String = icon ?: id
    }

    val ALL: List<ControlDef> = listOf(
        ControlDef(
            "drive_mode", "drive", EntityType.DRIVE_MODE,
            WellKnownProperties.DRIVE_MODE, "choice",
            optionKeys = listOf(
                "opt.drive_mode.1" to 5,
                "opt.drive_mode.2" to 6,
                "opt.drive_mode.3" to 7,
            ),
            lastKnown = true, icon = "drive_mode", history = true,
        ),
        ControlDef(
            "regen", "drive", EntityType.REGEN,
            WellKnownProperties.REGEN, "choice",
            optionKeys = listOf(
                "opt.regen.1" to 1,
                "opt.regen.2" to 2,
                "opt.regen.3" to 3,
                "opt.regen.4" to 4,
            ),
            lastKnown = true, icon = "regen",
        ),
        ControlDef("esc_sport", "drive", EntityType.EXTRA, WellKnownProperties.ESC_SPORT, "bool", acronym = "ESC", icon = "drive"),
        ControlDef("auto_hold", "drive", EntityType.BRAKE, WellKnownProperties.AUTO_HOLD, "bool", icon = "brake"),
        ControlDef("hdc", "drive", EntityType.BRAKE, WellKnownProperties.HDC, "bool", acronym = "HDC", icon = "brake"),
        ControlDef("cst", "drive", EntityType.EXTRA, WellKnownProperties.CST, "bool", acronym = "CST", lastKnown = true, icon = "drive"),
        ControlDef("steer_soft", "drive", EntityType.STEERING, WellKnownProperties.STEER_SOFT, "bool", lastKnown = true, icon = "steer"),
        ControlDef("steer_medium", "drive", EntityType.STEERING, WellKnownProperties.STEER_MEDIUM, "bool", lastKnown = true, icon = "steer"),
        ControlDef("steer_heavy", "drive", EntityType.STEERING, WellKnownProperties.STEER_HEAVY, "bool", lastKnown = true, icon = "steer"),
        ControlDef("intelligent_steer", "drive", EntityType.STEERING, WellKnownProperties.INTELLIGENT_STEER, "bool", lastKnown = true, icon = "steer"),
        ControlDef(
            "brake_pedal", "drive", EntityType.BRAKE,
            WellKnownProperties.BRAKE_PEDAL_MODE, "choice",
            optionKeys = listOf(
                "opt.brake_pedal.0" to 0,
                "opt.brake_pedal.1" to 1,
                "opt.brake_pedal.2" to 2,
            ),
            icon = "brake",
        ),
        ControlDef("hvac_power", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_POWER, "bool", icon = "climate", history = true),
        ControlDef("hvac_ac", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_AC, "bool", icon = "climate"),
        ControlDef("hvac_auto", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_AUTO, "bool", icon = "climate"),
        ControlDef("hvac_recirc", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_RECIRC, "bool", icon = "climate"),
        ControlDef("hvac_max_defrost", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_MAX_DEFROST, "bool", icon = "climate"),
        ControlDef("hvac_max_ac", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_MAX_AC, "bool", icon = "climate"),
        ControlDef("hvac_eco", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_ECO, "bool", lastKnown = true, icon = "climate"),
        ControlDef("hvac_auto_dry", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_AUTO_DRY, "bool", lastKnown = true, icon = "climate"),
        ControlDef("hvac_rapid_cool", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_RAPID_COOL, "bool", icon = "climate"),
        ControlDef("hvac_rapid_heat", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_RAPID_HEAT, "bool", icon = "climate"),
        ControlDef(
            "hvac_temp", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_TEMP_C, "float",
            deviceClass = DeviceClass.TEMPERATURE,
            unitOfMeasurement = UnitOfMeasurement.CELSIUS,
            lastKnown = true, icon = "temp", min = 16f, max = 32f, step = 0.5f, history = true,
        ),
        ControlDef(
            "hvac_fan", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_FAN, "choice",
            optionKeys = (0..8).map { "opt.hvac_fan.$it" to it },
            icon = "fan",
        ),
        ControlDef(
            "hvac_fan_direction", "climate", EntityType.CLIMATE, WellKnownProperties.HVAC_FAN_DIRECTION, "choice",
            optionKeys = listOf(
                "opt.hvac_fan_direction.0" to 0,
                "opt.hvac_fan_direction.1" to 1,
                "opt.hvac_fan_direction.2" to 2,
                "opt.hvac_fan_direction.3" to 3,
                "opt.hvac_fan_direction.4" to 4,
            ),
            icon = "fan",
        ),
        ControlDef(
            "hvac_seat_vent", "climate", EntityType.SEAT, WellKnownProperties.HVAC_SEAT_VENT, "int",
            icon = "seat", min = 0f, max = 3f, step = 1f,
        ),
        ControlDef("battery_hold", "energy", EntityType.ENERGY, WellKnownProperties.BATTERY_HOLD, "bool", lastKnown = true, icon = "battery"),
        ControlDef("battery_save", "energy", EntityType.ENERGY, WellKnownProperties.BATTERY_SAVE, "bool", lastKnown = true, icon = "battery"),
        ControlDef(
            "battery_mode", "energy", EntityType.ENERGY, WellKnownProperties.BATTERY_MODE, "choice",
            optionKeys = listOf(
                "opt.battery_mode.0" to 0,
                "opt.battery_mode.1" to 1,
                "opt.battery_mode.2" to 2,
            ),
            icon = "battery",
        ),
        ControlDef(
            "charge_current", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_CURRENT, "float",
            deviceClass = DeviceClass.CURRENT,
            unitOfMeasurement = UnitOfMeasurement.AMPERE,
            icon = "charge", min = 0f, max = 32f, step = 1f, history = true,
        ),
        ControlDef(
            "charge_limit", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_LIMIT, "int",
            deviceClass = DeviceClass.CURRENT,
            unitOfMeasurement = UnitOfMeasurement.AMPERE,
            icon = "charge", min = 5f, max = 32f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "charge_switch", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_SWITCH, "choice",
            optionKeys = listOf(
                "opt.charge_switch.609" to 609,
                "opt.charge_switch.610" to 610,
                "opt.charge_switch.611" to 611,
            ),
            icon = "charge",
        ),
        ControlDef("charge_pre_now", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_PRE_NOW, "bool", icon = "charge"),
        ControlDef(
            "charge_soc_max", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_SOC_MAX, "float",
            deviceClass = DeviceClass.BATTERY,
            unitOfMeasurement = UnitOfMeasurement.PERCENT,
            icon = "charge", min = 50f, max = 100f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "charge_soc_min", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_SOC_MIN, "float",
            deviceClass = DeviceClass.BATTERY,
            unitOfMeasurement = UnitOfMeasurement.PERCENT,
            icon = "charge", min = 0f, max = 50f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "charge_discharge_soc", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_DISCHARGE_SOC, "float",
            deviceClass = DeviceClass.BATTERY,
            unitOfMeasurement = UnitOfMeasurement.PERCENT,
            icon = "charge", min = 0f, max = 100f, step = 1f, lastKnown = true,
        ),
        ControlDef("charge_v2l", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_V2L, "bool", acronym = "V2L", icon = "charge"),
        ControlDef("charge_v2v", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_V2V, "bool", acronym = "V2V", icon = "charge"),
        ControlDef("charge_parking", "energy", EntityType.CHARGING, WellKnownProperties.CHARGE_PARKING, "bool", icon = "charge"),
        ControlDef("parking_comfort", "cabin", EntityType.CLIMATE, WellKnownProperties.PARKING_COMFORT, "bool", lastKnown = true, icon = "climate"),
        ControlDef("nap_mode", "cabin", EntityType.CLIMATE, WellKnownProperties.NAP_MODE, "bool", lastKnown = true, icon = "climate"),
        ControlDef("space_capsule", "cabin", EntityType.CLIMATE, WellKnownProperties.SPACE_CAPSULE, "bool", lastKnown = true, icon = "climate"),
        ControlDef("lka", "safety", EntityType.ADAS, WellKnownProperties.LANE_KEEPING, "bool", acronym = "LKA", lastKnown = true, icon = "adas"),
        ControlDef("ldw", "safety", EntityType.ADAS, WellKnownProperties.LDW, "bool", acronym = "LDW", lastKnown = true, icon = "adas"),
        ControlDef("elka", "safety", EntityType.ADAS, WellKnownProperties.ELKA, "bool", acronym = "ELKA", lastKnown = true, icon = "adas"),
        ControlDef("aeb", "safety", EntityType.ADAS, WellKnownProperties.AEB, "bool", acronym = "AEB", lastKnown = true, icon = "adas"),
        ControlDef("fcw", "safety", EntityType.ADAS, WellKnownProperties.FCW, "int", acronym = "FCW", icon = "adas", min = 0f, max = 3f, step = 1f),
        ControlDef("rcta", "safety", EntityType.ADAS, WellKnownProperties.RCTA, "bool", acronym = "RCTA", lastKnown = true, icon = "adas"),
        ControlDef("rcw", "safety", EntityType.ADAS, WellKnownProperties.RCW, "bool", acronym = "RCW", lastKnown = true, icon = "adas"),
        ControlDef("fcda", "safety", EntityType.ADAS, WellKnownProperties.FCDA, "bool", acronym = "FCDA", lastKnown = true, icon = "adas"),
        ControlDef("dow", "safety", EntityType.ADAS, WellKnownProperties.DOW, "bool", acronym = "DOW", lastKnown = true, icon = "adas"),
        ControlDef(
            "idas_mode", "safety", EntityType.ADAS, WellKnownProperties.IDAS_MODE, "int",
            acronym = "IDAS", icon = "adas", min = 0f, max = 5f, step = 1f, lastKnown = true,
        ),
        ControlDef("speed_limit_warn", "safety", EntityType.ADAS, WellKnownProperties.SPEED_LIMIT_WARN, "bool", lastKnown = true, icon = "adas"),
        ControlDef(
            "speed_limit_max", "safety", EntityType.ADAS, WellKnownProperties.SPEED_LIMIT_MAX, "sensor",
            // AAOS access=READ; gRPC SetProperty ACKs but car_service value never moves.
            writable = false,
            deviceClass = DeviceClass.SPEED,
            unitOfMeasurement = UnitOfMeasurement.KM_PER_HOUR,
            icon = "adas", lastKnown = true,
        ),
        ControlDef("lane_change_warn", "safety", EntityType.ADAS, WellKnownProperties.LANE_CHANGE_WARN, "bool", lastKnown = true, icon = "adas"),
        ControlDef("dms", "safety", EntityType.ADAS, WellKnownProperties.DMS, "bool", acronym = "DMS", lastKnown = true, icon = "adas"),
        ControlDef("approach_unlock", "cabin", EntityType.LOCK, WellKnownProperties.APPROACH_UNLOCK, "bool", lastKnown = true, icon = "lock"),
        ControlDef("away_lock", "cabin", EntityType.LOCK, WellKnownProperties.AWAY_LOCK, "bool", lastKnown = true, icon = "lock"),
        ControlDef("central_lock", "cabin", EntityType.LOCK, WellKnownProperties.CENTRAL_LOCK, "bool", icon = "lock"),
        ControlDef("audible_lock", "cabin", EntityType.LOCK, WellKnownProperties.AUDIBLE_LOCK, "bool", icon = "lock"),
        ControlDef("keyless_unlock", "cabin", EntityType.LOCK, WellKnownProperties.KEYLESS_UNLOCK, "bool", lastKnown = true, icon = "lock"),
        ControlDef("twostep_unlock", "cabin", EntityType.LOCK, WellKnownProperties.TWOSTEP_UNLOCK, "bool", lastKnown = true, icon = "lock"),
        ControlDef("p_gear_unlock", "cabin", EntityType.LOCK, WellKnownProperties.P_GEAR_UNLOCK, "bool", lastKnown = true, icon = "lock"),
        ControlDef("mirror_auto_fold", "cabin", EntityType.EXTRA, WellKnownProperties.MIRROR_AUTO_FOLD, "bool", lastKnown = true, icon = "cabin"),
        ControlDef("auto_close_window", "cabin", EntityType.WINDOW, WellKnownProperties.AUTO_CLOSE_WINDOW, "bool", icon = "window"),
        ControlDef("sunroof_tilt", "cabin", EntityType.WINDOW, WellKnownProperties.SUNROOF_TILT, "bool", icon = "window"),
        ControlDef("courtesy_light", "cabin", EntityType.LIGHT, WellKnownProperties.COURTESY_LIGHT, "bool", icon = "light"),
        ControlDef("approach_light", "cabin", EntityType.LIGHT, WellKnownProperties.APPROACH_LIGHT, "bool", icon = "light"),
        ControlDef(
            "exterior_light", "cabin", EntityType.LIGHT, WellKnownProperties.EXTERIOR_LIGHT, "choice",
            optionKeys = listOf(
                "opt.exterior_light.0" to 0,
                "opt.exterior_light.1" to 1,
                "opt.exterior_light.2" to 2,
                "opt.exterior_light.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        ControlDef(
            "home_safe_light", "cabin", EntityType.LIGHT, WellKnownProperties.HOME_SAFE_LIGHT, "int",
            icon = "light", min = 0f, max = 4f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "day_mode", "cabin", EntityType.LIGHT, WellKnownProperties.DAY_MODE, "choice",
            optionKeys = listOf(
                "opt.day_mode.0" to 0,
                "opt.day_mode.1" to 1,
                "opt.day_mode.2" to 2,
            ),
            lastKnown = true, icon = "light",
        ),
        ControlDef("night_mode", "cabin", EntityType.LIGHT, WellKnownProperties.NIGHT_MODE, "bool", lastKnown = true, icon = "light"),
        ControlDef(
            "ambience_main_color", "cabin", EntityType.LIGHT, WellKnownProperties.AMBIENCE_MAIN_COLOR, "int",
            icon = "light", min = 0f, max = 20f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "ambience_intensity", "cabin", EntityType.LIGHT, WellKnownProperties.AMBIENCE_INTENSITY, "int",
            icon = "light", min = 0f, max = 100f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "esm_volume", "cabin", EntityType.EXTRA, WellKnownProperties.ESM_VOLUME, "int",
            acronym = "AVAS", icon = "system", min = 0f, max = 10f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "esm_sound", "cabin", EntityType.EXTRA, WellKnownProperties.ESM_SOUND, "int",
            acronym = "AVAS", icon = "system", min = 0f, max = 10f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "media_volume", "cabin", EntityType.EXTRA, WellKnownProperties.MEDIA_VOLUME, "int",
            icon = "system", min = 0f, max = 39f, step = 1f, lastKnown = true,
        ),
        ControlDef(
            "usb_mode", "cabin", EntityType.EXTRA, WellKnownProperties.USB_MODE, "choice",
            optionKeys = listOf(
                "opt.usb_mode.0" to 0,
                "opt.usb_mode.1" to 1,
                "opt.usb_mode.2" to 2,
            ),
            icon = "usb",
        ),
        ControlDef("hud_active", "cabin", EntityType.HUD, WellKnownProperties.HUD_ACTIVE, "bool", icon = "hud"),
        ControlDef("hud_snow", "cabin", EntityType.HUD, WellKnownProperties.HUD_SNOW, "bool", icon = "hud"),
        ControlDef("hud_ar", "cabin", EntityType.HUD, WellKnownProperties.HUD_AR, "bool", icon = "hud"),
        ControlDef(
            "wheel_custom_key", "cabin", EntityType.EXTRA, WellKnownProperties.WHEEL_CUSTOM_KEY, "choice",
            // BCM_FUNC_CUSTOM_KEY stores small ints = CUSTOM_KEY_TYPE_* − NONE (1/4/5/7/8),
            // plus firmware extras like driving settings (0x21111418 / AntoraVhalIds.CUSTOM_KEY_DRIVING_SETTINGS).
            optionKeys = listOf(
                "opt.wheel_custom_key.0" to 0,
                "opt.wheel_custom_key.drive" to 0x21111418,
                "opt.wheel_custom_key.1" to 1,
                "opt.wheel_custom_key.2" to 4,
                "opt.wheel_custom_key.3" to 5,
                "opt.wheel_custom_key.4" to 7,
                "opt.wheel_custom_key.5" to 8,
            ),
            lastKnown = true, icon = "drive",
            deviceClass = DeviceClass.ENUM,
        ),
    )

    fun i18n(context: Context, session: VehicleSession): I18nBundle =
        I18nBundle.load(context, session.integrationId)

    suspend fun snapshot(
        session: VehicleSession,
        context: Context? = null,
        memory: SettingsMemoryController? = null,
    ): List<Map<String, Any?>> {
        val store = context?.let { LastKnownStore(it) }
        val i18n = context?.let { i18n(it, session) }
        val persist = memory?.persistSnapshot().orEmpty()
        return ALL.map { def ->
            val base = if (def.id == "drive_mode") {
                driveModeMap(session, def, store, i18n)
            } else {
                defToMap(def, session.diagnose(def.property), store, i18n)
            }
            enrichPersist(base, def, persist[def.id], i18n)
        }
    }

    suspend fun entities(
        session: VehicleSession,
        context: Context? = null,
        memory: SettingsMemoryController? = null,
        android: AndroidSettingsController? = null,
    ): List<Map<String, Any?>> {
        val controls = snapshot(session, context, memory)
        val t = session.telemetry().first()
        val i18n = context?.let { i18n(it, session) }
        fun s(key: String, fallback: String) = i18n?.t(key, fallback) ?: fallback
        val driveDisplay = i18n?.resolveMaybe(t.driveMode)
            ?: i18n?.valueLabel("drive_mode", t.driveMode)
            ?: t.driveMode
        val regenDisplay = t.regenLevel?.let { lvl ->
            i18n?.valueLabel("regen", lvl) ?: lvl.toString()
        }
        val persist = memory?.persistSnapshot().orEmpty()
        val sensors = listOf(
            sensor("sensor_model", "home", s("sensor.model", "Modelo"), t.extras["model"], icon = "sensor", i18n = i18n),
            sensor(
                "sensor_gear", "home", s("sensor.gear", "Marcha"), t.gear?.toString(),
                icon = "drive", history = true, i18n = i18n, valueMapId = "gear",
            ),
            sensor(
                "sensor_speed", "home", s("sensor.speed", "Velocidade"),
                t.speedKmh?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.SPEED,
                unitOfMeasurement = UnitOfMeasurement.KM_PER_HOUR,
                icon = "sensor", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_soc", "home", s("sensor.soc", "Bateria"),
                (t.evBatteryPercent ?: t.hybridSocPercent)?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.BATTERY,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_fuel", "home", s("sensor.fuel", "Combustível"),
                t.fuelPercent?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.FUEL,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_range", "home", s("sensor.range", "Autonomia"),
                t.rangeKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_range_ev", "home", s("sensor.range_ev", "Autonomia EV"),
                t.rangeEvKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_range_fuel", "home", s("sensor.range_fuel", "Autonomia combustível"),
                t.rangeFuelKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_odometer", "home", s("sensor.odometer", "Odômetro"),
                t.odometerKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "sensor", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_ignition", "home", s("sensor.ignition", "Ignição"),
                t.ignitionState?.toString(),
                icon = "drive", i18n = i18n, valueMapId = "ignition",
            ),
            sensor(
                "sensor_parking_brake", "home", s("sensor.parking_brake", "Freio"),
                t.extras["parkingBrake"],
                icon = "brake", i18n = i18n, binary = true, valueMapId = "parking_brake",
            ),
            sensor(
                "sensor_drive_mode", "home", s("sensor.drive_mode", "Modo"),
                driveDisplay, icon = "drive_mode", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_regen", "home", s("sensor.regen", "Regen"),
                regenDisplay, icon = "regen", i18n = i18n,
            ),
            sensor(
                "sensor_hvac_temp", "climate", s("sensor.hvac_temp", "Temp"),
                t.hvacTempC?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.TEMPERATURE,
                unitOfMeasurement = UnitOfMeasurement.CELSIUS,
                icon = "temp", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_temp_ambient", "climate", s("sensor.temp_ambient", "Temp. externa"),
                t.tempAmbientC?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.TEMPERATURE,
                unitOfMeasurement = UnitOfMeasurement.CELSIUS,
                icon = "temp", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_temp_indoor", "climate", s("sensor.temp_indoor", "Temp. interna"),
                t.tempIndoorC?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.TEMPERATURE,
                unitOfMeasurement = UnitOfMeasurement.CELSIUS,
                icon = "temp", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_battery_temp", "energy", s("sensor.battery_temp", "Temp. bateria"),
                t.batteryTempC?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.TEMPERATURE,
                unitOfMeasurement = UnitOfMeasurement.CELSIUS,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_a", "energy", s("sensor.charge_a", "Corrente"),
                t.chargeCurrentA?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.CURRENT,
                unitOfMeasurement = UnitOfMeasurement.AMPERE,
                icon = "charge", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_plug", "energy", s("sensor.charge_plug", "Plug"),
                t.chargePlugConnected?.let { if (it) "1" else "0" },
                icon = "charge", history = true, i18n = i18n, binary = true, valueMapId = "charge_plug",
            ),
            sensor(
                "sensor_hybrid_soc", "energy", s("sensor.hybrid_soc", "SOC híbrido"),
                t.hybridSocPercent?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.BATTERY,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_eta", "energy", s("sensor.charge_eta", "Tempo de carga"),
                t.chargeEstimatedTimeMin?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DURATION,
                unitOfMeasurement = UnitOfMeasurement.MINUTE,
                icon = "charge", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_energy", "energy", s("sensor.charge_energy", "Energia de carga"),
                t.chargeEnergyKwh?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.ENERGY,
                unitOfMeasurement = UnitOfMeasurement.KILOWATT_HOUR,
                icon = "charge", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_work_a", "energy", s("sensor.charge_work_a", "Corrente de carga"),
                t.chargeWorkCurrentA?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.CURRENT,
                unitOfMeasurement = UnitOfMeasurement.AMPERE,
                icon = "charge", i18n = i18n,
            ),
            sensor(
                "sensor_charge_work_v", "energy", s("sensor.charge_work_v", "Tensão de carga"),
                t.chargeWorkVoltageV?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.VOLTAGE,
                unitOfMeasurement = UnitOfMeasurement.VOLT,
                icon = "charge", i18n = i18n,
            ),
            sensor(
                "sensor_avg_energy", "energy", s("sensor.avg_energy", "Consumo elétrico"),
                t.avgEnergyKwh100km?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.ENERGY,
                unitOfMeasurement = UnitOfMeasurement.KWH_PER_100KM,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_avg_fuel", "energy", s("sensor.avg_fuel", "Consumo combustível"),
                t.avgFuelL100km?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.FUEL,
                unitOfMeasurement = UnitOfMeasurement.LITER_PER_100KM,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_energy_flow_driving", "energy", s("sensor.energy_flow_driving", "Fluxo tração"),
                t.energyFlowDriving?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.POWER,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "drive", i18n = i18n,
            ),
            sensor(
                "sensor_energy_flow_battery", "energy", s("sensor.energy_flow_battery", "Fluxo bateria"),
                t.energyFlowBattery?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.POWER,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "battery", i18n = i18n,
            ),
            sensor(
                "sensor_energy_flow_climate", "energy", s("sensor.energy_flow_climate", "Fluxo clima"),
                t.energyFlowClimate?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.POWER,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "climate", i18n = i18n,
            ),
            sensor(
                "sensor_maintenance", "home", s("sensor.maintenance", "Próx. revisão"),
                t.maintenanceMileageKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "sensor", i18n = i18n,
            ),
            sensor(
                "sensor_since_maintenance", "home", s("sensor.since_maintenance", "Desde a revisão"),
                t.sinceMaintenanceKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "sensor", i18n = i18n,
            ),
        )
        val androidEntities = android?.entityMaps(i18n, persist).orEmpty()
        return sensors + controls + androidEntities
    }

    /** Current display value for shortcut conditions / entity_state watching. */
    suspend fun currentValue(session: VehicleSession, id: String): String? {
        val def = ALL.firstOrNull { it.id == id } ?: return null
        return when (val out = session.diagnose(def.property)) {
            is ReadOutcome.Ok -> out.value?.display()
            else -> null
        }
    }

    suspend fun set(session: VehicleSession, id: String, raw: String, context: Context? = null): Result<Unit> {
        val store = context?.let { LastKnownStore(it) }
        if (id == "drive_mode") {
            val mode = raw.toIntOrNull() ?: return Result.failure(IllegalArgumentException("bad mode"))
            // Antora EX5: DM_FUNC_DRIVE_MODE_SELECT accepts 5/6/7 (Comfort/Normal/Sport).
            // DRIVE_MODE_SELECTION_{PURE,HYBRID,POWER} are VHAL READ-only — do not write them.
            val result = session.set(WellKnownProperties.DRIVE_MODE, PropertyValue.IntVal(mode))
            if (result.isSuccess) store?.put(id, mode.toString())
            return result
        }
        val def = ALL.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("unknown control"))
        if (!def.writable) {
            return Result.failure(IllegalArgumentException("control is read-only"))
        }
        val pv: PropertyValue = when (def.input) {
            "bool" -> {
                val on = raw == "1" || raw.equals("true", true) || raw == "on"
                PropertyValue.IntVal(if (on) 1 else 0)
            }
            "float" -> PropertyValue.FloatVal(raw.toFloat())
            else -> PropertyValue.IntVal(raw.toInt())
        }
        val result = session.set(def.property, pv)
        if (result.isSuccess && def.lastKnown) store?.put(def.id, raw)
        return result
    }

    private fun enrichPersist(
        base: Map<String, Any?>,
        def: ControlDef,
        pin: Map<String, Any?>?,
        i18n: I18nBundle?,
    ): Map<String, Any?> {
        val enabled = pin?.get("enabled") == true
        val pVal = pin?.get("value") as? String
        return base + mapOf(
            "input" to def.input,
            "icon" to def.resolvedIcon(),
            "min" to def.min,
            "max" to def.max,
            "step" to def.step,
            "history" to def.history,
            "persistEnabled" to enabled,
            "persistValue" to pVal,
            "persistLabel" to pVal?.let { i18n?.valueLabel(def.resolvedValueMapId(), it) ?: it },
        )
    }

    private suspend fun driveModeMap(
        session: VehicleSession,
        def: ControlDef,
        store: LastKnownStore?,
        i18n: I18nBundle?,
    ): Map<String, Any?> {
        val enumOut = session.diagnose(WellKnownProperties.DRIVE_MODE)
        when (enumOut) {
            is ReadOutcome.Ok -> {
                val selected = enumOut.value?.asInt()?.toString()
                if (selected != null) {
                    store?.put(def.id, selected)
                    return baseMap(def, selected, "ok", null, stale = false, i18n = i18n)
                }
            }
            is ReadOutcome.Denied -> {
                val cached = store?.get(def.id)
                if (cached != null) {
                    return baseMap(def, cached, "cached", null, stale = true, i18n = i18n)
                }
                return baseMap(def, null, "denied", enumOut.permission, stale = false, i18n = i18n)
            }
            else -> Unit
        }
        val cached = store?.get(def.id)
        if (cached != null) {
            return baseMap(def, cached, "cached", null, stale = true, i18n = i18n)
        }
        return baseMap(def, null, "unavailable", null, stale = false, i18n = i18n)
    }

    private fun defToMap(
        def: ControlDef,
        outcome: ReadOutcome,
        store: LastKnownStore?,
        i18n: I18nBundle?,
    ): Map<String, Any?> {
        val (value, status, permission) = when (outcome) {
            is ReadOutcome.Ok -> {
                val disp = outcome.value?.display()
                if (def.lastKnown && !disp.isNullOrBlank() && disp != "0" && disp != "false") {
                    store?.put(def.id, disp)
                }
                Triple(disp, "ok", null as String?)
            }
            is ReadOutcome.Denied -> Triple(null, "denied", outcome.permission)
            is ReadOutcome.Failed -> Triple(null, "failed", outcome.message)
            is ReadOutcome.Unavailable -> Triple(null, "unavailable", null)
        }
        val empty = value == null || value == "" || value == "0" || value == "false"
        if (empty && def.lastKnown) {
            val cached = store?.get(def.id)
            if (cached != null) {
                return baseMap(def, cached, "cached", null, stale = true, i18n = i18n)
            }
        }
        return baseMap(def, value, status, permission, stale = false, i18n = i18n)
    }

    private fun optionMaps(def: ControlDef, i18n: I18nBundle?): List<Map<String, Any?>> {
        val fromMaps = i18n?.valueMapsSnapshot()?.get(def.resolvedValueMapId())
        if (!fromMaps.isNullOrEmpty() && (def.input == "choice" || def.optionKeys != null)) {
            val parsed = fromMaps.mapNotNull { (k, labelKey) ->
                k.toIntOrNull()?.let { value -> labelKey to value }
            }.sortedBy { it.second }
            if (parsed.isNotEmpty()) {
                return parsed.map { (labelKey, value) ->
                    mapOf("label" to (i18n.t(labelKey, labelKey)), "value" to value)
                }
            }
        }
        val keys = def.optionKeys ?: return emptyList()
        return keys.map { (key, value) ->
            mapOf("label" to (i18n?.t(key, key) ?: key), "value" to value)
        }
    }

    private fun baseMap(
        def: ControlDef,
        value: String?,
        status: String,
        permission: String?,
        stale: Boolean,
        i18n: I18nBundle?,
    ): Map<String, Any?> {
        val label = i18n?.t(def.resolvedLabelKey(), def.id) ?: def.id
        val hint = when {
            stale -> i18n?.t("status.cached")?.takeIf { it.isNotBlank() }
            i18n != null && i18n.has(def.resolvedHintKey()) ->
                i18n.t(def.resolvedHintKey()).takeIf { it.isNotBlank() }
            else -> null
        }
        val unitLabel = resolveUnitLabel(def.unitOfMeasurement, i18n)
        return mapOf(
            "id" to def.id,
            "group" to def.group,
            "entity" to def.entity.id,
            "label" to label,
            "labelKey" to def.resolvedLabelKey(),
            "hint" to hint,
            "description" to hint,
            "acronym" to def.acronym,
            "input" to def.input,
            "icon" to def.resolvedIcon(),
            "deviceClass" to def.deviceClass?.id,
            "unitOfMeasurement" to def.unitOfMeasurement?.id,
            "unitLabel" to unitLabel,
            "min" to def.min,
            "max" to def.max,
            "step" to def.step,
            "history" to def.history,
            "writable" to (def.writable && (status == "ok" || status == "cached")),
            "options" to optionMaps(def, i18n).ifEmpty { null },
            "value" to value,
            "valueLabel" to valueLabel(def, value, i18n),
            "status" to status,
            "permission" to permission,
            "needsPrivilege" to (status == "denied"),
            "stale" to stale,
        )
    }

    private fun valueLabel(def: ControlDef, value: String?, i18n: I18nBundle?): String? {
        if (value == null) return null
        if (def.input == "bool") {
            val on = value == "1" || value.equals("true", true) || value == "on"
            return i18n?.t(if (on) "common.on" else "common.off", if (on) "On" else "Off")
                ?: if (on) "On" else "Off"
        }
        i18n?.valueLabel(def.resolvedValueMapId(), value)?.let { return it }
        val asInt = value.toIntOrNull()
        if (asInt != null && def.optionKeys != null) {
            def.optionKeys.firstOrNull { it.second == asInt }?.let { (key, _) ->
                return i18n?.t(key, key) ?: key
            }
            if (asInt > 0xff) return "0x" + asInt.toString(16)
        }
        return null
    }

    private fun resolveUnitLabel(unit: UnitOfMeasurement?, i18n: I18nBundle?): String? {
        if (unit == null) return null
        return i18n?.t(unit.i18nKey(), unit.symbol) ?: unit.symbol
    }

    private fun sensor(
        id: String,
        group: String,
        label: String,
        value: String?,
        deviceClass: DeviceClass? = null,
        unitOfMeasurement: UnitOfMeasurement? = null,
        icon: String = "sensor",
        history: Boolean = false,
        i18n: I18nBundle? = null,
        /** valueMaps id for enum-like raw values (gear, ignition, …). */
        valueMapId: String? = null,
        /** Treat 0/1/on/off/true/false as localized On/Off. */
        binary: Boolean = false,
    ): Map<String, Any?> {
        val ok = value != null && value.isNotBlank()
        val unitLabel = resolveUnitLabel(unitOfMeasurement, i18n)
        val stringKey = "sensor." + id.removePrefix("sensor_")
        val hintKey = "$stringKey.hint"
        val hint = if (i18n != null && i18n.has(hintKey)) {
            i18n.t(hintKey).takeIf { it.isNotBlank() }
        } else {
            null
        }
        val valueLabel = when {
            !ok -> null
            valueMapId != null ->
                i18n?.valueLabel(valueMapId, value)
                    ?: if (binary) sensorBinaryLabel(value, i18n) else null
            binary -> sensorBinaryLabel(value, i18n)
            else -> null
        }
        return mapOf(
            "id" to id,
            "group" to group,
            "entity" to EntityType.SENSOR.id,
            "label" to label,
            "labelKey" to stringKey,
            "hint" to hint,
            "description" to hint,
            "input" to "sensor",
            "icon" to icon,
            "deviceClass" to deviceClass?.id,
            "unitOfMeasurement" to unitOfMeasurement?.id,
            "unitLabel" to unitLabel,
            "writable" to false,
            "options" to null,
            "value" to if (ok) value else null,
            "valueLabel" to valueLabel,
            "status" to if (ok) "ok" else "unavailable",
            "permission" to null,
            "needsPrivilege" to false,
            "stale" to false,
            "history" to history,
            "persistEnabled" to false,
        )
    }

    private fun sensorBinaryLabel(value: String?, i18n: I18nBundle?): String? {
        if (value == null) return null
        val on = value == "1" || value.equals("true", true) || value.equals("on", true)
        val off = value == "0" || value.equals("false", true) || value.equals("off", true)
        if (!on && !off) return value
        return i18n?.t(if (on) "common.on" else "common.off", if (on) "On" else "Off")
            ?: if (on) "On" else "Off"
    }
}
