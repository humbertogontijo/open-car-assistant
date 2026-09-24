package cc.opencar.assistant.feature.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Routing
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
        val ok = dvr.start(null)
        call.respond(mapOf("ok" to ok, "status" to dvr.status()))
    }
    post("/api/dvr/stop") {
        dvr.stop()
        call.respond(mapOf("ok" to true, "status" to dvr.status()))
    }
    post("/api/dvr/toggle") {
        val storage = call.request.queryParameters["storage"]
            ?: call.receiveParameters()["storage"]
        if (storage != null) dvr.setStorage(storage)
        call.respond(dvr.toggleRecording())
    }
    post("/api/dvr/storage") {
        val params = call.receiveParameters()
        val id = params["id"] ?: call.request.queryParameters["id"]
        val ok = dvr.setStorage(id)
        call.respond(mapOf("ok" to ok, "status" to dvr.status()))
    }
    get("/api/dvr/status") {
        call.respond(dvr.status())
    }
    post("/api/dvr/preview/start") {
        // null camera → merged mosaic
        val cam = call.request.queryParameters["camera"]
        val ok = withContext(Dispatchers.IO) {
            dvr.startPreview(if (cam.isNullOrBlank() || cam == "merged") null else cam)
        }
        call.respond(mapOf("ok" to ok, "status" to dvr.status()))
    }
    post("/api/dvr/preview/stop") {
        dvr.stopPreview()
        call.respond(mapOf("ok" to true, "status" to dvr.status()))
    }
    get("/api/dvr/preview.jpg") {
        val cam = call.request.queryParameters["camera"]
        val jpeg = withContext(Dispatchers.IO) {
            dvr.snapshotJpeg(if (cam.isNullOrBlank() || cam == "merged") null else cam)
        }
        if (jpeg == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("error" to (dvr.previewStatus()["lastError"] ?: "no frame")),
            )
            return@get
        }
        call.respondBytes(jpeg, ContentType.Image.JPEG)
    }
    get("/api/dvr/preview.mjpeg") {
        val cam = call.request.queryParameters["camera"]
        val started = withContext(Dispatchers.IO) {
            dvr.startPreview(if (cam.isNullOrBlank() || cam == "merged") null else cam)
        }
        if (!started && dvr.latestPreviewJpeg() == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("error" to (dvr.previewStatus()["lastError"] ?: "preview failed")),
            )
            return@get
        }
        call.respondOutputStream(
            contentType = ContentType.parse("multipart/x-mixed-replace; boundary=frame"),
        ) {
            var idle = 0
            while (idle < 100) {
                val jpeg = dvr.latestPreviewJpeg()
                if (jpeg != null) {
                    idle = 0
                    write("--frame\r\n".toByteArray())
                    write("Content-Type: image/jpeg\r\n".toByteArray())
                    write("Content-Length: ${jpeg.size}\r\n\r\n".toByteArray())
                    write(jpeg)
                    write("\r\n".toByteArray())
                    flush()
                } else {
                    idle++
                }
                delay(100)
            }
        }
    }
}
