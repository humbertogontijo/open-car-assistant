package cc.opencar.assistant.feature.shortcuts

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import cc.opencar.assistant.api.QuickEntry
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.plugin.ShortcutActionHandler
import cc.opencar.assistant.api.plugin.ShortcutTriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Facade: store + runner + trigger engine + platform [QuickEntry] + shared menu.
 */
class ShortcutsController(
    private val context: Context,
    session: VehicleSession,
    setControl: suspend (entityId: String, value: String) -> Result<Unit>,
    private val mainActivityClass: Class<*>,
    quickEntry: QuickEntry? = null,
    actionHandlers: Map<String, ShortcutActionHandler> = emptyMap(),
    triggerSources: List<ShortcutTriggerSource> = emptyList(),
) {
    val store = ShortcutStore.get(context)
    val launcher = AppLauncher(context)
    val runner = ShortcutRunner(context, setControl, launcher, actionHandlers)
    val engine = ShortcutTriggerEngine(session, store, runner, triggerSources)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entry: QuickEntry = quickEntry ?: FloatChipQuickEntry()
    private var menu: QuickEntryMenu? = null
    private var entryStarted = false
    private var visibilityJob: Job? = null

    fun start() {
        engine.start()
    }

    fun stop() {
        engine.stop()
        detachOverlay()
    }

    /** Session Boot may already be consumed by memory — call once after start. */
    fun onBoot() {
        engine.onBoot()
    }

    /** Display / unlock wake — drives `screen`/`on` triggers. */
    fun onScreenOn(source: String = "direct") {
        engine.onScreenOn(source)
    }

    /** Display sleep — drives `screen`/`off` triggers and arms the next wake. */
    fun onScreenOff(source: String = "direct") {
        engine.onScreenOff(source)
    }

    fun attachOverlay(hostContext: Context = context) {
        if (entryStarted) return
        val m = QuickEntryMenu(
            context = hostContext,
            store = store,
            onRunShortcut = { id -> engine.runById(id) },
            onOpenOca = { section -> sendActivity(hostContext, QuickEntryMenu.ACTION_OPEN_SECTION, section) },
            onExitOca = { sendActivity(hostContext, QuickEntryMenu.ACTION_EXIT, null) },
            onBackgroundOca = { sendActivity(hostContext, QuickEntryMenu.ACTION_BACKGROUND, null) },
        )
        menu = m
        m.start()
        entry.start(hostContext, QuickEntry.Listener { anchor -> m.onActivated(anchor) })
        entryStarted = true
        visibilityJob?.cancel()
        visibilityJob = scope.launch {
            entry.setVisible(store.isOverlayEnabled())
            store.overlayEnabled.distinctUntilChanged().collect { enabled ->
                entry.setVisible(enabled)
            }
        }
    }

    fun detachOverlay() {
        visibilityJob?.cancel()
        visibilityJob = null
        entry.stop()
        menu?.stop()
        menu = null
        entryStarted = false
    }

    fun refreshOverlay() {
        scope.launch {
            entry.setVisible(store.isOverlayEnabled())
        }
    }

    fun overlayStatus(): Map<String, Any?> {
        return entry.status() + mapOf(
            "canDrawOverlays" to android.provider.Settings.canDrawOverlays(context),
        )
    }

    suspend fun setOverlayEnabled(enabled: Boolean): Map<String, Any?> {
        store.setOverlayEnabled(enabled)
        entry.setVisible(enabled)
        return overlayStatus() + mapOf("overlayEnabled" to enabled)
    }

    fun decorateNotification(context: Context, notification: Notification, visible: Boolean) {
        entry.decorateNotification(context, notification, visible)
    }

    fun notificationContentIntent(context: Context): PendingIntent? =
        entry.notificationContentIntent(context)

    suspend fun listMaps(): List<Map<String, Any?>> = store.list().map { it.toMap() }

    suspend fun slotMaps(): Map<String, String?> {
        val map = store.slotMap()
        return (0 until 8).associate { i -> i.toString() to map[i] }
    }

    suspend fun setSlot(slot: Int, shortcutId: String?): Map<String, Any?> {
        store.setSlot(slot, shortcutId)
        return mapOf("ok" to true, "slots" to slotMaps())
    }

    suspend fun upsertFromMap(body: Map<String, Any?>): Map<String, Any?> {
        val existingId = body["id"] as? String
        val base = if (existingId != null) store.get(existingId) else null
        val name = body["name"] as? String ?: base?.name ?: "Shortcut"
        val icon = body["icon"] as? String ?: base?.icon ?: "drive"
        val enabled = body["enabled"] as? Boolean ?: base?.enabled ?: true
        @Suppress("UNCHECKED_CAST")
        val actionsRaw = body["actions"] as? List<Map<*, *>>
        @Suppress("UNCHECKED_CAST")
        val triggersRaw = body["triggers"] as? List<Map<*, *>>
        val actions = actionsRaw?.mapNotNull { ShortcutAction.fromMap(it) }
            ?: base?.actions
            ?: emptyList()
        val triggers = triggersRaw?.mapNotNull { ShortcutTrigger.fromMap(it) }
            ?: base?.triggers
            ?: emptyList()
        val id = existingId ?: java.util.UUID.randomUUID().toString().take(8)
        val saved = store.upsert(
            Shortcut(
                id = id,
                name = name,
                icon = icon,
                enabled = enabled,
                actions = actions.take(ShortcutAction.MAX_ACTIONS),
                triggers = triggers,
            ),
        )
        return mapOf("ok" to true, "shortcut" to saved.toMap())
    }

    suspend fun delete(id: String): Map<String, Any?> {
        val ok = store.delete(id)
        return mapOf("ok" to ok)
    }

    suspend fun run(id: String): Map<String, Any?> = engine.runById(id)

    private fun sendActivity(host: Context, action: String, section: String?) {
        val intent = Intent(host, mainActivityClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            this.action = action
            if (!section.isNullOrBlank()) {
                putExtra(QuickEntryMenu.EXTRA_SECTION, section)
            }
        }
        try {
            host.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "activity $action failed: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "ShortcutsCtrl"
    }
}
