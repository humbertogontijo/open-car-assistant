package cc.opencar.assistant.integrations.antora1000

import android.content.Context
import android.os.Build
import cc.opencar.assistant.api.Capability
import cc.opencar.assistant.api.DeviceFingerprint
import cc.opencar.assistant.api.PlatformVariant
import cc.opencar.assistant.api.QuickEntry
import cc.opencar.assistant.api.VehicleIntegration
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.WakeSignals
import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.integrations.aaos.PlatformConfig
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
        val cfg = config
        val sku = cfg?.matchSku(systemFingerprint())
        val profileId = detectProfileId(session)
        val profile = cfg?.profileOrNull(profileId)
            ?: PlatformConfig.ProfileDef(
                id = profileId,
                label = when (profileId) {
                    "phev" -> "PHEV / EM-i"
                    "bev" -> "Battery electric"
                    else -> "Antora default"
                },
                extraCapabilities = if (profileId == "phev") setOf(Capability.HYBRID_ENERGY) else emptySet(),
            )
        return cfg?.selectionVariant(sku, profile)
            ?: PlatformVariant(
                id = profile.id,
                label = listOfNotNull(sku?.label, profile.label).joinToString(" · "),
                extraCapabilities = profile.extraCapabilities,
                skuId = sku?.id,
            )
    }

    private fun detectProfileId(session: VehicleSession): String {
        if (session !is AntoraVehicleSession) return "default"
        val fuelCap = runBlocking {
            session.get(EntityRegistry.property("INFO_FUEL_CAPACITY"))?.asFloat()
        }
        val hybridSoc = runBlocking {
            session.get(EntityRegistry.property("HYBRID_FUNC_BATTERY_SOC"))?.asFloat()
        }
        return when {
            (fuelCap != null && fuelCap > 0f) || hybridSoc != null -> "phev"
            else -> "bev"
        }
    }

    override fun capabilities(variant: PlatformVariant): Set<Capability> {
        val cfg = config
        val base = cfg?.capabilities ?: setOf(
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
        val profileExtra = cfg?.profileOrNull(variant.id)?.extraCapabilities.orEmpty()
        return base + variant.extraCapabilities + profileExtra
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

        val VARIANT_DEFAULT = PlatformVariant(
            id = "default",
            label = "Antora default",
            extraCapabilities = emptySet(),
        )

        fun systemFingerprint(): DeviceFingerprint =
            DeviceFingerprint(
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                hardware = Build.HARDWARE.orEmpty(),
                manufacturer = Build.MANUFACTURER.orEmpty(),
                fingerprint = Build.FINGERPRINT.orEmpty(),
                brand = Build.BRAND.orEmpty(),
            )
    }
}
