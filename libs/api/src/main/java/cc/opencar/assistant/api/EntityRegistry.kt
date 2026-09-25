package cc.opencar.assistant.api

/**
 * Single product entity registry (HA-inspired).
 *
 * - Atomic entities: [id] equals the platform.json binding key ([bindingKey]).
 * - Composite entities (e.g. [climate]): [bindingKey] is null; [attributes] map
 *   HA attribute names → platform binding keys. Product id is [id] only.
 *
 * Integrations never hardcode VHAL hex — they bind keys in `platform.json`.
 */
data class EntityDef(
    val id: String,
    val domain: EntityType,
    val group: String,
    /** Single-binding entities: platform.json `entity` key. Composite: null. */
    val bindingKey: String? = id,
    /** Composite only: attribute name → binding key. */
    val attributes: Map<String, String> = emptyMap(),
    /**
     * Soft UI hint for simple domains: bool | choice | command | int | float | text | sensor | climate.
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
    /** Legacy product ids redirected here (e.g. hvac_power → climate). */
    val aliases: Set<String> = emptySet(),
) {
    val isComposite: Boolean get() = bindingKey == null && attributes.isNotEmpty()

    fun resolvedLabelKey(): String = labelKey ?: "control.$id"
    fun resolvedHintKey(): String = hintKey ?: "control.$id.hint"
    fun resolvedValueMapId(): String = valueMapId ?: id
    fun resolvedIcon(): String = icon ?: id

    /** Primary [VehicleProperty] for atomic entities; null for composites. */
    fun property(): VehicleProperty? = bindingKey?.let { EntityRegistry.property(it) }

    fun attributeProperty(attr: String): VehicleProperty? =
        attributes[attr]?.let { EntityRegistry.property(it) }
}

object EntityRegistry {
    const val NS = "oca"

    fun property(bindingKey: String): VehicleProperty = VehicleProperty(NS, bindingKey)

    private val climateAliases = setOf(
        "hvac_power", "hvac_ac", "hvac_auto", "hvac_recirc",
        "hvac_max_defrost", "hvac_max_ac", "hvac_eco", "hvac_auto_dry",
        "hvac_rapid_cool", "hvac_rapid_heat", "hvac_temp", "hvac_fan", "hvac_fan_direction",
    )

    val CLIMATE = EntityDef(
        id = "climate",
        domain = EntityType.CLIMATE,
        group = "controls",
        bindingKey = null,
        attributes = mapOf(
            "power" to "hvac_power",
            "temperature" to "hvac_temp_c",
            "fan_mode" to "hvac_fan",
            "fan_direction" to "hvac_fan_direction",
            "ac" to "hvac_ac",
            "auto" to "hvac_auto",
            "recirc" to "hvac_recirc",
            "max_defrost" to "hvac_max_defrost",
            "max_ac" to "hvac_max_ac",
            "eco" to "hvac_eco",
            "auto_dry" to "hvac_auto_dry",
            "rapid_cool" to "hvac_rapid_cool",
            "rapid_heat" to "hvac_rapid_heat",
            "current_temperature" to "temp_indoor_c",
        ),
        input = "climate",
        aliases = climateAliases,
        min = 16f,
        max = 32f,
        step = 0.5f,
        deviceClass = DeviceClass.TEMPERATURE,
        unitOfMeasurement = UnitOfMeasurement.CELSIUS,
        icon = "climate",
        history = true,
        lastKnown = true,
    )

