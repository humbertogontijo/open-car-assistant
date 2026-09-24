package cc.opencar.assistant.integrations.antora1000

import cc.opencar.assistant.integrations.common.AospVehicleIds

/**
 * Native VHAL property IDs observed on Antora 1000 (SE1000) head units.
 * AOSP-overlapping IDs alias [AospVehicleIds]. Writable allowlist lives in
 * [platform.json] (`writableAllowlist`) and is enforced at runtime via [PlatformConfig].
 */
object AntoraVhalIds {
    const val INFO_VIN = 0x11100100
    const val INFO_MODEL = 0x11100102
    const val PARKING_BRAKE_ON = 0x11200402
    const val GEAR_SELECTION = AospVehicleIds.GEAR_SELECTION
    const val CURRENT_GEAR = AospVehicleIds.CURRENT_GEAR
    const val IGNITION_STATE = AospVehicleIds.IGNITION_STATE
    const val INFO_FUEL_CAPACITY = AospVehicleIds.INFO_FUEL_CAPACITY
    const val INFO_EV_BATTERY_CAPACITY = AospVehicleIds.INFO_EV_BATTERY_CAPACITY
    const val PERF_VEHICLE_SPEED = AospVehicleIds.PERF_VEHICLE_SPEED
    const val RANGE_REMAINING = AospVehicleIds.RANGE_REMAINING
    const val EV_BATTERY_LEVEL = AospVehicleIds.EV_BATTERY_LEVEL
    /** Vendor display SoC (%) — matches cluster / energy UI (not hybrid target band). */
    const val TYPE_EV_BATTERY_PERCENTAGE = 0x2160730e
    const val TYPE_FUEL_PERCENTAGE = 0x21607313
    const val SENSOR_TYPE_ENDURANCE_MILEAGE_EV = 0x216072da
    const val SENSOR_TYPE_ENDURANCE_MILEAGE_FUEL = 0x216072d9
    const val PERF_ODOMETER = 0x11600204
    const val SENSOR_TYPE_TEMPERATURE_AMBIENT = 0x216072cc
    const val SENSOR_TYPE_TEMPERATURE_INDOOR = 0x216072cd
    const val SENSOR_TYPE_EV_BATTERY_TEMP = 0x21607653
    const val CHARGE_FUNC_CHARGING_CURRENT_MAX = 0x21607273
    const val CHARGE_FUNC_CHARGING_ESTIMATED_TIME = 0x2160728d
    const val CHARGE_FUNC_CHARGING_ENERGY = 0x2160728f
    const val CHARGE_FUNC_CHARGING_WORK_CURRENT = 0x2160728c
    const val CHARGE_FUNC_CHARGING_WORK_VOLTAGE = 0x2160728b
    const val CHARGE_FUNC_DISCHARGING_SOC = 0x21607283
    const val TRIP_DI_AVG_ELC_CONSUMPTION = 0x2160765f
    const val TRIP_DI_AVG_FUEL_CONSUMPTION = 0x2160768a
    const val TRIP_ED_DRIVING_ENERGY_FLOW = 0x216074e9
    const val TRIP_ED_CLIMATE_ENERGY_FLOW = 0x216074ea
    const val TRIP_ED_BATTERY_ENERGY_FLOW = 0x216074eb
    const val TYPE_MAINTENANCE_MILEAGE = 0x2160730a
    const val TYPE_SINCE_MAINTENANCE_TOTAL_MILEAGE = 0x2160767b

    const val HVAC_AC_ON = AospVehicleIds.HVAC_AC_ON
    const val HVAC_MAX_AC_ON = AospVehicleIds.HVAC_MAX_AC_ON
    const val HVAC_MAX_DEFROST_ON = AospVehicleIds.HVAC_MAX_DEFROST_ON
    const val HVAC_RECIRC_ON = AospVehicleIds.HVAC_RECIRC_ON
    const val HVAC_AUTO_ON = AospVehicleIds.HVAC_AUTO_ON
    const val HVAC_POWER_ON = AospVehicleIds.HVAC_POWER_ON
    const val HVAC_FAN_SPEED = AospVehicleIds.HVAC_FAN_SPEED
    const val HVAC_FAN_DIRECTION = AospVehicleIds.HVAC_FAN_DIRECTION
    const val HVAC_SEAT_VENTILATION = AospVehicleIds.HVAC_SEAT_VENTILATION
    const val HVAC_TEMPERATURE_SET = AospVehicleIds.HVAC_TEMPERATURE_SET
    const val HVAC_ECO = 0x252070b0

