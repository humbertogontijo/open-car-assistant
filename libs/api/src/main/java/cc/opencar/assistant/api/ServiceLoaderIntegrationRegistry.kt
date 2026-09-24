package cc.opencar.assistant.api

import java.util.ServiceLoader

/**
 * Discovers [VehicleIntegration] implementations via
 * `META-INF/services/cc.opencar.assistant.api.VehicleIntegration`.
 */
class ServiceLoaderIntegrationRegistry(
    classLoader: ClassLoader,
) : IntegrationRegistry {
    private val integrations: List<VehicleIntegration> =
        ServiceLoader.load(VehicleIntegration::class.java, classLoader).toList()

    override fun all(): List<VehicleIntegration> = integrations

    override fun match(fingerprint: DeviceFingerprint): VehicleIntegration? =
        integrations.firstOrNull { it.matches(fingerprint) }
}
