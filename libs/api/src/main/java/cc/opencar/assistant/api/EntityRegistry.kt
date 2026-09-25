package cc.opencar.assistant.api

/**
 * Single product entity registry.
 *
 * Entity ids are Home Assistant–shaped: `domain.object_id`
 * (e.g. `cover.window_driver`, `switch.mirror_fold`, `climate.cabin`).
 *
 * - Atomic entities: [bindingKey] is the platform.json `entity` key (often the object_id).
 * - Composite entities: [bindingKey] is null; [attributes] map semantic names → binding keys.
 * - Legacy bare / pre-cover ids live in [aliases] and resolve via [resolve].
 *
 * Integrations never hardcode VHAL hex — they bind keys in `platform.json`.
 */
data class EntityDef(
    val id: String,
    val domain: EntityType,
    val group: String,
    /** Single-binding entities: platform.json `entity` key. Composite: null. */
    val bindingKey: String? = null,
    /** Composite only: attribute name → binding key. */
    val attributes: Map<String, String> = emptyMap(),
    /**
     * Soft UI hint: bool | choice | command | int | float | text | sensor | climate | cover | …
     * Card family is selected by [domain]; this refines the widget inside simple cards.
     */
    val input: String = "bool",
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
    val history: Boolean = false,
    /** Legacy product ids redirected here (e.g. hvac_power → climate.cabin). */
    val aliases: Set<String> = emptySet(),
    /**
     * Preferred VHAL area for instanced covers (`cover.window_driver`, `cover.sunroof`, …).
     */
    val areaId: Int? = null,
    /**
     * Per-attribute area overrides (rare; prefer separate cover entities per area).
     */
    val attributeAreas: Map<String, Int> = emptyMap(),
) {
    val isComposite: Boolean get() = bindingKey == null && attributes.isNotEmpty()

    /** Object id segment after `domain.` (HA-shaped). */
    val objectId: String
        get() {
            val prefix = domain.id + "."
            return if (id.startsWith(prefix)) id.removePrefix(prefix) else id
        }

    fun resolvedLabelKey(): String = labelKey ?: "control.$id"
    fun resolvedHintKey(): String = hintKey ?: "control.$id.hint"
    fun resolvedValueMapId(): String = valueMapId ?: id
    fun resolvedIcon(): String = icon ?: objectId

    fun property(): VehicleProperty? =
        bindingKey?.let { EntityRegistry.property(it, areaId ?: 0) }

    fun attributeProperty(attr: String): VehicleProperty? {
        val key = attributes[attr] ?: return null
        val area = when {
            attr in EntityRegistry.GLOBAL_AREA_ATTRS -> 0
            attributeAreas.containsKey(attr) -> attributeAreas.getValue(attr)
            else -> areaId ?: 0
        }
        return EntityRegistry.property(key, area)
    }
}

object EntityRegistry {
    const val NS = "oca"

    /** Attributes that are always area 0 even on instanced covers. */
    val GLOBAL_AREA_ATTRS: Set<String> = setOf("auto_close", "status", "open_height")

    fun property(bindingKey: String, areaId: Int = 0): VehicleProperty =
        VehicleProperty(NS, bindingKey, defaultAreaId = areaId)

    fun entityId(domain: EntityType, objectId: String): String = "${domain.id}.$objectId"

    // AOSP VehicleAreaSeat row-1 (HVAC_SEAT_VENTILATION areas on Antora).
    const val AREA_SEAT_ROW_1_LEFT = 0x1
    const val AREA_SEAT_ROW_1_RIGHT = 0x4

