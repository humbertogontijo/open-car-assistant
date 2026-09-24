package cc.opencar.assistant.feature.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

internal fun Routing.registerStaticRoutes(deps: OcaWebDeps) {
    get("/") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.respondText(deps.assetText("web/index.html"), ContentType.Text.Html)
    }
    get("/static/{path...}") {
        val rel = call.parameters.getAll("path")?.joinToString("/") ?: return@get
        if (rel.contains("..") || rel.startsWith("/")) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val asset = "web/$rel"
        val type = when {
            rel.endsWith(".css") -> ContentType.Text.CSS
            rel.endsWith(".js") || rel.endsWith(".mjs") -> ContentType.Text.JavaScript
            rel.endsWith(".html") -> ContentType.Text.Html
            rel.endsWith(".svg") -> ContentType.parse("image/svg+xml")
            rel.endsWith(".m3u8") -> ContentType.parse("application/vnd.apple.mpegurl")
            else -> ContentType.Application.OctetStream
        }
        val bytes = try {
            deps.assetBytes(asset)
        } catch (_: Exception) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(bytes, type)
    }
}

internal fun OcaWebDeps.assetText(path: String): String =
    context.assets.open(path).bufferedReader().use { it.readText() }

internal fun OcaWebDeps.assetBytes(path: String): ByteArray =
    context.assets.open(path).use { it.readBytes() }
