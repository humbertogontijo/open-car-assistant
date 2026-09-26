package cc.opencar.assistant.api.plugin

import java.util.ServiceLoader

/**
 * Discovers [OaaPlugin] implementations via
 * `META-INF/services/cc.opencar.assistant.api.plugin.OaaPlugin`.
 */
class ServiceLoaderPluginRegistry(
    classLoader: ClassLoader,
) : PluginRegistry {
    private val plugins: List<OaaPlugin> =
        ServiceLoader.load(OaaPlugin::class.java, classLoader).toList()

    override fun all(): List<OaaPlugin> = plugins
}
