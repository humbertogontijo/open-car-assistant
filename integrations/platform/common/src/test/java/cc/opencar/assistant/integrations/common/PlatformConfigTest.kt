package cc.opencar.assistant.integrations.common

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlatformConfigTest {
    @Test
    fun parseAntoraPlatformJson() {
        val json = File("../../antora1000/src/main/assets/platform.json")
        assertTrue("expected ${json.absolutePath}", json.isFile)
        val cfg = PlatformConfig.parse(json.readText())
        assertEquals("antora1000", cfg.id)
        assertEquals("vhal", cfg.backend)
        assertTrue(cfg.match.isNotEmpty())
        assertTrue(cfg.bindings.containsKey("speed_kmh"))
        assertTrue(cfg.writableAllowlist.isNotEmpty())
        assertEquals(0x11600207, cfg.bindings.getValue("speed_kmh").nativeId)
    }

    @Test
    fun parseIhu629gPlatformJson() {
        val json = File("../../ihu629g/src/main/assets/platform.json")
        assertTrue("expected ${json.absolutePath}", json.isFile)
        val cfg = PlatformConfig.parse(json.readText())
        assertEquals("ihu629g", cfg.id)
        assertEquals("vhal", cfg.backend)
        assertTrue(cfg.writableAllowlist.isNotEmpty())
        assertTrue(cfg.bindings.containsKey("drive_mode"))
        assertEquals(570491136, cfg.bindings.getValue("drive_mode").nativeId)
        assertTrue(cfg.driveModeEnum.containsKey(570491137))
        assertTrue(605029888 in cfg.writableAllowlist)
    }

    @Test
    fun parseHexAllowlist() {
        val cfg = PlatformConfig.parse(
            """
            {
              "id": "t",
              "displayName": "T",
              "backend": "vhal",
              "match": ["t"],
              "capabilities": ["READ_TELEMETRY"],
              "variants": [],
              "bindings": {
                "gear": { "nativeId": "0x11400400", "areaId": 0 }
              },
              "writableAllowlist": ["0x15200510", 42]
            }
            """.trimIndent(),
        )
        assertEquals(0x11400400, cfg.bindings.getValue("gear").nativeId)
        assertTrue(0x15200510 in cfg.writableAllowlist)
        assertTrue(42 in cfg.writableAllowlist)
    }
}

class I18nKeyParityTest {
    @Test
    fun commonEnAndPtBrShareSameStringKeys() {
        val root = File("../../../support/src/main/assets/i18n/common")
        val en = loadStringKeys(File(root, "en.json"))
        val pt = loadStringKeys(File(root, "pt-BR.json"))
        val missingInPt = en - pt
        val missingInEn = pt - en
        assertTrue("Missing in pt-BR: $missingInPt", missingInPt.isEmpty())
        assertTrue("Missing in en: $missingInEn", missingInEn.isEmpty())
    }

    private fun loadStringKeys(file: File): Set<String> {
        assertTrue("expected ${file.absolutePath}", file.isFile)
        val root = JSONObject(file.readText())
        val strings = root.getJSONObject("strings")
        val out = mutableSetOf<String>()
        val keys = strings.keys()
        while (keys.hasNext()) out += keys.next()
        return out
    }
}
