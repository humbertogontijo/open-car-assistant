package cc.opencar.assistant.feature.web

import android.content.Context
import android.util.Log
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.plugin.PluginRegistry
import cc.opencar.assistant.feature.debug.CatalogProbe
import cc.opencar.assistant.feature.debug.Obd2Probe
import cc.opencar.assistant.feature.debug.ContributorDebugState
import cc.opencar.assistant.feature.debug.LogRingBuffer
import cc.opencar.assistant.feature.dvr.DvrController
import cc.opencar.assistant.feature.history.EntityHistoryRecorder
import cc.opencar.assistant.feature.install.ApkInstaller
import cc.opencar.assistant.feature.memory.SettingsMemoryController
import cc.opencar.assistant.feature.shortcuts.ShortcutsController
import io.ktor.serialization.gson.gson
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.util.concurrent.atomic.AtomicReference

class OcaWebServer(
    private val context: Context,
    private val session: VehicleSession,
    private val debug: ContributorDebugState,
    private val memory: SettingsMemoryController?,
    private val installer: ApkInstaller,
    private val dvr: DvrController,
    private val probe: CatalogProbe,
    private val obd2: Obd2Probe? = null,
    private val capabilities: Set<String>,
    private val variantId: String,
    private val port: Int = 8787,
    private val androidSettings: AndroidSettingsController? = null,
    private val history: EntityHistoryRecorder? = null,
    private val shortcuts: ShortcutsController? = null,
    private val plugins: PluginRegistry? = null,
    private val sounds: SoundsController? = null,
    private val integrationIds: List<String> = emptyList(),
    private val getIntegrationOverride: () -> String? = { null },
    private val setIntegrationOverride: (String?) -> Unit = {},
) {
    private val engine = AtomicReference<ApplicationEngine?>(null)

    fun start() {
        if (engine.get() != null) return
        val prefs = context.getSharedPreferences("oca_ui_prefs", Context.MODE_PRIVATE)
        val deps = OcaWebDeps(
            context = context,
            session = session,
            debug = debug,
            memory = memory,
            installer = installer,
            dvr = dvr,
            probe = probe,
            obd2 = obd2,
            capabilities = capabilities,
            variantId = variantId,
            port = port,
            prefs = prefs,
            androidSettings = androidSettings,
            history = history,
            shortcuts = shortcuts,
            plugins = plugins,
            sounds = sounds ?: SoundsController(context),
            integrationIds = integrationIds,
            getIntegrationOverride = getIntegrationOverride,
            setIntegrationOverride = setIntegrationOverride,
        )
        val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { gson() }
            install(WebSockets)
            routing {
                registerStaticRoutes(deps)
                registerCoreRoutes(deps)
                registerStoreRoutes(deps)
                registerDvrRoutes(deps)
                registerSoundRoutes(deps)
                registerDebugRoutes(deps)
                registerShortcutRoutes(deps)
                registerPluginRoutes(deps)
            }
        }
        server.start(wait = false)
        engine.set(server)
        Log.i(TAG, "OCA web listening on :$port")
        LogRingBuffer.append("Web server started on :$port")
    }

    fun stop() {
        engine.getAndSet(null)?.stop(1_000, 2_000)
    }

    companion object {
        private const val TAG = "OcaWeb"
    }
}
