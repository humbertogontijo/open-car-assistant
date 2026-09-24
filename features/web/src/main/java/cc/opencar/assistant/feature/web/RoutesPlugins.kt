package cc.opencar.assistant.feature.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONObject

internal fun Routing.registerPluginRoutes(deps: OcaWebDeps) {
    get("/api/plugins") {
        call.respond(mapOf("plugins" to deps.pluginDetailMaps()))
    }

    get("/api/plugins/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing id"))
            return@get
        }
        val plugin = deps.plugins?.get(id)
        if (plugin == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "plugin unavailable"))
            return@get
        }
        val schema = plugin.configSchema()
        call.respond(
            mapOf(
                "id" to plugin.id,
                "displayName" to plugin.displayName,
                "status" to plugin.status(),
                "config" to plugin.configSnapshot(),
                "schema" to schema?.let { s ->
                    mapOf(
                        "fields" to s.fields.map { f ->
                            mapOf(
                                "key" to f.key,
                                "type" to f.type,
                                "label" to f.label,
                                "optional" to f.optional,
                                "placeholder" to f.placeholder,
                                "description" to f.description,
                            )
                        },
                    )
                },
            ),
        )
    }

    post("/api/plugins/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing id"))
            return@post
        }
        val plugin = deps.plugins?.get(id)
        if (plugin == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "plugin unavailable"))
            return@post
        }
        val raw = call.receiveText()
        val obj = runCatching { JSONObject(raw) }.getOrNull()
        if (obj == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid json"))
            return@post
        }
        val values = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            values[key] = obj.opt(key)
        }
        plugin.applyConfig(values)
        call.respond(
            mapOf(
                "ok" to true,
                "id" to plugin.id,
                "config" to plugin.configSnapshot(),
                "status" to plugin.status(),
            ),
        )
    }
}
