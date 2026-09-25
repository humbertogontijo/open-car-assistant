package cc.opencar.assistant.feature.web

import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.api.VehicleEvent
import com.google.gson.Gson
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Event-driven UI channel: telemetry + entity deltas + catalog invalidate.
 * Client still bootstraps once via HTTP [refresh]; this replaces soft-poll.
 *
 * Composite binding-key edges never become WS `entity` deltas — catalog
 * invalidation only ([EntityContract.UPDATE_CATALOG]).
 */
internal fun Routing.registerEventRoutes(deps: OcaWebDeps) {
    val session = deps.session
    val gson = Gson()

    webSocket("/api/events") {
        try {
            send(Frame.Text(gson.toJson(mapOf("t" to "hello"))))
            val telemetryJob = launch {
                session.telemetry().distinctUntilChanged().collect { snap ->
                    val payload = mapOf(
                        "t" to "telemetry",
                        "telemetry" to telemetryPayload(snap),
                        "dvr" to deps.dvr.status(),
                    )
                    send(Frame.Text(gson.toJson(payload)))
                }
            }
            val eventsJob = launch {
                session.events().collect { ev ->
                    when (ev) {
                        is VehicleEvent.EntityValueChanged -> {
                            val product = EntityRegistry.resolveBinding(ev.entityId)
                            if (product != null && product.isComposite) {
                                // Binding-key edge for a composite → catalog only.
                                WebEventHub.emitCatalog("composite_attr")
                            } else {
                                send(
                                    Frame.Text(
                                        gson.toJson(
                                            mapOf(
                                                "t" to "entity",
                                                "id" to ev.entityId,
                                                "value" to ev.value,
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
            val hubJob = launch {
                WebEventHub.bus.collect { msg ->
                    send(Frame.Text(gson.toJson(msg)))
                }
            }
            val pingJob = launch {
                while (isActive) {
                    delay(25_000)
                    send(Frame.Text(gson.toJson(mapOf("t" to "ping"))))
                }
            }
            // Hold the socket open until the client disconnects.
            while (isActive) {
                incoming.receiveCatching().getOrNull() ?: break
            }
            telemetryJob.cancel()
            eventsJob.cancel()
            hubJob.cancel()
            pingJob.cancel()
        } catch (_: ClosedSendChannelException) {
            // client gone
        } catch (_: Throwable) {
            // best-effort
        }
    }
}