    // AOSP-style seat / window area ids used on Antora openings.
    const val AREA_WINDOW_ROW_1_LEFT = 0x10
    const val AREA_WINDOW_ROW_1_RIGHT = 0x40
    const val AREA_WINDOW_ROW_2_LEFT = 0x100
    const val AREA_WINDOW_ROW_2_RIGHT = 0x400
    /** Panoramic sunroof glass (VehicleAreaWindow.WINDOW_ROOF_TOP_1). */
    const val AREA_WINDOW_ROOF_TOP = 0x10000
    /** Sunshade / curtain (VehicleAreaWindow.WINDOW_ROOF_TOP_2). */
    const val AREA_WINDOW_ROOF_TOP_2 = 0x20000
    /** Power liftgate / trunk (VehicleAreaDoor rear). */
    const val AREA_DOOR_REAR = 0x20000000

    // ----- Composites -----

    val CLIMATE = EntityDef(
        id = entityId(EntityType.CLIMATE, "cabin"),
        domain = EntityType.CLIMATE,
        group = "controls",
        bindingKey = null,
        // Car HVAC is power+auto+setpoint (not HA heat/cool/fan_only).
        // Cabin fan + vent direction stay here; seat fans / boosts are atomics.
        // Zones: Antora HVAC_TEMPERATURE_SET is a single area (driver). Multi-zone
        // cabin climate would be extra climate.* entities (like covers) when a
        // platform lists multiple temp areas — not invented heat/cool modes.
        // AAOS/Ultra attrs: power, temperature, current_temperature, fan_mode,
        // fan_direction, ac, auto, recirc; dual/SYNC when HVAC_DUAL_ON is bound.
        attributes = mapOf(
            "power" to "hvac_power",
            "temperature" to "hvac_temp_c",
            "fan_mode" to "hvac_fan",
            "fan_direction" to "hvac_fan_direction",
            "ac" to "hvac_ac",
            "auto" to "hvac_auto",
            "recirc" to "hvac_recirc",
            "current_temperature" to "temp_indoor_c",
        ),
        input = "climate",
        aliases = setOf(
            "climate",
            "hvac_power", "hvac_ac", "hvac_auto", "hvac_recirc",
            "hvac_temp", "hvac_fan", "hvac_fan_direction",
        ),
        min = 16f,
        max = 32f,
        step = 0.5f,
        deviceClass = DeviceClass.TEMPERATURE,
        unitOfMeasurement = UnitOfMeasurement.CELSIUS,
        icon = "climate",
        history = true,
        lastKnown = true,
    )

    val DRIVETRAIN = EntityDef(
        id = entityId(EntityType.DRIVETRAIN, "vehicle"),
        domain = EntityType.DRIVETRAIN,
        group = "drive",
        bindingKey = null,
        attributes = mapOf(
            "gear" to "gear",
            "mode" to "drive_mode",
            "regen" to "regen",
            "battery_hold" to "battery_hold",
            "battery_save" to "battery_save",
            "battery_mode" to "battery_mode",
        ),
        input = "choice",
        valueMapId = "drive_mode",
        aliases = setOf(
            "drivetrain",
            "gear", "drive_mode", "regen",
            "battery_hold", "battery_save", "battery_mode",
        ),
        icon = "drive_mode",
        history = true,
        lastKnown = true,
    )

    val CHASSIS = EntityDef(
        id = entityId(EntityType.CHASSIS, "vehicle"),
        domain = EntityType.CHASSIS,
        group = "drive",
        bindingKey = null,
        attributes = mapOf(
            "brake_pedal" to "brake_pedal_mode",
            "esc" to "esc_sport",
            "hdc" to "hdc",
            "auto_hold" to "auto_hold",
            "epb" to "epb",
            "parking_brake" to "parking_brake",
        ),
        input = "bool",
        aliases = setOf(
            "chassis",
            "brake_pedal", "brake_pedal_mode", "esc_sport", "hdc", "auto_hold",
            "epb", "parking_brake",
        ),
        icon = "brake",
        lastKnown = true,
    )

