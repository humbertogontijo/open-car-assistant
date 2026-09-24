package cc.opencar.assistant.feature.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun Routing.registerDvrRoutes(deps: OcaWebDeps) {
    val dvr = deps.dvr

    post("/api/dvr/start") {
        val storage = call.request.queryParameters["storage"]
        if (storage != null) dvr.setStorage(storage)
        val ok = withContext(Dispatchers.IO) { dvr.start(null) }
        call.respond(mapOf("ok" to ok, "status" to dvr.status()))
    }
    post("/api/dvr/stop") {
        withContext(Dispatchers.IO) { dvr.stop() }
        call.respond(mapOf("ok" to true, "status" to dvr.status()))
    }
    post("/api/dvr/toggle") {
        val storage = call.request.queryParameters["storage"]
            ?: call.receiveParameters()["storage"]
        if (storage != null) dvr.setStorage(storage)
        val res = withContext(Dispatchers.IO) { dvr.toggleRecording() }
        call.respond(res)
    }
    post("/api/dvr/mode") {
        val params = call.receiveParameters()
        val mode = params["mode"] ?: call.request.queryParameters["mode"]
        val storage = params["storage"] ?: call.request.queryParameters["storage"]
        if (storage != null) dvr.setStorage(storage)
        val res = withContext(Dispatchers.IO) { dvr.setMode(mode) }
        call.respond(res)
    }
    post("/api/dvr/policy") {
        val params = call.receiveParameters()
        val maxTotalMb = (params["maxTotalMb"] ?: call.request.queryParameters["maxTotalMb"])
            ?.toIntOrNull()
        val maxAgeDays = (params["maxAgeDays"] ?: call.request.queryParameters["maxAgeDays"])
            ?.toIntOrNull()
        val status = withContext(Dispatchers.IO) { dvr.setPolicy(maxTotalMb, maxAgeDays) }
        call.respond(mapOf("ok" to true, "status" to status))
    }
    post("/api/dvr/storage") {
        val params = call.receiveParameters()
        val id = params["id"] ?: call.request.queryParameters["id"]
        val ok = withContext(Dispatchers.IO) { dvr.setStorage(id) }
        call.respond(mapOf("ok" to ok, "status" to dvr.status()))
    }
    get("/api/dvr/status") {
        call.respond(withContext(Dispatchers.IO) { dvr.status() })
    }
    get("/api/dvr/recordings") {
        val list = withContext(Dispatchers.IO) { dvr.listRecordings() }
        call.respond(mapOf("ok" to true, "recordings" to list))
    }
    get("/api/dvr/recordings/{name}") {
        val name = call.parameters["name"] ?: return@get
        val file = dvr.recordingFile(name)
        if (file == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "error" to "not found"))
            return@get
        }
        val inline = call.request.queryParameters["inline"] == "1"
        if (!inline) {
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${file.name}\"",
            )
        }
        if (file.name.endsWith(".mp4")) {
            call.response.header(HttpHeaders.ContentType, "video/mp4")
        } else if (file.name.endsWith(".mjpeg")) {
            call.response.header(HttpHeaders.ContentType, "application/octet-stream")
        }
        call.respondFile(file)
    }
    get("/api/dvr/recordings/{name}/stream") {
        val name = call.parameters["name"] ?: return@get
        val file = dvr.recordingFile(name)
        if (file == null || !file.name.endsWith(".mjpeg")) {
            call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "error" to "not found"))
            return@get
        }
        val fromMs = call.request.queryParameters["fromMs"]?.toLongOrNull() ?: 0L
        try {
            call.respondOutputStream(
                contentType = ContentType.parse("multipart/x-mixed-replace; boundary=frame"),
            ) {
                withContext(Dispatchers.IO) {
                    dvr.streamRecordingMultipart(file, fromMs, this@respondOutputStream)
                }
            }
        } catch (_: Throwable) {
            // Client cancelled / replaced img.src
        }
    }
    post("/api/dvr/recordings/{name}/lock") {
        val name = call.parameters["name"] ?: return@post
        val params = call.receiveParameters()
        val locked = when (params["locked"] ?: call.request.queryParameters["locked"]) {
            "0", "false", "off" -> false
            else -> true
        }
        val ok = withContext(Dispatchers.IO) { dvr.setLocked(name, locked) }
        if (!ok) {
            call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "error" to "not found"))
        } else {
            call.respond(mapOf("ok" to true, "locked" to locked))
        }
    }
    delete("/api/dvr/recordings/{name}/lock") {
        val name = call.parameters["name"] ?: return@delete
        val ok = withContext(Dispatchers.IO) { dvr.setLocked(name, false) }
        if (!ok) {
            call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "error" to "not found"))
        } else {
            call.respond(mapOf("ok" to true, "locked" to false))
        }
    }
    delete("/api/dvr/recordings/{name}") {
        val name = call.parameters["name"] ?: return@delete
        val ok = withContext(Dispatchers.IO) { dvr.deleteRecording(name) }
        if (!ok) {
            call.respond(HttpStatusCode.NotFound, mapOf("ok" to false, "error" to "not found"))
        } else {
            call.respond(mapOf("ok" to true))
        }
    }
    post("/api/dvr/preview/start") {
        val ok = withContext(Dispatchers.IO) { dvr.startPreview(null) }
        call.respond(mapOf("ok" to ok, "status" to dvr.status()))
    }
    post("/api/dvr/preview/stop") {
        dvr.stopPreview()
        call.respond(mapOf("ok" to true, "status" to dvr.status()))
    }
    /**
     * Live HLS (CMAF/fMP4). One <video> + hls.js — no clip reloads.
     * Supports LL-HLS blocking reload via `_HLS_msn` (holds until that SN exists).
     */
    get("/api/dvr/live.m3u8") {
        val started = withContext(Dispatchers.IO) { dvr.startPreview(null) }
        if (!started) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("error" to (dvr.lastError ?: "preview failed")),
            )
            return@get
        }
        val msn = call.request.queryParameters["_HLS_msn"]?.toLongOrNull()
        // `_HLS_part` ignored — we publish whole segments only.
        val body = withContext(Dispatchers.IO) {
            if (msn != null) {
                dvr.hlsPlaylistBlocking(msn)
            } else {
                var ready: String? = null
                var waits = 0
                while (ready == null && waits < 80) {
                    ready = dvr.hlsPlaylist()
                    if (ready == null) {
                        delay(100)
                        waits++
                    }
                }
                ready
            }
        }
        if (body == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("error" to "hls not ready", "detail" to dvr.lastError),
            )
            return@get
        }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondText(body, ContentType.parse("application/vnd.apple.mpegurl"))
    }
    get("/api/dvr/live/init.mp4") {
        val init = dvr.fmp4InitSegment()
        if (init == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "no init"))
            return@get
        }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(init, ContentType.parse("video/mp4"))
    }
    get("/api/dvr/live/seg/{seq}") {
        val seq = call.parameters["seq"]?.removeSuffix(".m4s")?.toLongOrNull()
        if (seq == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "bad seq"))
            return@get
        }
        val frag = dvr.fmp4Fragment(seq)
        if (frag == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "seg gone"))
            return@get
        }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondBytes(frag, ContentType.parse("video/iso.segment"))
    }
}
