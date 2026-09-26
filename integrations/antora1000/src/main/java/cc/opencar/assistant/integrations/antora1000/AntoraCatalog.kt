package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import cc.opencar.assistant.api.CatalogEntry
import cc.opencar.assistant.integrations.aaos.PlatformConfig

object AntoraCatalog {
    @Volatile
    private var cachedConfig: PlatformConfig? = null

    /** Base platform + SKUs/profiles (default selection until [PlatformConfig.forSelection]). */
    fun platformConfig(context: Context): PlatformConfig {
        cachedConfig?.let { return it }
        return PlatformConfig.load(context).also { cachedConfig = it }
    }

    fun loadFromAssets(context: Context): List<CatalogEntry> =
        platformConfig(context).catalogEntries()
}
