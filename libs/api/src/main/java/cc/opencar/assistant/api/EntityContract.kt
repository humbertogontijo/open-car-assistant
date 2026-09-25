package cc.opencar.assistant.api

/**
 * Home Assistant–inspired entity contract for the product surface.
 *
 * Integrations bind [WellKnownProperties] in `platform.json`; the curated
 * [ControlCatalog] (feature-web) exposes stable catalog **entity ids** used by
 * UI, history, shortcuts, scenes, and routines. Features never hardcode VHAL hex.
 *
 * JSON maps from `/api/entities` (and virtual shortcut cards) include:
 *
 * | Field | Role |
 * |-------|------|
 * | `id` | Stable catalog id (`sensor_soc`, `hvac_temp`, …) — public contract |
 * | `domain` | Same as [EntityType.id] (`sensor`, `climate`, `lock`, …) |
 * | `entity` | Alias of `domain` (legacy UI field) |
 * | `state` / `value` | Current state string (HA `state` / OCA `value`) |
 * | `friendlyName` / `label` | Localized display name |
 * | `available` | `true` when `status` is `ok` or `cached` |
 * | `status` | `ok` \| `cached` \| `denied` \| `unavailable` \| `failed` |
 * | `deviceClass` | HA-aligned [DeviceClass.id] |
 * | `unitOfMeasurement` | HA-aligned [UnitOfMeasurement.id] |
 * | `attributes` | Nested map of semantic extras (device_class, unit, …) |
 * | `group` | OEM nav section id (`home`, `energy`, `controls`, …) |
 * | `input` | Card widget type (`bool`, `choice`, `sensor`, …) |
 *
 * **Availability:** an entity appears only when the platform binds its property
 * (and capability gates allow the section). Unbound = not created — same idea as
 * HA “integration didn’t register the entity.”
 *
 * **Portability:** shortcuts / scenes / routines must reference catalog `id`s only.
 * Builtin scenes (e.g. Sentinel) skip missing targets at runtime.
 *
 * MQTT / HA outbound discovery may publish these fields later; inbound HA bridge
 * remains [plugins/homeassistant].
 */
object EntityContract {
    const val FIELD_ID = "id"
    const val FIELD_DOMAIN = "domain"
    const val FIELD_ENTITY = "entity"
    const val FIELD_STATE = "state"
    const val FIELD_VALUE = "value"
    const val FIELD_FRIENDLY_NAME = "friendlyName"
    const val FIELD_LABEL = "label"
    const val FIELD_AVAILABLE = "available"
    const val FIELD_STATUS = "status"
    const val FIELD_ATTRIBUTES = "attributes"
    const val FIELD_DEVICE_CLASS = "deviceClass"
    const val FIELD_UNIT = "unitOfMeasurement"
    const val FIELD_GROUP = "group"

    /** Status values treated as “entity exists and has a usable reading.” */
    fun isAvailable(status: String?): Boolean =
        status == "ok" || status == "cached"

    /**
     * Adds HA-shaped aliases (`domain`, `state`, `friendlyName`, `available`,
     * `attributes`) without removing legacy keys used by the web shell.
     */
    fun enrich(row: Map<String, Any?>): Map<String, Any?> {
        val status = row[FIELD_STATUS] as? String
        val label = row[FIELD_LABEL] as? String
        val domain = (row[FIELD_DOMAIN] as? String)
            ?: (row[FIELD_ENTITY] as? String)
            ?: EntityType.EXTRA.id
        val value = row[FIELD_VALUE]
        val attributes = linkedMapOf<String, Any?>().apply {
            put("friendly_name", label)
            put("device_class", row[FIELD_DEVICE_CLASS])
            put("unit_of_measurement", row[FIELD_UNIT])
            put("icon", row["icon"])
            put("group", row[FIELD_GROUP])
            put("input", row["input"])
            put("stale", row["stale"])
            put("history", row["history"])
            put("writable", row["writable"])
            if (row["virtual"] == true) {
                put("virtual", true)
                put("virtual_kind", row["virtualKind"])
            }
        }.filterValues { it != null }
        return row + mapOf(
            FIELD_DOMAIN to domain,
            FIELD_ENTITY to (row[FIELD_ENTITY] ?: domain),
            FIELD_STATE to value,
            FIELD_FRIENDLY_NAME to label,
            FIELD_AVAILABLE to isAvailable(status),
            FIELD_ATTRIBUTES to attributes,
        )
    }
}
