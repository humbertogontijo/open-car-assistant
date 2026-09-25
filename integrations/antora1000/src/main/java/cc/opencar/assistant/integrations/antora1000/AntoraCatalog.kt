package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.api.VehicleProperty
import cc.opencar.assistant.integrations.common.PlatformConfig
import cc.opencar.assistant.integrations.common.wellKnownByKey

object AntoraCatalog {
    @Volatile
    private var cachedConfig: PlatformConfig? = null

    fun platformConfig(context: Context): PlatformConfig {
        cachedConfig?.let { return it }
        return PlatformConfig.load(context).also { cachedConfig = it }
    }

    fun loadFromAssets(context: Context): List<CatalogEntry> =
        platformConfig(context).catalogEntries()

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
}
