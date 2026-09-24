package cc.opencar.assistant.api

/**
 * Semantic measurement class, aligned with Home Assistant `device_class`.
 * Used for icon defaults, history charts, and future MQTT/HA discovery — not for display text.
 */
enum class DeviceClass(val id: String) {
    BATTERY("battery"),
    FUEL("fuel"),
    TEMPERATURE("temperature"),
    SPEED("speed"),
    DISTANCE("distance"),
    CURRENT("current"),
    VOLTAGE("voltage"),
    POWER("power"),
    ENERGY("energy"),
    DURATION("duration"),
    PRESSURE("pressure"),
    HUMIDITY("humidity"),
    ENUM("enum"),
    ;

    companion object {
        fun fromId(id: String?): DeviceClass? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
