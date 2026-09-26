package cc.opencar.assistant.integrations.demo

import cc.opencar.assistant.integrations.aaos.PlatformConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DemoPlatformConfigTest {
    @Test
    fun demoPlatformExtendsAaos() {
        val demoJson = File("src/main/assets/platform.json")
        val skuJson = File("src/main/assets/models/default.json")
        val profileJson = File("src/main/assets/profiles/default.json")
        val aaosJson = File("../platform/aaos/src/main/assets/platform/aaos/platform.json")
        assertTrue(demoJson.isFile)
        assertTrue(skuJson.isFile)
        assertTrue(profileJson.isFile)
        assertTrue(aaosJson.isFile)
        val sku = PlatformConfig.parseSku(skuJson.readText())
        val profile = PlatformConfig.parseProfile(profileJson.readText())
        val cfg = PlatformConfig.parse(demoJson.readText()) { name ->
            require(name == "aaos")
            aaosJson.readText()
        }.copy(skus = listOf(sku), profiles = listOf(profile)).forSelection("default", "default")
        assertEquals("demo", cfg.id)
        assertEquals("demo", cfg.backend)
        assertTrue(cfg.bindings.containsKey("PERF_VEHICLE_SPEED"))
        assertTrue(cfg.bindings.containsKey("HVAC_POWER_ON"))
        assertEquals("PERF_VEHICLE_SPEED", cfg.bindings.keys.first { it == "PERF_VEHICLE_SPEED" })
        assertTrue(cfg.android.settings.any { it.entity == "switch.wifi" })
        assertTrue(0x15200510 in cfg.writableAllowlist)
    }
}