    const val SETTING_FUNC_ESC_SPORT_MODE = 0x21207006
    const val SETTING_FUNC_LAMP_AUTOMATIC_COURTESY_LIGHT = 0x21207010
    const val SETTING_FUNC_LAMP_APPROACH_LIGHT = 0x21207013
    const val SETTING_FUNC_AUTO_HOLD = 0x21207018
    const val SETTING_FUNC_AUTO_CLOSE_WINDOW = 0x2120701f
    const val SETTING_FUNC_AUDIBLE_LOCKING_FEEDBACK = 0x2120703a
    const val SETTING_FUNC_CENTRAL_LOCK = 0x21207040
    const val SETTING_FUNC_HUD_ACTIVE = 0x21207042
    const val SETTING_FUNC_ENERGY_REGENERATION = 0x21407008
    const val SETTING_FUNC_AMBIENCE_MAIN_COLOR = 0x21407027
    const val SETTING_FUNC_ESM_VOLUME = 0x21407053
    const val DM_FUNC_DRIVE_MODE_SELECT = 0x214070f9
    const val DRIVE_MODE_SELECTION_PURE = 0x2120733b
    const val DRIVE_MODE_SELECTION_HYBRID = 0x2120733c
    const val DRIVE_MODE_SELECTION_POWER = 0x2120733d
    const val SETTING_FUNC_CST = 0x212073a2
    const val HYBRID_FUNC_BATTERY_SAVE_MODE = 0x21207174
    const val HYBRID_FUNC_BATTERY_CHARGE_MODE = 0x21207175
    const val HYBRID_FUNC_BATTERY_SOC = 0x21607176
    const val HYBRID_FUNC_BATTERY_MODE = 0x214075b4
    const val SETTING_FUNC_HDC_SWITCH = 0x2120717c
    const val SETTING_FUNC_LANE_KEEPING_AID = 0x2120717d
    const val SETTING_FUNC_LANE_CHANGE_WARN = 0x21407181
    const val SETTING_FUNC_FORWARD_COLLISION_WARN_SNVTY = 0x21407191
    const val SETTING_FUNC_SPEED_LIMIT_WARN = 0x2140719c
    const val SETTING_FUNC_EMGY_LANE_KEEP_AID = 0x21207183
    const val SETTING_FUNC_REAR_CROSS_TRAFFIC_ALERT = 0x21207187
    const val SETTING_FUNC_AUTONOMOUS_EMERGENCY_BRAKING = 0x2120718b
    const val SETTING_FUNC_REAR_COLLISION_WARNING = 0x2120718d
    const val SETTING_FUNC_DAY_MODE = 0x214071e8
    const val SETTING_FUNC_HUD_SNOW_MODE = 0x2120720d
    const val SETTING_FUNC_HUD_AR_ENGINE = 0x2120720e
    const val SETTING_FUNC_APPROACH_UNLOCK = 0x21207253
    const val SETTING_FUNC_AWAY_LOCK = 0x21207255
    const val CHARGE_FUNC_DISCHARGING_SWITCH_V2V = 0x21207281
    const val CHARGE_FUNC_DISCHARGING_SWITCH_V2L = 0x21207282
    const val CHARGE_FUNC_SOC_MAX = 0x2160726f
    const val CHARGE_FUNC_SOC_MIN = 0x21607270
    const val CHARGE_FUNC_CHARGING_CURRENT = 0x21607272
    const val CHARGE_FUNC_CHARGING_PLUG_STATE = 0x21407289
    const val CHARGE_FUNC_PARKING = 0x21207359
    const val SETTING_FUNC_SPEED_LIMIT_MAX = 0x21407427
    const val SETTING_FUNC_STEERING_ASSISTANCE_LEVEL_HEAVY = 0x2120748d
    const val SETTING_FUNC_STEERING_ASSISTANCE_LEVEL_MEDIUM = 0x2120748e
    const val SETTING_FUNC_STEERING_ASSISTANCE_LEVEL_SOFT = 0x2120748f
    const val SETTING_BRAKE_PEDAL_STATUS = 0x21407502
    const val SETTING_FUNC_DMS = 0x21207505
    const val SETTING_FUNC_ESM_SOUND = 0x21407622
    const val SETTING_FUNC_PARKING_COMFORT = 0x21207626
    const val SETTING_FUNC_NIGHT_MODE = 0x2120764a
    const val SETTING_FUNC_USB_SWITCH = 0x21407628
    const val SETTING_FUNC_AMBIENCE_INTENSITY = 0x254071c3
    const val SUNROOF_TILT = 0x23207156