    val STEERING = EntityDef(
        id = entityId(EntityType.STEERING, "vehicle"),
        domain = EntityType.STEERING,
        group = "drive",
        bindingKey = null,
        attributes = mapOf(
            "assist_level" to "steer_assist_level",
            "sync_drive_mode" to "steer_sync_drive_mode",
            "intelligent" to "intelligent_steer",
            "custom_key" to "wheel_custom_key",
        ),
        input = "choice",
        optionKeys = listOf(
            "opt.steer_assist_level.1" to 1,
            "opt.steer_assist_level.2" to 2,
            "opt.steer_assist_level.3" to 3,
        ),
        valueMapId = "steer_assist_level",
        aliases = setOf(
            "steering",
            "steer_assist_level", "steer_sync_drive_mode", "intelligent_steer",
            "wheel_custom_key", "steer_soft", "steer_medium", "steer_heavy",
        ),
        icon = "steer",
        lastKnown = true,
    )

    val CHARGER = EntityDef(
        id = entityId(EntityType.CHARGER, "vehicle"),
        domain = EntityType.CHARGER,
        group = "energy",
        bindingKey = null,
        attributes = mapOf(
            "plug" to "charge_plug",
            "current" to "charge_current",
            "limit" to "charge_limit",
            "switch" to "charge_switch",
            "pre_now" to "charge_pre_now",
            "soc_max" to "charge_soc_max",
            "soc_min" to "charge_soc_min",
            "discharge_soc" to "charge_discharge_soc",
            "v2l" to "charge_v2l",
            "v2v" to "charge_v2v",
            "parking" to "charge_parking",
            "estimated_time" to "charge_estimated_time",
            "energy" to "charge_energy",
            "work_current" to "charge_work_current",
            "work_voltage" to "charge_work_voltage",
            "external_light" to "charge_external_light",
        ),
        input = "bool",
        aliases = setOf(
            "charger",
            "charge_plug", "charge_current", "charge_limit", "charge_switch", "charge_pre_now",
            "charge_soc_max", "charge_soc_min", "charge_discharge_soc",
            "charge_v2l", "charge_v2v", "charge_parking",
            "charge_estimated_time", "charge_energy", "charge_work_current", "charge_work_voltage",
            "charge_external_light",
        ),
        icon = "charge",
        history = true,
        lastKnown = true,
    )

    val EV_BATTERY = EntityDef(
        id = entityId(EntityType.EV_BATTERY, "main"),
        domain = EntityType.EV_BATTERY,
        group = "energy",
        bindingKey = null,
        attributes = mapOf(
            "percent" to "ev_battery_percent",
            "level_raw" to "ev_battery_level_raw",
            "temp_c" to "battery_temp_c",
            "hybrid_soc" to "hybrid_soc",
        ),
        input = "sensor",
        aliases = setOf(
            "ev_battery",
            "ev_battery_percent", "ev_battery_level_raw", "battery_temp_c", "hybrid_soc",
        ),
        icon = "battery",
        history = true,
        writable = false,
    )

    val HUD = EntityDef(
        id = entityId(EntityType.HUD, "main"),
        domain = EntityType.HUD,
        group = "display",
        bindingKey = null,
        attributes = mapOf(
            "active" to "hud_active",
            "snow" to "hud_snow",
            "ar" to "hud_ar",
            "display_mode" to "hud_display_mode",
            "angle" to "hud_angle",
        ),
        input = "bool",
        aliases = setOf(
            "hud",
            "hud_active", "hud_snow", "hud_ar", "hud_display_mode", "hud_angle",
        ),
        icon = "hud",
        lastKnown = true,
    )

    val LIGHT = EntityDef(
        id = entityId(EntityType.LIGHT, "ambient"),
        domain = EntityType.LIGHT,
        group = "lights",
        bindingKey = null,
        attributes = mapOf(
            "color" to "ambience_main_color",
            "brightness" to "ambience_intensity",
        ),
        input = "light",
        optionKeys = listOf(
            "opt.ambience_main_color.2" to 2,
            "opt.ambience_main_color.3" to 3,
            "opt.ambience_main_color.4" to 4,
        ),
        aliases = setOf(
            "light",
            "ambience_main_color", "ambience_intensity", "ambient_light",
        ),
        icon = "light",
        lastKnown = true,
        min = 0f,
        max = 100f,
        step = 1f,
        valueMapId = "ambience_main_color",
    )

