package cc.opencar.assistant.feature.web

import cc.opencar.assistant.support.I18nBundle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.flow.first

internal fun Routing.registerCoreRoutes(deps: OcaWebDeps) {
    val context = deps.context
    val session = deps.session
    val debug = deps.debug
    val memory = deps.memory
    val capabilities = deps.capabilities
    val variantId = deps.variantId
    val port = deps.port
    val prefs = deps.prefs

    get("/api/status") {
        val snap = session.telemetry().first()
        val host = call.request.local.remoteHost
        val i18n = I18nBundle.load(context, session.integrationId)
        val driveModeLabel = i18n.resolveMaybe(snap.driveMode)
            ?: i18n.valueLabel("drive_mode", snap.driveMode)
            ?: snap.driveMode
        call.respond(
            mapOf(
                "integration" to session.integrationId,
                "variant" to variantId,
                "capabilities" to capabilities,
                "locale" to i18n.locale,
                "locales" to I18nBundle.SUPPORTED,
                "remote" to (host != "127.0.0.1" && host != "localhost" && host != "::1"),
                "telemetry" to mapOf(
                    "gear" to snap.gear,
                    "gearLabel" to snap.gear?.let { i18n.valueLabel("gear", it) },
                    "speedKmh" to snap.speedKmh,
                    "evBatteryPercent" to snap.evBatteryPercent,
                    "hybridSocPercent" to snap.hybridSocPercent,
                    "rangeKm" to snap.rangeKm,
                    "driveMode" to driveModeLabel,
                    "driveModeKey" to snap.driveMode,
                    "energyMode" to (snap.extras["energyMode"]?.let { i18n.resolveMaybe(it) ?: it }),
                    "energyModeKey" to snap.extras["energyMode"],
                    "regenLevel" to snap.regenLevel,
                    "regenLabel" to snap.regenLevel?.let { i18n.valueLabel("regen", it) },
                    "hvacPower" to snap.hvacPower,
                    "hvacTempC" to snap.hvacTempC,
                    "hvacFan" to snap.hvacFan,
                    "chargeCurrentA" to snap.chargeCurrentA,
                    "chargePlugConnected" to snap.chargePlugConnected,
                    "ignitionState" to snap.ignitionState,
                    "ignitionLabel" to snap.ignitionState?.let { i18n.valueLabel("ignition", it) },
                    "model" to snap.extras["model"],
                    "parkingBrake" to snap.extras["parkingBrake"],
                    "parkingBrakeLabel" to snap.extras["parkingBrake"]?.let { i18n.valueLabel("parking_brake", it) },
                    "extras" to snap.extras,
                ),
                "setup" to SetupStatus.snapshot(context, session, prefs),
                "plugins" to deps.pluginDetailMaps(),
                "dvr" to deps.dvr.status(),
                "webPort" to port,
                "theme" to (prefs.getString("theme", "dark") ?: "dark"),
                "adb" to WirelessAdbController(context, debug).status(),
                "android" to (deps.androidSettings?.status() ?: emptyMap<String, Any?>()),
            ),
        )
    }
    get("/api/i18n") {
        val i18n = I18nBundle.load(context, session.integrationId)
        call.respond(
            mapOf(
                "locale" to i18n.locale,
                "locales" to I18nBundle.SUPPORTED,
                "integration" to session.integrationId,
                "strings" to i18n.dictionary(),
                "valueMaps" to i18n.valueMapsSnapshot(),
            ),
        )
    }
    post("/api/locale") {
        val params = call.receiveParameters()
        val raw = params["locale"] ?: call.request.queryParameters["locale"]
        val loc = I18nBundle.normalize(raw)
        prefs.edit().putString(I18nBundle.PREF_LOCALE, loc).apply()
        val i18n = I18nBundle.load(context, session.integrationId, loc)
        call.respond(
            mapOf(
                "ok" to true,
                "locale" to i18n.locale,
                "strings" to i18n.dictionary(),
                "valueMaps" to i18n.valueMapsSnapshot(),
            ),
        )
    }
    get("/api/controls") {
        val hidden = deps.entityVisibility.hiddenIds()
        val includeHidden = call.request.queryParameters["includeHidden"] == "1"
        val all = ControlCatalog.snapshot(session, context, memory).map { row ->
            val id = row["id"] as? String
            row + ("hidden" to (id != null && id in hidden))
        }
        call.respond(if (includeHidden) all else all.filter { it["hidden"] != true })
    }
    get("/api/entities") {
        val hidden = deps.entityVisibility.hiddenIds()
        val includeHidden = call.request.queryParameters["includeHidden"] == "1"
        val all = ControlCatalog.entities(session, context, memory, deps.androidSettings).map { row ->
            val id = row["id"] as? String
            row + ("hidden" to (id != null && id in hidden))
        }
        call.respond(if (includeHidden) all else all.filter { it["hidden"] != true })
    }
    get("/api/entities/hidden") {
        val hidden = deps.entityVisibility.hiddenIds()
        val all = ControlCatalog.entities(session, context, memory, deps.androidSettings)
        call.respond(
            mapOf(
                "ids" to hidden.toList().sorted(),
                "entities" to all.filter { (it["id"] as? String) in hidden }.map {
                    it + ("hidden" to true)
                },
            ),
        )
    }
    post("/api/entities/{id}/visibility") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("ok" to false))
        val params = call.receiveParameters()
        val raw = params["hidden"] ?: call.request.queryParameters["hidden"]
        val hide = when (raw?.lowercase()) {
            "1", "true", "yes", "hide" -> true
            "0", "false", "no", "unhide", "show" -> false
            else -> return@post call.respond(HttpStatusCode.BadRequest, mapOf("ok" to false, "error" to "hidden required"))
        }
        deps.entityVisibility.setHidden(id, hide)
        call.respond(mapOf("ok" to true, "id" to id, "hidden" to hide, "ids" to deps.entityVisibility.hiddenIds().toList().sorted()))
    }
    get("/api/setup") {
        call.respond(SetupStatus.snapshot(context, session, prefs))
    }
    post("/api/setup") {
        val params = call.receiveParameters()
        val edit = prefs.edit()
        if (params["dismiss"] == "1" || params["dismiss"] == "true") {
            edit.putBoolean("setup_dismissed", true)
        }
        if (params["reset"] == "1" || params["reset"] == "true") {
            edit.remove("setup_dismissed")
        }
        edit.apply()
        call.respond(SetupStatus.snapshot(context, session, prefs))
    }
    post("/api/setup/actions/request-runtime") {
        SetupActionBus.requestRuntimePermissions()
        call.respond(mapOf("ok" to true, "message" to "Solicitando permissões no HU"))
    }
    post("/api/system/open-android-settings") {
        SetupActionBus.requestOpenAndroidSettings()
        call.respond(mapOf("ok" to true, "message" to "Opening Android Settings"))
    }
    get("/api/android") {
        val android = deps.androidSettings
        if (android == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "android settings unavailable"))
            return@get
        }
        call.respond(android.status())
    }
    post("/api/android/{id}") {
        val android = deps.androidSettings
        if (android == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "android settings unavailable"))
            return@post
        }
        val id = call.parameters["id"] ?: return@post
        val params = call.receiveParameters()
        val value = params["value"] ?: call.request.queryParameters["value"]
        if (value == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "value required"))
            return@post
        }
        val on = value == "1" || value.equals("true", true) || value.equals("on", true)
        val result = when (id) {
            AndroidSettingsController.ID_WIFI -> android.setWifi(on)
            AndroidSettingsController.ID_BT -> android.setBluetooth(on)
            else -> mapOf("ok" to false, "error" to "unknown id")
        }
        call.respond(result)
    }
    get("/api/history") {
        val history = deps.history
        if (history == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "history unavailable"))
            return@get
        }
        call.respond(mapOf("entities" to history.entitiesTracked()))
    }
    get("/api/history/{entityId}") {
        val history = deps.history
        if (history == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "history unavailable"))
            return@get
        }
        val entityId = call.parameters["entityId"] ?: return@get
        val end = call.request.queryParameters["end"]?.toLongOrNull()
            ?: System.currentTimeMillis()
        val start = call.request.queryParameters["start"]?.toLongOrNull()
            ?: (end - 24L * 60 * 60 * 1000)
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 2000
        call.respond(
            mapOf(
                "entityId" to entityId,
                "start" to start,
                "end" to end,
                "points" to history.query(entityId, start, end, limit),
            ),
        )
    }
    post("/api/setup/actions/elevate") {
        val elev = PrivilegeElevator(context, session.integrationId)
        call.respond(elev.elevate())
    }
    get("/api/setup/actions/elevate-status") {
        call.respond(PrivilegeElevator(context, session.integrationId).status())
    }
    post("/api/setup/actions/reboot") {
        call.respond(PrivilegeElevator(context, session.integrationId).reboot())
    }
    post("/api/controls/{id}") {
        val id = call.parameters["id"] ?: return@post
        val params = call.receiveParameters()
        val value = params["value"] ?: call.request.queryParameters["value"]
        if (value == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "value required"))
            return@post
        }
        if (id == AndroidSettingsController.ID_WIFI || id == AndroidSettingsController.ID_BT) {
            val android = deps.androidSettings
            if (android == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "android settings unavailable"))
                return@post
            }
            val on = value == "1" || value.equals("true", true) || value.equals("on", true)
            val result = when (id) {
                AndroidSettingsController.ID_WIFI -> android.setWifi(on)
                else -> android.setBluetooth(on)
            }
            call.respond(mapOf("ok" to (result["ok"] == true), "error" to result["error"]))
            return@post
        }
        val result = ControlCatalog.set(session, id, value, context)
        call.respond(
            mapOf(
                "ok" to result.isSuccess,
                "error" to result.exceptionOrNull()?.message,
            ),
        )
    }
    post("/api/controls/{id}/persist") {
        val id = call.parameters["id"] ?: return@post
        if (memory == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "memory unavailable"))
            return@post
        }
        val params = call.receiveParameters()
        val enabledRaw = params["enabled"] ?: call.request.queryParameters["enabled"]
        val enabled = when (enabledRaw) {
            null -> null
            "1", "true", "on" -> true
            "0", "false", "off" -> false
            else -> null
        }
        val value = params["value"] ?: call.request.queryParameters["value"]
        call.respond(memory.setPersist(id, enabled, value))
    }
    get("/api/prefs") {
        call.respond(
            mapOf(
                "theme" to (prefs.getString("theme", "dark") ?: "dark"),
                "locale" to I18nBundle.normalize(prefs.getString(I18nBundle.PREF_LOCALE, null)),
                "locales" to I18nBundle.SUPPORTED,
            ),
        )
    }
    post("/api/prefs") {
        val params = call.receiveParameters()
        val edit = prefs.edit()
        val theme = params["theme"]
        if (theme != null && theme in setOf("dark", "light", "contrast")) {
            edit.putString("theme", theme)
        }
        val locale = params["locale"]
        if (locale != null) {
            edit.putString(I18nBundle.PREF_LOCALE, I18nBundle.normalize(locale))
        }
        edit.apply()
        call.respond(
            mapOf(
                "ok" to true,
                "theme" to (prefs.getString("theme", "dark") ?: "dark"),
                "locale" to I18nBundle.normalize(prefs.getString(I18nBundle.PREF_LOCALE, null)),
            ),
        )
    }
    post("/api/memory/capture") {
        if (memory == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "memory unavailable"))
            return@post
        }
        val captured = memory.captureFromVehicle()
        call.respond(mapOf("ok" to true, "prefs" to captured))
    }
    post("/api/memory/reapply") {
        memory?.reapply()
        call.respond(mapOf("ok" to true))
    }
    get("/api/adb") {
        call.respond(WirelessAdbController(context, debug).status())
    }
    post("/api/adb") {
        val params = call.receiveParameters()
        val enabled = when (params["enabled"] ?: call.request.queryParameters["enabled"]) {
            "1", "true", "on" -> true
            "0", "false", "off" -> false
            else -> null
        }
        if (enabled == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "enabled=1|0 required"))
            return@post
        }
        val adbPort = (params["port"] ?: call.request.queryParameters["port"])?.toIntOrNull() ?: 5566
        call.respond(
            WirelessAdbController(context, debug).setEnabled(enabled, adbPort),
        )
    }
}
