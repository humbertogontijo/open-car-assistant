package cc.opencar.assistant.api

/**
 * Product entity domains used by the web shell to pick card templates.
 *
 * Entity ids follow Home Assistant shape: `domain.object_id`
 * (e.g. `cover.window_driver`, `switch.mirror_fold`, `climate.cabin`).
 * Legacy bare / pre-cover ids resolve via [fromId] and [EntityRegistry] aliases.
 */
enum class EntityType(val id: String) {
    // --- Composites (multi-property) ---
    CLIMATE("climate"),
    MEDIA_PLAYER("media_player"),
    CHARGER("charger"),
    EV_BATTERY("ev_battery"),
    HUD("hud"),
    LIGHT("light"),
    DRIVETRAIN("drivetrain"),
    CHASSIS("chassis"),
    STEERING("steering"),
    CAMERA("camera"),

    // --- Cover / fan / lock ---
    COVER("cover"),
    FAN("fan"),
    LOCK("lock"),

    // --- Atomic widgets ---
    SWITCH("switch"),
    SELECT("select"),
    NUMBER("number"),
    SENSOR("sensor"),

    // --- Platform / virtual ---
    ANDROID("android"),
    DEVICE_TRACKER("device_tracker"),
    EXTRA("extra"),
    ;

    companion object {
        fun fromId(id: String?): EntityType {
            if (id.isNullOrBlank()) return EXTRA
            entries.firstOrNull { it.id == id }?.let { return it }
            // Legacy HA / pre-cover / pre-composite ids
            return when (id) {
                "drive_mode", "regen", "energy" -> DRIVETRAIN
                "brake" -> CHASSIS
                "charging" -> CHARGER
                "ambient_light" -> LIGHT
                "adas", "seat" -> SWITCH
                "window", "trunk", "hood", "sunroof", "door", "mirror" -> COVER
                else -> EXTRA
            }
        }
    }
}
