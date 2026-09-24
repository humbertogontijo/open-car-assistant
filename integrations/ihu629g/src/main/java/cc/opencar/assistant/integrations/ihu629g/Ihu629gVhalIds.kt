package cc.opencar.assistant.integrations.ihu629g

import cc.opencar.assistant.integrations.common.AospVehicleIds

/**
 * VHAL IDs for Geely EX2 / Geometry E IHU629G (CarPropertyManager path — not AdaptAPI).
 */
object Ihu629gVhalIds {
    const val PROP_DRIVE = 570491136
    const val PROP_REGEN = 537003264
    const val DRIVE_ECO = 570491137
    const val DRIVE_COMFORT = 570491138
    const val DRIVE_SPORT = 570491139
    const val REGEN_LOW = 537003265
    const val REGEN_MID = 537003266
    const val REGEN_HIGH = 537003267

    const val GEAR_SELECTION = AospVehicleIds.CURRENT_GEAR // platform.json binds CURRENT_GEAR
    const val CURRENT_GEAR = AospVehicleIds.CURRENT_GEAR
    const val PERF_VEHICLE_SPEED = AospVehicleIds.PERF_VEHICLE_SPEED
    const val IGNITION_STATE = AospVehicleIds.IGNITION_STATE
    const val BATTERY_SOC = 557885165
    const val RANGE = 289407752
    const val PORT_CONNECTED = 557887621
    const val CHARGE_LIMIT = 605029888
    const val CHARGE_CURRENT = 605291008
    const val CHARGE_VOLTAGE = 605290752
    const val CHARGE_SWITCH = 605028608

    /** charge_switch enum values (not VHAL property IDs). */
    object ChargeSwitchValues {
        const val STOP = 609
        const val RESTART = 610
        const val START_NOW = 611
    }

    const val HVAC_POWER_ON = AospVehicleIds.HVAC_POWER_ON
    const val HVAC_AC_ON = AospVehicleIds.HVAC_AC_ON
    const val HVAC_TEMP = AospVehicleIds.HVAC_TEMPERATURE_SET
    const val HVAC_FAN = AospVehicleIds.HVAC_FAN_SPEED
    const val HVAC_FAN_DIRECTION = 557846560
    const val HVAC_RECIRC = AospVehicleIds.HVAC_RECIRC_ON
    /** Accepts writes with no effect on EX2 — not on writable allowlist. */
    const val HVAC_AUTO_ON = AospVehicleIds.HVAC_AUTO_ON

    const val PARK_MODE = 557885463
    const val PARK_ON_BASE = 0x201B0100
    const val PARK_DURATION_UNLIMITED = 0x13

    const val AMBIENT_COLOR = 537528576
    const val AMBIENT_BRIGHTNESS = 704708864

    const val AREA_GLOBAL = AospVehicleIds.AREA_GLOBAL
    const val AREA_TEMP = AospVehicleIds.AREA_HVAC_ROW1_LEFT
    const val AREA_FAN = AospVehicleIds.AREA_HVAC_ZONE
    const val AREA_AMBIENT = AospVehicleIds.AREA_HVAC_ROW1_ALL
}

val IHU629G_WRITABLE_ALLOWLIST: Set<Int> = setOf(
    Ihu629gVhalIds.PROP_DRIVE,
    Ihu629gVhalIds.PROP_REGEN,
    Ihu629gVhalIds.CHARGE_LIMIT,
    Ihu629gVhalIds.CHARGE_SWITCH,
    Ihu629gVhalIds.HVAC_POWER_ON,
    Ihu629gVhalIds.HVAC_AC_ON,
    Ihu629gVhalIds.HVAC_TEMP,
    Ihu629gVhalIds.HVAC_FAN,
    Ihu629gVhalIds.HVAC_FAN_DIRECTION,
    Ihu629gVhalIds.HVAC_RECIRC,
    Ihu629gVhalIds.PARK_MODE,
    Ihu629gVhalIds.AMBIENT_COLOR,
    Ihu629gVhalIds.AMBIENT_BRIGHTNESS,
)