    const val BCM_FUNC_CUSTOM_KEY = 0x21407171
    /**
     * Catalog of known custom-key *capabilities* (separate VHAL props).
     * [BCM_FUNC_CUSTOM_KEY] stores small ints = (TYPE_id - [CUSTOM_KEY_TYPE_NONE]),
     * plus at least one firmware-specific outlier (e.g. driving settings = 0x21111418).
     */
    const val CUSTOM_KEY_TYPE_NONE = 0x2140752d
    const val CUSTOM_KEY_TYPE_360_PANORAMA = 0x2140752e // → enum 1
    const val CUSTOM_KEY_TYPE_UNLOCK_TRUNK = 0x21407531 // → enum 4
    const val CUSTOM_KEY_TYPE_CHANGE_DVRMOD = 0x21407532 // → enum 5
    const val CUSTOM_KEY_TYPE_SOUND_SWITCH = 0x21407534 // → enum 7 (OEM: media source)
    const val CUSTOM_KEY_TYPE_REAR_MIRROR_ADJUST = 0x21407535 // → enum 8
    /** Observed on Antora: OEM “driving settings” selection (not in CUSTOM_KEY_TYPE_*). */
    const val CUSTOM_KEY_DRIVING_SETTINGS = 0x21111418
    const val WHEEL_HARD_KEY_TOP = 0x21407432
    const val WHEEL_HARD_KEY_LEFT = 0x21407433
    const val WHEEL_HARD_KEY_RIGHT = 0x21407434
    const val WHEEL_HARD_KEY_BOTTOM = 0x21407435
    const val WHEEL_HARD_KEY_VR = 0x21407436
    const val WHEEL_HARD_KEY_CUSTOM = 0x21407437
    const val WHEEL_HARD_KEY_MENU = 0x21407438
    const val WHEEL_HARD_KEY_CONFIRM = 0x21407439
    const val WHEEL_HARD_KEY_MUTE = 0x21407565

    const val AREA_GLOBAL = AospVehicleIds.AREA_GLOBAL
    const val AREA_HVAC_PRIMARY = AospVehicleIds.AREA_HVAC_ROW1_ALL
    const val AREA_HVAC_ZONE = AospVehicleIds.AREA_HVAC_ZONE
    const val AREA_TEMP = AospVehicleIds.AREA_HVAC_ROW1_LEFT
    const val AREA_SUNROOF = 65536

    /** Logical key name → VHAL id for SWC hard-key press monitoring. */
    val WHEEL_HARD_KEYS: Map<String, Int> = mapOf(
        "top" to WHEEL_HARD_KEY_TOP,
        "left" to WHEEL_HARD_KEY_LEFT,
        "right" to WHEEL_HARD_KEY_RIGHT,
        "bottom" to WHEEL_HARD_KEY_BOTTOM,
        "vr" to WHEEL_HARD_KEY_VR,
        "custom" to WHEEL_HARD_KEY_CUSTOM,
        "menu" to WHEEL_HARD_KEY_MENU,
        "confirm" to WHEEL_HARD_KEY_CONFIRM,
        "mute" to WHEEL_HARD_KEY_MUTE,
    )
}
