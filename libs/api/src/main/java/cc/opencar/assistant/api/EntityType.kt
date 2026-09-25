package cc.opencar.assistant.api

/**
 * Product entity kinds used by the web shell to pick card templates.
 * Integrations map native props into these buckets; unknown controls use [EXTRA].
 */
enum class EntityType(val id: String) {
    SENSOR("sensor"),
    CLIMATE("climate"),
    DRIVE_MODE("drive_mode"),
    REGEN("regen"),
    STEERING("steering"),
    BRAKE("brake"),
    ENERGY("energy"),
    CHARGING("charging"),
    ADAS("adas"),
    LOCK("lock"),
    LIGHT("light"),
    HUD("hud"),
    SEAT("seat"),
    WINDOW("window"),
    ANDROID("android"),
    MEDIA_PLAYER("media_player"),
    DEVICE_TRACKER("device_tracker"),
    EXTRA("extra"),
    ;

    companion object {
        fun fromId(id: String?): EntityType =
            entries.firstOrNull { it.id == id } ?: EXTRA
    }
}