    val ALL: List<EntityDef> = listOf(
        CLIMATE,
        e("drive_mode", "drive", EntityType.DRIVE_MODE, "choice",
            optionKeys = listOf(
                "opt.drive_mode.1" to 5,
                "opt.drive_mode.2" to 6,
                "opt.drive_mode.3" to 7,
            ),
            lastKnown = true, icon = "drive_mode", history = true,
        ),
        e("regen", "drive", EntityType.REGEN, "choice",
            optionKeys = listOf(
                "opt.regen.1" to 1,
                "opt.regen.2" to 2,
                "opt.regen.3" to 3,
                "opt.regen.4" to 4,
            ),
            lastKnown = true, icon = "regen",
        ),
        e("esc_sport", "drive", EntityType.EXTRA, "bool", acronym = "ESC", icon = "drive"),
        e("auto_hold", "drive", EntityType.BRAKE, "bool", icon = "brake"),
        e("hdc", "drive", EntityType.BRAKE, "bool", acronym = "HDC", icon = "brake"),
        e("cst", "drive", EntityType.EXTRA, "bool", acronym = "CST", lastKnown = true, icon = "drive"),
        e("steer_soft", "drive", EntityType.STEERING, "bool", lastKnown = true, icon = "steer"),
        e("steer_medium", "drive", EntityType.STEERING, "bool", lastKnown = true, icon = "steer"),
        e("steer_heavy", "drive", EntityType.STEERING, "bool", lastKnown = true, icon = "steer"),
        e("intelligent_steer", "drive", EntityType.STEERING, "bool", lastKnown = true, icon = "steer"),
        e("brake_pedal", "drive", EntityType.BRAKE, "choice",
            bindingKey = "brake_pedal_mode",
            optionKeys = listOf(
                "opt.brake_pedal.0" to 0,
                "opt.brake_pedal.1" to 1,
                "opt.brake_pedal.2" to 2,
            ),
            icon = "brake",
        ),
        e("hvac_seat_vent", "controls", EntityType.SEAT, "choice",
            optionKeys = listOf(
                "opt.hvac_seat_vent.0" to 0,
                "opt.hvac_seat_vent.1" to 1,
                "opt.hvac_seat_vent.2" to 2,
                "opt.hvac_seat_vent.3" to 3,
            ),
            icon = "seat",
        ),
        e("battery_hold", "energy", EntityType.ENERGY, "bool", lastKnown = true, icon = "battery"),
        e("battery_save", "energy", EntityType.ENERGY, "bool", lastKnown = true, icon = "battery"),
        e("battery_mode", "energy", EntityType.ENERGY, "choice",
            optionKeys = listOf(
                "opt.battery_mode.1" to 1,
                "opt.battery_mode.2" to 2,
                "opt.battery_mode.3" to 3,
            ),
            icon = "battery",
        ),
        e("charge_current", "energy", EntityType.CHARGING, "float",
            deviceClass = DeviceClass.CURRENT,
            unitOfMeasurement = UnitOfMeasurement.AMPERE,
            icon = "charge", min = 0f, max = 32f, step = 1f, history = true,
        ),
        e("charge_limit", "energy", EntityType.CHARGING, "int",
            deviceClass = DeviceClass.CURRENT,
            unitOfMeasurement = UnitOfMeasurement.AMPERE,
            icon = "charge", min = 5f, max = 32f, step = 1f, lastKnown = true,
        ),
        e("charge_switch", "energy", EntityType.CHARGING, "command",
            optionKeys = listOf(
                "opt.charge_switch.609" to 609,
                "opt.charge_switch.610" to 610,
                "opt.charge_switch.611" to 611,
            ),
            icon = "charge",
        ),
        e("charge_pre_now", "energy", EntityType.CHARGING, "bool", icon = "charge"),
        e("charge_soc_max", "energy", EntityType.CHARGING, "float",
            deviceClass = DeviceClass.BATTERY,
            unitOfMeasurement = UnitOfMeasurement.PERCENT,
            icon = "charge", min = 50f, max = 100f, step = 1f, lastKnown = true,
        ),
        e("charge_soc_min", "energy", EntityType.CHARGING, "float",
            deviceClass = DeviceClass.BATTERY,
            unitOfMeasurement = UnitOfMeasurement.PERCENT,
            icon = "charge", min = 0f, max = 50f, step = 1f, lastKnown = true,
        ),
        e("charge_discharge_soc", "energy", EntityType.CHARGING, "float",
            deviceClass = DeviceClass.BATTERY,
            unitOfMeasurement = UnitOfMeasurement.PERCENT,
            icon = "charge", min = 0f, max = 100f, step = 1f, lastKnown = true,
        ),
        e("charge_v2l", "energy", EntityType.CHARGING, "bool", acronym = "V2L", icon = "charge"),
        e("charge_v2v", "energy", EntityType.CHARGING, "bool", acronym = "V2V", icon = "charge"),
        e("charge_parking", "energy", EntityType.CHARGING, "bool", icon = "charge"),
        e("parking_comfort", "controls", EntityType.CLIMATE, "bool", lastKnown = true, icon = "climate"),
        e("nap_mode", "controls", EntityType.CLIMATE, "bool", lastKnown = true, icon = "climate"),
        e("space_capsule", "controls", EntityType.CLIMATE, "bool", lastKnown = true, icon = "climate"),
        e("lka", "adas", EntityType.ADAS, "bool", bindingKey = "lane_keeping", acronym = "LKA", lastKnown = true, icon = "adas"),
        e("ldw", "adas", EntityType.ADAS, "bool", acronym = "LDW", lastKnown = true, icon = "adas"),
        e("elka", "adas", EntityType.ADAS, "bool", acronym = "ELKA", lastKnown = true, icon = "adas"),
        e("aeb", "adas", EntityType.ADAS, "bool", acronym = "AEB", lastKnown = true, icon = "adas"),
        e("fcw", "adas", EntityType.ADAS, "choice",
            optionKeys = listOf(
                "opt.fcw.0" to 0,
                "opt.fcw.1" to 1,
                "opt.fcw.2" to 2,
                "opt.fcw.3" to 3,
            ),
            acronym = "FCW", icon = "adas",
        ),
        e("rcta", "adas", EntityType.ADAS, "bool", acronym = "RCTA", lastKnown = true, icon = "adas"),
        e("rcw", "adas", EntityType.ADAS, "bool", acronym = "RCW", lastKnown = true, icon = "adas"),
        e("fcda", "adas", EntityType.ADAS, "bool", acronym = "FCDA", lastKnown = true, icon = "adas"),
        e("dow", "adas", EntityType.ADAS, "bool", acronym = "DOW", lastKnown = true, icon = "adas"),
        e("idas_mode", "adas", EntityType.ADAS, "choice",
            optionKeys = listOf(
                "opt.idas_mode.1" to 1,
                "opt.idas_mode.2" to 2,
            ),
            acronym = "IDAS", icon = "adas", lastKnown = true,
        ),
        e("speed_limit_warn", "adas", EntityType.ADAS, "bool", lastKnown = true, icon = "adas"),
        e("speed_limit_max", "adas", EntityType.ADAS, "sensor",
            writable = false,
            deviceClass = DeviceClass.SPEED,
            unitOfMeasurement = UnitOfMeasurement.KM_PER_HOUR,
            icon = "adas", lastKnown = true,
        ),
        e("lane_change_warn", "adas", EntityType.ADAS, "bool", lastKnown = true, icon = "adas"),
        e("dms", "adas", EntityType.ADAS, "bool", acronym = "DMS", lastKnown = true, icon = "adas"),
        e("approach_unlock", "controls", EntityType.LOCK, "bool", lastKnown = true, icon = "lock"),
        e("away_lock", "controls", EntityType.LOCK, "bool", lastKnown = true, icon = "lock"),
        e("central_lock", "controls", EntityType.LOCK, "bool", icon = "lock"),
        e("audible_lock", "controls", EntityType.LOCK, "bool", icon = "lock"),
        e("keyless_unlock", "controls", EntityType.LOCK, "bool", lastKnown = true, icon = "lock"),
        e("twostep_unlock", "controls", EntityType.LOCK, "bool", lastKnown = true, icon = "lock"),
        e("p_gear_unlock", "controls", EntityType.LOCK, "bool", lastKnown = true, icon = "lock"),
        e("mirror_auto_fold", "controls", EntityType.EXTRA, "bool", lastKnown = true, icon = "cabin"),
        e("auto_close_window", "controls", EntityType.WINDOW, "bool", icon = "window"),
        e("easy_ingress", "controls", EntityType.SEAT, "bool", lastKnown = true, icon = "seat"),
        e("vehicle_locator_mode", "controls", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.vehicle_locator_mode.1" to 1,
                "opt.vehicle_locator_mode.2" to 2,
                "opt.vehicle_locator_mode.3" to 3,
            ),
            lastKnown = true, icon = "cabin",
        ),
        e("trunk_open_height", "controls", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.trunk_open_height.1" to 1,
                "opt.trunk_open_height.2" to 2,
                "opt.trunk_open_height.3" to 3,
                "opt.trunk_open_height.4" to 4,
                "opt.trunk_open_height.5" to 5,
            ),
            lastKnown = true, icon = "cabin",
        ),
        e("sunroof_tilt", "controls", EntityType.WINDOW, "bool", icon = "window"),
        e("courtesy_light", "lights", EntityType.LIGHT, "bool", icon = "light"),
        e("approach_light", "lights", EntityType.LIGHT, "bool", icon = "light"),
        e("exterior_light", "lights", EntityType.LIGHT, "choice",
            optionKeys = listOf(
                "opt.exterior_light.0" to 0,
                "opt.exterior_light.1" to 1,
                "opt.exterior_light.2" to 2,
                "opt.exterior_light.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        e("rear_fog", "lights", EntityType.LIGHT, "bool", icon = "light"),
        e("headlight_height", "lights", EntityType.LIGHT, "choice",
            optionKeys = listOf(
                "opt.headlight_height.0" to 0,
                "opt.headlight_height.1" to 1,
                "opt.headlight_height.2" to 2,
                "opt.headlight_height.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        e("home_safe_light", "lights", EntityType.LIGHT, "choice",
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
        e("day_mode", "lights", EntityType.LIGHT, "choice",
            optionKeys = listOf(
                "opt.day_mode.1" to 1,
                "opt.day_mode.2" to 2,
                "opt.day_mode.3" to 3,
            ),
            lastKnown = true, icon = "light",
        ),
        e("night_mode", "lights", EntityType.LIGHT, "bool", lastKnown = true, icon = "light"),
        e("ambience_main_color", "lights", EntityType.LIGHT, "choice",
            optionKeys = listOf(
                "opt.ambience_main_color.2" to 2,
                "opt.ambience_main_color.3" to 3,
                "opt.ambience_main_color.4" to 4,
            ),
            lastKnown = true, icon = "light",
        ),
        e("ambience_intensity", "lights", EntityType.LIGHT, "int",
            icon = "light", min = 0f, max = 100f, step = 1f, lastKnown = true,
        ),
        e("esm_volume", "sound", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.esm_volume.0" to 0,
                "opt.esm_volume.1" to 1,
                "opt.esm_volume.2" to 2,
                "opt.esm_volume.3" to 3,
            ),
            acronym = "AVAS", icon = "system", lastKnown = true,
        ),
        e("esm_sound", "sound", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.esm_sound.0" to 1,
                "opt.esm_sound.1" to 2,
                "opt.esm_sound.2" to 3,
            ),
            acronym = "AVAS", icon = "system", lastKnown = true,
        ),
        e("media_volume", "sound", EntityType.EXTRA, "int",
            icon = "sound", min = 0f, max = 39f, step = 1f, lastKnown = true,
        ),
        e("speed_volume", "sound", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.speed_volume.0" to 0,
                "opt.speed_volume.1" to 1,
                "opt.speed_volume.2" to 2,
                "opt.speed_volume.3" to 3,
            ),
            lastKnown = true, icon = "system",
        ),
        e("usb_mode", "vehicle", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.usb_mode.0" to 0,
                "opt.usb_mode.1" to 1,
                "opt.usb_mode.2" to 2,
            ),
            icon = "usb",
        ),
        e("hud_active", "display", EntityType.HUD, "bool", icon = "hud"),
        e("hud_snow", "display", EntityType.HUD, "bool", icon = "hud"),
        e("hud_ar", "display", EntityType.HUD, "bool", icon = "hud"),
        e("wheel_custom_key", "controls", EntityType.EXTRA, "choice",
            optionKeys = listOf(
                "opt.wheel_custom_key.0" to 0,
                "opt.wheel_custom_key.1" to 1,
                "opt.wheel_custom_key.2" to 4,
                "opt.wheel_custom_key.3" to 5,
                "opt.wheel_custom_key.4" to 7,
                "opt.wheel_custom_key.5" to 8,
                "opt.wheel_custom_key.drive" to 0x21111418,
            ),
            lastKnown = true, icon = "drive",
            deviceClass = DeviceClass.ENUM,
        ),
        e("vr_activated", "assistant", EntityType.EXTRA, "bool", lastKnown = true, icon = "system"),
    )

    private val byId: Map<String, EntityDef> = ALL.associateBy { it.id }

    private val byAlias: Map<String, EntityDef> = buildMap {
        for (def in ALL) {
            for (a in def.aliases) put(a, def)
        }
    }

    /** Binding key (platform.json `entity`) → product entity (atomic or composite). */
    private val byBindingKey: Map<String, EntityDef> = buildMap {
        for (def in ALL) {
            def.bindingKey?.let { putIfAbsent(it, def) }
            for (bk in def.attributes.values) putIfAbsent(bk, def)
        }
    }

    fun byId(id: String): EntityDef? = byId[id]

    /** Resolve product id or legacy alias (e.g. hvac_power → climate). */
    fun resolve(id: String): EntityDef? = byId[id] ?: byAlias[id]

    /**
     * Resolve a platform binding key to its product entity
     * (e.g. `hvac_temp_c` → climate, `drive_mode` → drive_mode).
     */
    fun resolveBinding(bindingKey: String): EntityDef? =
        byId[bindingKey] ?: byAlias[bindingKey] ?: byBindingKey[bindingKey]

    /** Attribute name for an alias on a composite, if any (hvac_temp → temperature). */
    fun aliasAttribute(aliasId: String): String? {
        val def = byAlias[aliasId] ?: return null
        if (!def.isComposite) return null
        // Prefer matching by catalog id conventions
        return when (aliasId) {
            "hvac_temp" -> "temperature"
            "hvac_fan" -> "fan_mode"
            "hvac_fan_direction" -> "fan_direction"
            "hvac_power" -> "power"
            "hvac_ac" -> "ac"
            "hvac_auto" -> "auto"
            "hvac_recirc" -> "recirc"
            "hvac_max_defrost" -> "max_defrost"
            "hvac_max_ac" -> "max_ac"
            "hvac_eco" -> "eco"
            "hvac_auto_dry" -> "auto_dry"
            "hvac_rapid_cool" -> "rapid_cool"
            "hvac_rapid_heat" -> "rapid_heat"
            else -> def.attributes.entries.firstOrNull { it.value == aliasId || it.value == aliasId + "_c" }?.key
        }
    }

    private fun e(
        id: String,
        group: String,
        domain: EntityType,
        input: String,
        bindingKey: String? = id,
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
    ) = EntityDef(
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
    )
}
