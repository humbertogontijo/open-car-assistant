package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import cc.opencar.assistant.api.Capability
import cc.opencar.assistant.api.DeviceFingerprint
import cc.opencar.assistant.api.PlatformVariant
import cc.opencar.assistant.api.QuickEntry
import cc.opencar.assistant.api.VehicleIntegration
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.WakeSignals
import cc.opencar.assistant.api.WellKnownProperties
import cc.opencar.assistant.integrations.common.PlatformConfig
import cc.opencar.assistant.integrations.platform.flyme.FlymePlatform
import kotlinx.coroutines.runBlocking

class Antora1000Integration : VehicleIntegration {
    override val id: String = ID
    override val displayName: String = "Antora 1000 (SE1000)"

    @Volatile private var config: PlatformConfig? = null

    private fun config(context: Context): PlatformConfig {
        config?.let { return it }
        return AntoraCatalog.platformConfig(context).also { config = it }
    }

    override fun matches(device: DeviceFingerprint): Boolean {
        val needles = config?.match ?: listOf("antora1000", "se1000", "antora")
        return device.containsAny(*needles.toTypedArray())
    }

    override fun warm(context: Context) {
        config(context)
    }

    override fun createQuickEntry(): QuickEntry = FlymePlatform.createStatusBarQuickEntry()

    override fun wakeSignals(): WakeSignals = FlymePlatform.wakeSignals()

    override fun detectVariant(session: VehicleSession): PlatformVariant {
        if (session is AntoraVehicleSession) {
            val fuelCap = runBlocking {
                session.get(WellKnownProperties.FUEL_CAPACITY)?.asFloat()
            }
            val hybridSoc = runBlocking {
                session.get(WellKnownProperties.HYBRID_SOC)?.asFloat()
            }
            return when {
                (fuelCap != null && fuelCap > 0f) || hybridSoc != null -> VARIANT_PHEV
                else -> VARIANT_BEV
            }
        }
        return VARIANT_DEFAULT
    }

    override fun capabilities(variant: PlatformVariant): Set<Capability> {
        val base = config?.capabilities ?: setOf(
            Capability.READ_TELEMETRY,
            Capability.WRITE_SETTINGS,
            Capability.DRIVE_MODES,
            Capability.HVAC,
            Capability.CHARGING,
            Capability.ADAS_TOGGLES,
            Capability.LOCK_PROXIMITY,
            Capability.WINDOWS_SUNROOF,
            Capability.CAMERAS_DVR,
            Capability.GEAR_EVENTS,
            Capability.IGNITION_EVENTS,
        )
        return base + variant.extraCapabilities
    }

    override suspend fun connect(context: Context): VehicleSession {
        warm(context)
        val session = AntoraVehicleSession(context, VARIANT_DEFAULT)
        val variant = detectVariant(session)
        session.updateVariant(variant)
        return session
    }

    companion object {
        const val ID = "antora1000"

        val VARIANT_PHEV = PlatformVariant(
            id = "phev",
            label = "PHEV / EM-i energy modes",
            extraCapabilities = setOf(Capability.HYBRID_ENERGY),
        )
        val VARIANT_BEV = PlatformVariant(
            id = "bev",
            label = "Battery electric",
            extraCapabilities = emptySet(),
        )
        val VARIANT_DEFAULT = PlatformVariant(
            id = "default",
            label = "Antora default",
            extraCapabilities = emptySet(),
        )
    }
}
