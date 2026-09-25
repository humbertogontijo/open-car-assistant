package cc.opencar.assistant.integrations.common

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlatformConfigTest {
    private val commonAssets = File("src/main/assets/platform")
    private val antoraJson = File("../../antora1000/src/main/assets/platform.json")
    private val ihuJson = File("../../ihu629g/src/main/assets/platform.json")

    private fun parentResolver(): (String) -> String = { name ->
        File(commonAssets, "$name.json").readText()
    }

    @Test
    fun parseAntoraPlatformJson() {
        assertTrue("expected ${antoraJson.absolutePath}", antoraJson.isFile)
        val cfg = PlatformConfig.parse(antoraJson.readText(), parentResolver())
        assertEquals("antora1000", cfg.id)
        assertEquals("vhal", cfg.backend)
        assertTrue(cfg.match.isNotEmpty())
        assertTrue(cfg.bindings.containsKey("speed_kmh"))
        assertTrue(cfg.writableAllowlist.isNotEmpty())
        assertEquals(0x11600207, cfg.bindings.getValue("speed_kmh").nativeId)
        assertEquals(5, cfg.dvr.fps)
        assertEquals(720, cfg.dvr.mosaicHeight)
        assertTrue(cfg.properties.size >= 100)
        assertTrue(cfg.android.volumeGroups.any { it.entity == "cabin_vol_media" })
        assertTrue(cfg.catalogEntries().isNotEmpty())
        assertEquals(
            cfg.writableAllowlist.size,
            cfg.properties.count { it.canWrite },
        )
    }

    @Test
    fun parseIhu629gPlatformJson() {
        assertTrue("expected ${ihuJson.absolutePath}", ihuJson.isFile)
        val cfg = PlatformConfig.parse(ihuJson.readText(), parentResolver())
        assertEquals("ihu629g", cfg.id)
        assertEquals("vhal", cfg.backend)
        assertTrue(cfg.writableAllowlist.isNotEmpty())
        assertTrue(cfg.bindings.containsKey("drive_mode"))
        assertEquals(570491136, cfg.bindings.getValue("drive_mode").nativeId)
        assertTrue(cfg.driveModeEnum.containsKey(570491137))
        assertTrue(605029888 in cfg.writableAllowlist)
        assertTrue(cfg.android.settings.any { it.entity == "android_wifi" })
    }

    @Test
    fun accessDerivesAllowlist() {
        val cfg = PlatformConfig.parse(
            """
            {
              "id": "t",
              "displayName": "T",
              "backend": "vhal",
              "match": ["t"],
              "capabilities": ["READ_TELEMETRY"],
              "variants": [],
              "properties": [
                {
                  "id": "0x11400400",
                  "key": "GEAR_SELECTION",
                  "access": "r",
                  "areas": [0],
                  "entity": "gear"
                },
                {
                  "id": "0x15200510",
                  "key": "HVAC_POWER_ON",
                  "access": "rw",
                  "areas": [5],
                  "entity": "hvac_power"
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(0x11400400, cfg.bindings.getValue("gear").nativeId)
        assertEquals(5, cfg.bindings.getValue("hvac_power").areaId)
        assertTrue(0x15200510 in cfg.writableAllowlist)
        assertFalse(0x11400400 in cfg.writableAllowlist)
        assertEquals(2, cfg.catalogEntries().size)
        assertTrue(cfg.catalogEntries().first { it.name == "HVAC_POWER_ON" }.writable)
        assertFalse(cfg.catalogEntries().first { it.name == "GEAR_SELECTION" }.writable)
    }

    @Test
    fun extendsMergesPropertiesAndAndroid() {
        val parents = mapOf(
            "aosp" to """
              {
                "properties": [
                  { "id": "0x15200510", "key": "HVAC_POWER_ON", "access": "r", "areas": [0] }
                ]
              }
            """.trimIndent(),
            "android" to """
              {
                "android": {
                  "settings": [
                    { "settingsKey": "wifi_on", "entity": "android_wifi", "access": "rw" }
                  ],
                  "volumeGroups": []
                }
              }
            """.trimIndent(),
        )
        val cfg = PlatformConfig.parse(
            """
            {
              "id": "child",
              "extends": ["aosp", "android"],
              "displayName": "Child",
              "backend": "vhal",
              "match": ["c"],
              "capabilities": [],
              "variants": [],
              "properties": [
                {
                  "id": "0x15200510",
                  "access": "rw",
                  "areas": [5],
                  "entity": "hvac_power"
                }
              ],
              "android": {
                "volumeGroups": [
                  {
                    "groupId": 0,
                    "entity": "cabin_vol_media",
                    "access": "rw",
                    "writeVia": "media_keyevent"
                  }
                ]
              }
            }
            """.trimIndent(),
        ) { parents.getValue(it) }

        val power = cfg.properties.first { it.id == 0x15200510 }
        assertEquals("HVAC_POWER_ON", power.key)
        assertEquals(listOf(5), power.areas)
        assertEquals("hvac_power", power.entity)
        assertEquals("rw", power.access)
        assertTrue(power.canWrite)
        assertTrue(cfg.android.settings.any { it.entity == "android_wifi" })
        assertEquals(1, cfg.android.volumeGroups.size)
        assertEquals("cabin_vol_media", cfg.android.volumeGroups.first().entity)
    }

    @Test
    fun legacyBindingsStillParse() {
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
        // Legacy path marks bound entity writable only when its id is on allowlist.
        assertFalse(0x11400400 in cfg.writableAllowlist)
    }
}

class I18nKeyParityTest {
    @Test
    fun commonEnAndPtBrShareSameStringKeys() {
        val root = File("../../../libs/support/src/main/assets/i18n/common")
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