    // ----- Covers (position 0–100 except trunk) -----

    val COVER_WINDOW_DRIVER = cover(
        objectId = "window_driver",
        bindingKey = "window_pos",
        areaId = AREA_WINDOW_ROW_1_LEFT,
        deviceClass = DeviceClass.WINDOW,
        aliases = setOf("window.driver", "window_driver"),
        icon = "window",
    )
    val COVER_WINDOW_PASSENGER = cover(
        objectId = "window_passenger",
        bindingKey = "window_pos",
        areaId = AREA_WINDOW_ROW_1_RIGHT,
        deviceClass = DeviceClass.WINDOW,
        aliases = setOf("window.passenger", "window_passenger"),
        icon = "window",
    )
    val COVER_WINDOW_REAR_LEFT = cover(
        objectId = "window_rear_left",
        bindingKey = "window_pos",
        areaId = AREA_WINDOW_ROW_2_LEFT,
        deviceClass = DeviceClass.WINDOW,
        aliases = setOf("window.rear_left", "window_rear_left"),
        icon = "window",
    )
    val COVER_WINDOW_REAR_RIGHT = cover(
        objectId = "window_rear_right",
        bindingKey = "window_pos",
        areaId = AREA_WINDOW_ROW_2_RIGHT,
        deviceClass = DeviceClass.WINDOW,
        aliases = setOf("window.rear_right", "window_rear_right"),
        icon = "window",
    )
    val COVER_SUNROOF = cover(
        objectId = "sunroof",
        bindingKey = "window_pos",
        areaId = AREA_WINDOW_ROOF_TOP,
        deviceClass = DeviceClass.WINDOW,
        aliases = setOf("sunroof"),
        icon = "window",
    )
    val COVER_SUNSHADE = cover(
        objectId = "sunshade",
        bindingKey = "window_pos",
        areaId = AREA_WINDOW_ROOF_TOP_2,
        deviceClass = DeviceClass.SHADE,
        aliases = setOf("sunshade"),
        icon = "window",
    )

    /** Power liftgate: open/close via DOOR_MOVE; status via BCM enum (no live position). */
    val COVER_TRUNK = EntityDef(
        id = entityId(EntityType.COVER, "trunk"),
        domain = EntityType.COVER,
        group = "controls",
        bindingKey = null,
        attributes = mapOf(
            "status" to "trunk_status",
            "move" to "trunk_move",
        ),
        input = "cover",
        aliases = setOf("trunk", "trunk_status", "trunk_move"),
        icon = "cabin",
        lastKnown = true,
        deviceClass = DeviceClass.GARAGE,
        areaId = AREA_DOOR_REAR,
        valueMapId = "cover",
    )

    // Camera entities are virtual (Camera2 + platform.json roles), not VHAL-bound.
    val CAMERA_FRONT = camera("front")
    val CAMERA_REAR = camera("rear")
    val CAMERA_LEFT = camera("left")
    val CAMERA_RIGHT = camera("right")
    val CAMERAS: List<EntityDef> = listOf(
        CAMERA_FRONT, CAMERA_RIGHT, CAMERA_REAR, CAMERA_LEFT,
    )

