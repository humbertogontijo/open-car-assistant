package cc.opencar.assistant.api

/**
 * Canonical unit id (stable in APIs / MQTT), similar to HA `unit_of_measurement`
 * but keyed for i18n rather than baking in a locale-specific glyph.
 *
 * Display: resolve via `unit.<id>` in string packs; [symbol] is the fallback
 * (and the typical HA discovery string).
 */
enum class UnitOfMeasurement(
    val id: String,
    /** Neutral fallback / HA-style symbol when i18n is missing. */
    val symbol: String,
) {
    PERCENT("percent", "%"),
    CELSIUS("celsius", "°C"),
    FAHRENHEIT("fahrenheit", "°F"),
    KILOMETER("km", "km"),
    METER("m", "m"),
    KM_PER_HOUR("km_h", "km/h"),
    MILES_PER_HOUR("mph", "mph"),
    AMPERE("A", "A"),
    VOLT("V", "V"),
    WATT("W", "W"),
    KILOWATT_HOUR("kWh", "kWh"),
    KWH_PER_100KM("kwh_100km", "kWh/100km"),
    LITER_PER_100KM("l_100km", "L/100km"),
    MINUTE("min", "min"),
    ;

    fun i18nKey(): String = "unit.$id"

    companion object {
        fun fromId(id: String?): UnitOfMeasurement? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.symbol == id
            }
        }
    }
}
