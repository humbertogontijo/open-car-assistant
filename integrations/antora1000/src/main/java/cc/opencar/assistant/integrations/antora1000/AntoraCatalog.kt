package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.integrations.common.PlatformConfig
import cc.opencar.assistant.integrations.common.loadCatalogTsv
import cc.opencar.assistant.integrations.common.wellKnownByKey

object AntoraCatalog {
    @Volatile
    private var cachedConfig: PlatformConfig? = null

    fun platformConfig(context: Context): PlatformConfig {
        cachedConfig?.let { return it }
        return PlatformConfig.load(context).also { cachedConfig = it }
    }

    fun loadFromAssets(context: Context): List<CatalogEntry> {
        val config = platformConfig(context)
        val allow = config.writableAllowlist
        return loadCatalogTsv(context).map { (id, name) ->
            val propId = id.toInt()
            val areas = areasFor(propId)
            CatalogEntry(
                property = VehicleProperty(
                    namespace = "vhal",
                    key = name,
                    nativeId = id,
                    defaultAreaId = areas.first(),
                ),
                name = name,
                writable = propId in allow,
                areaIds = areas,
            )
        }
    }

    fun wellKnownBindings(context: Context): Map<VehicleProperty, Pair<Int, Int>> {
        val config = platformConfig(context)
        val out = mutableMapOf<VehicleProperty, Pair<Int, Int>>()
        for ((key, binding) in config.bindings) {
            val prop = wellKnownByKey(key) ?: continue
            out[prop] = binding.nativeId to binding.areaId
        }
        return out
    }

    fun writableAllowlist(context: Context): Set<Int> = platformConfig(context).writableAllowlist

    private fun areasFor(propId: Int): List<Int> = when (propId) {
        AntoraVhalIds.HVAC_POWER_ON, AntoraVhalIds.HVAC_FAN_SPEED ->
            listOf(AntoraVhalIds.AREA_HVAC_PRIMARY, AntoraVhalIds.AREA_HVAC_ZONE)
        AntoraVhalIds.HVAC_FAN_DIRECTION ->
            listOf(AntoraVhalIds.AREA_TEMP, AntoraVhalIds.AREA_HVAC_PRIMARY, AntoraVhalIds.AREA_HVAC_ZONE)
        AntoraVhalIds.HVAC_AC_ON, AntoraVhalIds.HVAC_RECIRC_ON,
        AntoraVhalIds.HVAC_MAX_DEFROST_ON, AntoraVhalIds.HVAC_MAX_AC_ON, AntoraVhalIds.HVAC_ECO,
        -> listOf(AntoraVhalIds.AREA_HVAC_ZONE, AntoraVhalIds.AREA_HVAC_PRIMARY, AntoraVhalIds.AREA_TEMP)
        AntoraVhalIds.HVAC_AUTO_ON, AntoraVhalIds.HVAC_SEAT_VENTILATION ->
            listOf(AntoraVhalIds.AREA_TEMP, AntoraVhalIds.AREA_HVAC_ZONE, AntoraVhalIds.AREA_HVAC_PRIMARY)
        AntoraVhalIds.HVAC_TEMPERATURE_SET -> listOf(AntoraVhalIds.AREA_TEMP)
        AntoraVhalIds.SETTING_FUNC_AMBIENCE_INTENSITY -> listOf(AntoraVhalIds.AREA_HVAC_PRIMARY, AntoraVhalIds.AREA_GLOBAL)
        AntoraVhalIds.SUNROOF_TILT -> listOf(AntoraVhalIds.AREA_SUNROOF, AntoraVhalIds.AREA_GLOBAL)
        else -> listOf(AntoraVhalIds.AREA_GLOBAL)
    }
}