    val ALL: List<EntityDef> = listOf(
        CLIMATE,
        DRIVETRAIN,
        CHASSIS,
        STEERING,
        CHARGER,
        EV_BATTERY,
        HUD,
        LIGHT,
        COVER_WINDOW_DRIVER,
        COVER_WINDOW_PASSENGER,
        COVER_WINDOW_REAR_LEFT,
        COVER_WINDOW_REAR_RIGHT,
        COVER_SUNROOF,
        COVER_SUNSHADE,
        COVER_TRUNK,
        CAMERA_FRONT,
        CAMERA_RIGHT,
        CAMERA_REAR,
        CAMERA_LEFT,

        // Cover siblings
        e("auto_close_window", "controls", EntityType.SWITCH, "bool",
            lastKnown = true, icon = "window",
        ),
        e("sunroof_tilt", "controls", EntityType.SWITCH, "bool",
            lastKnown = true, icon = "window",
        ),
        e("trunk_open_height", "controls", EntityType.NUMBER, "int",
            icon = "cabin", min = 0f, max = 100f, step = 1f, lastKnown = true,
        ),

        // Drive extras
        e("cst", "drive", EntityType.SWITCH, "bool", acronym = "CST", lastKnown = true, icon = "drive"),

        // HVAC extras — boosts / presets, not climate modes.
        e("hvac_max_defrost", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_max_ac", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_eco", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_auto_dry", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_rapid_cool", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_rapid_heat", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_electric_defrost", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_auto_recirc", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("hvac_auto_seat_vent", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "seat"),

        // Seat ventilation — fan domain (separate from climate cabin fan).
        fan(
            objectId = "seat_driver",
            bindingKey = "hvac_seat_vent",
            areaId = AREA_SEAT_ROW_1_LEFT,
            aliases = setOf("hvac_seat_vent", "hvac_seat_vent_driver"),
            icon = "seat",
        ),
        fan(
            objectId = "seat_passenger",
            bindingKey = "hvac_seat_vent",
            areaId = AREA_SEAT_ROW_1_RIGHT,
            aliases = setOf("hvac_seat_vent_passenger"),
            icon = "seat",
        ),

        e("parking_comfort", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("nap_mode", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),
        e("space_capsule", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "climate"),

        e("mirror_fold", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "cabin"),
        e("mirror_auto_fold", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "cabin"),

        // Real locks (actuators). Policy helpers stay switch.* (approach_unlock, …).
        lock(
            objectId = "central",
            bindingKey = "central_lock",
            aliases = setOf("central_lock"),
            icon = "lock",
        ),
        lock(
            objectId = "windows",
            bindingKey = "window_lock",
            aliases = setOf("window_lock"),
            icon = "lock",
        ),

        e("lka", "adas", EntityType.SWITCH, "bool", bindingKey = "lane_keeping", acronym = "LKA", lastKnown = true, icon = "adas"),
        e("ldw", "adas", EntityType.SWITCH, "bool", acronym = "LDW", lastKnown = true, icon = "adas"),
        e("elka", "adas", EntityType.SWITCH, "bool", acronym = "ELKA", lastKnown = true, icon = "adas"),
        e("aeb", "adas", EntityType.SWITCH, "bool", acronym = "AEB", lastKnown = true, icon = "adas"),
        e("fcw", "adas", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.fcw.0" to 0,
                "opt.fcw.1" to 1,
                "opt.fcw.2" to 2,
                "opt.fcw.3" to 3,
            ),
            acronym = "FCW", icon = "adas",
        ),
        e("rcta", "adas", EntityType.SWITCH, "bool", acronym = "RCTA", lastKnown = true, icon = "adas"),
        e("rcta_volume", "adas", EntityType.NUMBER, "int",
            icon = "adas", min = 0f, max = 3f, step = 1f, lastKnown = true,
        ),
        e("rcw", "adas", EntityType.SWITCH, "bool", acronym = "RCW", lastKnown = true, icon = "adas"),
        e("fcda", "adas", EntityType.SWITCH, "bool", acronym = "FCDA", lastKnown = true, icon = "adas"),
        e("dow", "adas", EntityType.SWITCH, "bool", acronym = "DOW", lastKnown = true, icon = "adas"),
        e("idas_mode", "adas", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.idas_mode.1" to 1,
                "opt.idas_mode.2" to 2,
            ),
            acronym = "IDAS", icon = "adas", lastKnown = true,
        ),
        e("speed_limit_warn", "adas", EntityType.SWITCH, "bool", lastKnown = true, icon = "adas"),
        e("speed_limit_max", "adas", EntityType.SENSOR, "sensor",
            writable = false,
            deviceClass = DeviceClass.SPEED,
            unitOfMeasurement = UnitOfMeasurement.KM_PER_HOUR,
            icon = "adas", lastKnown = true,
        ),
        e("speed_limit_update", "adas", EntityType.SWITCH, "bool", lastKnown = true, icon = "adas"),
        e("lane_change_warn", "adas", EntityType.SWITCH, "bool", lastKnown = true, icon = "adas"),
        e("dms", "adas", EntityType.SWITCH, "bool", acronym = "DMS", lastKnown = true, icon = "adas"),

        // Access *policy* — not lock domain.
        e("approach_unlock", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "lock"),
        e("away_lock", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "lock"),
        e("audible_lock", "controls", EntityType.SWITCH, "bool", icon = "lock"),
        e("keyless_unlock", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "lock"),
        e("twostep_unlock", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "lock"),
        e("p_gear_unlock", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "lock"),
        e("easy_ingress", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "seat"),
        e("vehicle_locator_mode", "controls", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.vehicle_locator_mode.1" to 1,
                "opt.vehicle_locator_mode.2" to 2,
                "opt.vehicle_locator_mode.3" to 3,
            ),
            lastKnown = true, icon = "cabin",
        ),

