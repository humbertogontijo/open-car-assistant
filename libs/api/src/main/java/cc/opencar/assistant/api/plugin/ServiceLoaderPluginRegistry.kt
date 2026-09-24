package cc.opencar.assistant.api.plugin

import java.util.ServiceLoader

/**
 * Discovers [OcaPlugin] implementations via
 * `META-INF/services/cc.opencar.assistant.api.plugin.OcaPlugin`.
 */
class ServiceLoaderPluginRegistry(
    classLoader: ClassLoader,
) : PluginRegistry {
    private val plugins: List<OcaPlugin> =
        ServiceLoader.load(OcaPlugin::class.java, classLoader).toList()

    override fun all(): List<OcaPlugin> = plugins
}
