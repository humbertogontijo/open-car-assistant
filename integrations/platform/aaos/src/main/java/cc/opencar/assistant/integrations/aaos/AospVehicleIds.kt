package cc.opencar.assistant.integrations.aaos

/**
 * True AOSP / AAOS [android.car.VehiclePropertyIds]-family constants confirmed on
 * multiple platforms (including Geely Flyme Auto EX2 and EX5).
 *
 * Vendor / adaptation-layer IDs stay in per-integration modules.
 */
object AospVehicleIds {
    const val PERF_VEHICLE_SPEED = 0x11600207
    const val GEAR_SELECTION = 0x11400400
    const val CURRENT_GEAR = 0x11400401
    const val IGNITION_STATE = 0x11400409
    const val INFO_EV_BATTERY_CAPACITY = 0x11600106
    const val EV_BATTERY_LEVEL = 0x11600309
    const val RANGE_REMAINING = 0x11600308
    const val INFO_FUEL_CAPACITY = 0x11600104

    const val HVAC_AC_ON = 0x15200505
    const val HVAC_MAX_AC_ON = 0x15200506
    const val HVAC_MAX_DEFROST_ON = 0x15200507
    const val HVAC_RECIRC_ON = 0x15200508
    const val HVAC_AUTO_ON = 0x1520050a
    const val HVAC_POWER_ON = 0x15200510
    const val HVAC_FAN_SPEED = 0x15400500
    const val HVAC_FAN_DIRECTION = 0x15400501
    const val HVAC_SEAT_VENTILATION = 0x15400513
    const val HVAC_TEMPERATURE_SET = 0x15600503

    const val AREA_GLOBAL = 0
    const val AREA_HVAC_ROW1_LEFT = 1
    const val AREA_HVAC_ROW1_RIGHT = 4
    const val AREA_HVAC_ROW1_ALL = 5
    const val AREA_HVAC_ZONE = 0x75

    /** AOSP PERF_VEHICLE_SPEED is m/s — convert for telemetry displays. */
    fun speedMsToKmh(ms: Float): Float = ms * 3.6f
}