        e("courtesy_light", "lights", EntityType.SWITCH, "bool", icon = "light"),
        e("approach_light", "lights", EntityType.SWITCH, "bool", icon = "light"),
        e("exterior_light", "lights", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.exterior_light.0" to 0,
                "opt.exterior_light.1" to 1,
                "opt.exterior_light.2" to 2,
                "opt.exterior_light.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        e("rear_fog", "lights", EntityType.SWITCH, "bool", icon = "light"),
        e("hazard_lights", "lights", EntityType.SWITCH, "bool", icon = "light"),
        e("high_beam", "lights", EntityType.SWITCH, "bool", icon = "light"),
        e("headlight_height", "lights", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.headlight_height.0" to 0,
                "opt.headlight_height.1" to 1,
                "opt.headlight_height.2" to 2,
                "opt.headlight_height.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        e("home_safe_light", "lights", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.home_safe_light.0" to 0,
                "opt.home_safe_light.1" to 1,
                "opt.home_safe_light.2" to 2,
                "opt.home_safe_light.3" to 3,
                "opt.home_safe_light.4" to 4,
                "opt.home_safe_light.5" to 5,
                "opt.home_safe_light.6" to 6,
            ),
            lastKnown = true, icon = "light",
        ),
        e("day_mode", "lights", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.day_mode.1" to 1,
                "opt.day_mode.2" to 2,
                "opt.day_mode.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        e("night_mode", "lights", EntityType.SWITCH, "bool", lastKnown = true, icon = "light"),

        e("display_brightness", "display", EntityType.NUMBER, "int",
            icon = "light", min = 0f, max = 100f, step = 1f, lastKnown = true,
        ),
        e("display_auto_brightness", "display", EntityType.SWITCH, "bool", lastKnown = true, icon = "light"),
        e("rain_sensor_sensitivity", "controls", EntityType.NUMBER, "int",
            icon = "cabin", min = 0f, max = 5f, step = 1f, lastKnown = true,
        ),
        e("auto_rear_wiper", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "cabin"),
        e("wireless_charge", "controls", EntityType.SWITCH, "bool", lastKnown = true, icon = "charge"),
        e("wireless_charge_state", "controls", EntityType.SENSOR, "sensor",
            writable = false, icon = "charge",
        ),
        e("tire_pressure", "vehicle", EntityType.SENSOR, "sensor",
            writable = false, icon = "sensor", history = true,
        ),
        e("tire_warning", "vehicle", EntityType.SENSOR, "sensor",
            writable = false, icon = "sensor",
        ),
        e("esm_volume", "sound", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.esm_volume.0" to 0,
                "opt.esm_volume.1" to 1,
                "opt.esm_volume.2" to 2,
                "opt.esm_volume.3" to 3,
            ),
            acronym = "AVAS", icon = "system", lastKnown = true,
        ),
        e("esm_sound", "sound", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.esm_sound.0" to 1,
                "opt.esm_sound.1" to 2,
                "opt.esm_sound.2" to 3,
            ),
            acronym = "AVAS", icon = "system", lastKnown = true,
        ),
        e("media_volume", "sound", EntityType.NUMBER, "int",
            icon = "sound", min = 0f, max = 39f, step = 1f, lastKnown = true,
        ),
        e("speed_volume", "sound", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.speed_volume.0" to 0,
                "opt.speed_volume.1" to 1,
                "opt.speed_volume.2" to 2,
                "opt.speed_volume.3" to 3,
            ),
            lastKnown = true, icon = "system",
        ),
        e("warning_volume", "sound", EntityType.NUMBER, "int",
            icon = "sound", min = 0f, max = 39f, step = 1f, lastKnown = true,
        ),
        e("voice_broadcast", "assistant", EntityType.SELECT, "choice",
            lastKnown = true, icon = "system",
        ),
        e("usb_mode", "vehicle", EntityType.SELECT, "choice",
            optionKeys = listOf(
                "opt.usb_mode.0" to 0,
                "opt.usb_mode.1" to 1,
                "opt.usb_mode.2" to 2,
            ),
            icon = "usb",
        ),
        e("vr_activated", "assistant", EntityType.SWITCH, "bool", lastKnown = true, icon = "system"),
    )

    private val byId: Map<String, EntityDef> = ALL.associateBy { it.id }

    private val byAlias: Map<String, EntityDef> = buildMap {
        for (def in ALL) {
            for (a in def.aliases) put(a, def)
        }
    }

    private val byBindingKey: Map<String, EntityDef> = buildMap {
        for (def in ALL) {
            def.bindingKey?.let { putIfAbsent(it, def) }
            for (bk in def.attributes.values) putIfAbsent(bk, def)
        }
    }

    fun byId(id: String): EntityDef? = byId[id]

    fun resolve(id: String): EntityDef? = byId[id] ?: byAlias[id]

    fun resolveBinding(bindingKey: String): EntityDef? =
        byId[bindingKey] ?: byAlias[bindingKey] ?: byBindingKey[bindingKey]

    /** Attribute name for an alias on a composite, if any. */
    fun aliasAttribute(aliasId: String): String? {
        val def = byAlias[aliasId] ?: return null
        if (!def.isComposite) return null
        def.attributes.entries.firstOrNull { it.value == aliasId }?.key?.let { return it }
        return when (aliasId) {
            "hvac_temp" -> "temperature"
            "hvac_fan" -> "fan_mode"
            "steer_soft", "steer_medium", "steer_heavy" -> "assist_level"
            "brake_pedal" -> "brake_pedal"
            "esc_sport" -> "esc"
            "drive_mode" -> "mode"
            else -> def.attributes.entries.firstOrNull {
                it.value == aliasId || it.value == aliasId + "_c"
            }?.key
        }
    }

    /** Map legacy steer_* bool aliases to assist_level enum values. */
    fun steerAssistLevelForAlias(aliasId: String): Int? = when (aliasId) {
        "steer_soft" -> 1
        "steer_medium" -> 2
        "steer_heavy" -> 3
        else -> null
    }

    private fun camera(role: String) = EntityDef(
        id = entityId(EntityType.CAMERA, role),
        domain = EntityType.CAMERA,
        group = "cameras",
        bindingKey = null,
        input = "camera",
        writable = false,
        icon = "camera",
    )

    /**
     * Position cover bound to a single `window_pos` (or similar) property + area.
     * Product state is open/closed; `current_position` is 0–100.
     */
    private fun cover(
        objectId: String,
        bindingKey: String,
        areaId: Int,
        deviceClass: DeviceClass,
        aliases: Set<String> = emptySet(),
        icon: String,
    ): EntityDef {
        val id = entityId(EntityType.COVER, objectId)
        return EntityDef(
            id = id,
            domain = EntityType.COVER,
            group = "controls",
            bindingKey = bindingKey,
            input = "cover",
            aliases = aliases + objectId,
            icon = icon,
            lastKnown = true,
            areaId = areaId,
            deviceClass = deviceClass,
            min = 0f,
            max = 100f,
            step = 1f,
            valueMapId = "cover",
        )
    }

    /** Seat / cabin fan with discrete levels (HA fan presets). */
    private fun fan(
        objectId: String,
        bindingKey: String,
        areaId: Int,
        aliases: Set<String> = emptySet(),
        icon: String,
    ): EntityDef {
        val id = entityId(EntityType.FAN, objectId)
        return EntityDef(
            id = id,
            domain = EntityType.FAN,
            group = "controls",
            bindingKey = bindingKey,
            input = "choice",
            optionKeys = listOf(
                "opt.hvac_seat_vent.0" to 0,
                "opt.hvac_seat_vent.1" to 1,
                "opt.hvac_seat_vent.2" to 2,
                "opt.hvac_seat_vent.3" to 3,
            ),
            aliases = aliases + objectId,
            icon = icon,
            lastKnown = true,
            areaId = areaId,
            valueMapId = "hvac_seat_vent",
            min = 0f,
            max = 3f,
            step = 1f,
        )
    }

    /** Actuator lock — locked (1) / unlocked (0). */
    private fun lock(
        objectId: String,
        bindingKey: String,
        aliases: Set<String> = emptySet(),
        icon: String,
    ): EntityDef {
        val id = entityId(EntityType.LOCK, objectId)
        return EntityDef(
            id = id,
            domain = EntityType.LOCK,
            group = "controls",
            bindingKey = bindingKey,
            input = "choice",
            optionKeys = listOf(
                "lock.unlocked" to 0,
                "lock.locked" to 1,
            ),
            aliases = aliases + objectId,
            icon = icon,
            lastKnown = true,
            valueMapId = "lock",
        )
    }

    /**
     * Atomic widget: [objectId] is the HA object_id; [id] = `domain.objectId`.
     * [bindingKey] defaults to [objectId] (platform.json entity key).
     * Bare [objectId] is always aliased for legacy resolve.
     */
    private fun e(
        objectId: String,
        group: String,
        domain: EntityType,
        input: String,
        bindingKey: String? = objectId,
        optionKeys: List<Pair<String, Int>>? = null,
        writable: Boolean = true,
        deviceClass: DeviceClass? = null,
        unitOfMeasurement: UnitOfMeasurement? = null,
        acronym: String? = null,
        lastKnown: Boolean = false,
        icon: String? = null,
        min: Float? = null,
        max: Float? = null,
        step: Float? = null,
        history: Boolean = false,
        aliases: Set<String> = emptySet(),
        areaId: Int? = null,
    ): EntityDef {
        val id = entityId(domain, objectId)
        return EntityDef(
            id = id,
            domain = domain,
            group = group,
            bindingKey = bindingKey,
            input = input,
            optionKeys = optionKeys,
            writable = writable,
            deviceClass = deviceClass,
            unitOfMeasurement = unitOfMeasurement,
            acronym = acronym,
            lastKnown = lastKnown,
            icon = icon,
            min = min,
            max = max,
            step = step,
            history = history,
            aliases = aliases + objectId,
            areaId = areaId,
        )
    }
}
