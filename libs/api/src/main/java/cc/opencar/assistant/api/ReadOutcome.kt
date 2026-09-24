package cc.opencar.assistant.api

/**
 * Result of a diagnostic VHAL/property read used by CatalogProbe.
 */
sealed class ReadOutcome {
    data class Ok(val value: PropertyValue?, val areaId: Int) : ReadOutcome()
    data class Denied(val permission: String?, val areaId: Int, val message: String? = null) : ReadOutcome()
    data class Failed(val message: String?, val areaId: Int) : ReadOutcome()
    data class Unavailable(val areaId: Int) : ReadOutcome()
}
