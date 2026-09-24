package cc.opencar.assistant.api

/**
 * Canonical unit id (stable in APIs / MQTT), similar to HA `unit_of_measurement`
 * but keyed for i18n rather than baking in a locale-specific glyph.
 *
 * Platform/catalog values use a native unit (e.g. [LITER_PER_100KM]); the web UI
 * may convert to a user-preferred sibling in the same dimension for display.
 *
 * Display: resolve via `unit.<id>` in string packs; [symbol] is the fallback
 * (and the typical HA discovery string).
 */
enum class UnitOfMeasurement(
    val id: String,
    /** Neutral fallback / HA-style symbol when i18n is missing. */
    val symbol: String,
    /** Dimension for preference grouping / conversion. */
    val dimension: Dimension,
) {
    PERCENT("percent", "%", Dimension.RATIO),
    CELSIUS("celsius", "°C", Dimension.TEMPERATURE),
    FAHRENHEIT("fahrenheit", "°F", Dimension.TEMPERATURE),
    KILOMETER("km", "km", Dimension.DISTANCE),
    MILE("mi", "mi", Dimension.DISTANCE),
    METER("m", "m", Dimension.DISTANCE),
    KM_PER_HOUR("km_h", "km/h", Dimension.SPEED),
    MILES_PER_HOUR("mph", "mph", Dimension.SPEED),
    AMPERE("A", "A", Dimension.ELECTRIC_CURRENT),
    VOLT("V", "V", Dimension.ELECTRIC_POTENTIAL),
    WATT("W", "W", Dimension.POWER),
    KILOWATT_HOUR("kWh", "kWh", Dimension.ENERGY),
    KWH_PER_100KM("kwh_100km", "kWh/100km", Dimension.ENERGY_ECONOMY),
    KM_PER_KWH("km_kwh", "km/kWh", Dimension.ENERGY_ECONOMY),
    WH_PER_KM("wh_km", "Wh/km", Dimension.ENERGY_ECONOMY),
    LITER_PER_100KM("l_100km", "L/100km", Dimension.FUEL_ECONOMY),
    KM_PER_LITER("km_l", "km/L", Dimension.FUEL_ECONOMY),
    MPG_US("mpg", "mpg", Dimension.FUEL_ECONOMY),
    MPG_UK("mpg_uk", "mpg (UK)", Dimension.FUEL_ECONOMY),
    MINUTE("min", "min", Dimension.DURATION),
    ;

    fun i18nKey(): String = "unit.$id"

    enum class Dimension {
        RATIO,
        TEMPERATURE,
        DISTANCE,
        SPEED,
        ELECTRIC_CURRENT,
        ELECTRIC_POTENTIAL,
        POWER,
        ENERGY,
        ENERGY_ECONOMY,
        FUEL_ECONOMY,
        DURATION,
    }

    companion object {
        fun fromId(id: String?): UnitOfMeasurement? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.symbol == id
            }
        }
    }
}
