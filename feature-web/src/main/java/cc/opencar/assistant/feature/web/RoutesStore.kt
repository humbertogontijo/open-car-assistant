package cc.opencar.assistant.feature.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun Routing.registerStoreRoutes(deps: OcaWebDeps) {
    post("/api/install/binary") {
        val expected = call.request.headers["X-Sha256"]
        val channel = call.receiveChannel()
        val dir = deps.installer.installDir()
        val file = File(dir, "upload-${System.currentTimeMillis()}.apk")
        file.outputStream().use { out ->
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(limit = 8192)
                if (packet.isEmpty) break
                out.write(packet.readBytes())
            }
        }
        val result = deps.installer.install(file, expected)
        call.respond(mapOf("ok" to result.ok, "message" to result.message, "sha256" to result.sha256))
    }
    get("/api/store/search") {
        val q = call.request.queryParameters["q"].orEmpty()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
        val hits = withContext(Dispatchers.IO) { deps.store.search(q, limit) }
        call.respond(
            mapOf(
                "ok" to true,
                "query" to q,
                "apps" to hits.map {
                    mapOf(
                        "packageName" to it.packageName,
                        "name" to it.name,
                        "summary" to it.summary,
                        "iconUrl" to it.iconUrl,
                        "source" to it.source,
                        "installReady" to it.installReady,
                    )
                },
            ),
        )
    }
    get("/api/store/package/{packageName}") {
        val pkg = call.parameters["packageName"].orEmpty()
        val detail = withContext(Dispatchers.IO) { deps.store.detail(pkg) }
        if (detail == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "message" to "not found"))
            return@get
        }
        call.respond(detail)
    }
    post("/api/store/install") {
        val params = call.receiveParameters()
        val pkg = params["packageName"]
            ?: call.request.queryParameters["packageName"]
            ?: ""
        val vc = (params["versionCode"] ?: call.request.queryParameters["versionCode"])
            ?.toLongOrNull()
        if (pkg.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("ok" to false, "message" to "packageName required"))
            return@post
        }
        val outcome = withContext(Dispatchers.IO) { deps.store.install(pkg, vc) }
        call.respond(
            mapOf(
                "ok" to outcome.ok,
                "message" to outcome.message,
                "packageName" to outcome.packageName,
                "sha256" to outcome.sha256,
            ),
        )
    }
}
