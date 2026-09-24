package cc.opencar.assistant.feature.shortcuts

import android.util.Log
import cc.opencar.assistant.api.VehicleEvent
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.plugin.ShortcutTriggerListener
import cc.opencar.assistant.api.plugin.ShortcutTriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Matches vehicle / wheel / plugin events to shortcut triggers and runs them.
 * Pin-slot taps run via [runById] from the quick-entry menu.
 * Screen on/off is driven by [onScreenOn] / [onScreenOff]; session
 * [VehicleEvent.ScreenOn] is ignored so Antora does not double-fire.
 */
class ShortcutTriggerEngine(
    private val session: VehicleSession,
    private val store: ShortcutStore,
    private val runner: ShortcutRunner,
    private val triggerSources: List<ShortcutTriggerSource> = emptyList(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val screenMutex = Mutex()

    @Volatile private var lastScreenOnMs: Long = 0L
    /** When the display last went non-interactive; null means currently awake. */
    @Volatile private var lastScreenOffMs: Long? = null
    private val lastWheelMs = mutableMapOf<String, Long>()
    private val lastPluginMs = mutableMapOf<String, Long>()

    private val pluginListener = ShortcutTriggerListener { pluginId, triggerType, eventParams ->
        scope.launch {
            handlePluginTrigger(pluginId, triggerType, eventParams)
        }
    }

    fun start() {
        if (job != null) return
        job = scope.launch {
            session.events().collect { event ->
                when (event) {
                    // Boot is delivered from AssistantRuntime after memory+shortcuts start
                    // (session Boot may already have been consumed by memory).
                    is VehicleEvent.Boot -> Unit
                    is VehicleEvent.ScreenOn -> Unit // runtime-only via onScreenOn
                    is VehicleEvent.GearChanged -> {
                        val gear = event.gear
                        matchAndRun { t ->
                            t is ShortcutTrigger.Gear && (t.gear == null || t.gear == gear)
                        }
                    }
                    is VehicleEvent.WheelKeyPressed -> {
                        val key = event.key
                        val now = System.currentTimeMillis()
                        val last = lastWheelMs[key] ?: 0L
                        if (now - last < WHEEL_DEBOUNCE_MS) return@collect
                        lastWheelMs[key] = now
                        matchAndRun { t -> t is ShortcutTrigger.WheelKey && t.key.equals(key, true) }
                    }
                    else -> Unit
                }
            }
        }
        for (source in triggerSources) {
            source.start(pluginListener)
        }
    }

    fun stop() {
        for (source in triggerSources) {
            source.stop()
        }
        job?.cancel()
        job = null
    }

    /** Call once after runtime wires shortcuts (session Boot may already be consumed). */
    fun onBoot() {
        scope.launch {
            val matched = matchAndRun { it is ShortcutTrigger.Boot }
            Log.i(TAG, "boot: matched=$matched")
        }
    }

    /** HU display wake — call from SCREEN_ON / USER_PRESENT / DisplayListener / interactive poll. */
    fun onScreenOn(source: String = "direct") {
        scope.launch { handleScreenOn(source) }
    }

    /** HU display sleep — fires `screen`/`on=false` triggers and arms the next wake. */
    fun onScreenOff(source: String = "direct") {
        scope.launch { handleScreenOff(source) }
    }

    private suspend fun handleScreenOn(source: String) = screenMutex.withLock {
        val now = System.currentTimeMillis()
        val offAt = lastScreenOffMs
        val afterSleep = offAt != null && now - offAt >= MIN_SLEEP_MS
        val since = now - lastScreenOnMs
        if (!afterSleep && since < SCREEN_DEBOUNCE_MS) {
            Log.d(
                TAG,
                "screen on ignored ($source) debounce ${since}ms " +
                    "afterSleep=$afterSleep offAge=${offAt?.let { now - it }}",
            )
            return
        }
        lastScreenOnMs = now
        lastScreenOffMs = null
        Log.d(TAG, "screen on ($source) afterSleep=$afterSleep")
        val matched = matchAndRun { it is ShortcutTrigger.Screen && it.on }
        if (matched == 0) {
            Log.d(TAG, "screen on: no enabled shortcuts with screen/on trigger")
        }
    }

    private suspend fun handleScreenOff(source: String) = screenMutex.withLock {
        val now = System.currentTimeMillis()
        val offAt = lastScreenOffMs
        if (offAt != null) {
            if (now - offAt < SCREEN_DEBOUNCE_MS) {
                Log.d(TAG, "screen off ignored ($source) debounce ${now - offAt}ms")
                return
            }
            // Still off — refresh timestamp, do not re-fire.
            lastScreenOffMs = now
            Log.d(TAG, "screen off ignored ($source) already off")
            return
        }
        lastScreenOffMs = now
        Log.d(TAG, "screen off ($source)")
        val matched = matchAndRun { it is ShortcutTrigger.Screen && !it.on }
        if (matched == 0) {
            Log.d(TAG, "screen off: no enabled shortcuts with screen/off trigger")
        }
    }

    private suspend fun handlePluginTrigger(
        pluginId: String,
        triggerType: String,
        eventParams: Map<String, Any?>,
    ) {
        val debounceKey = "$pluginId:$triggerType:${eventParams["entity_id"] ?: ""}"
        val now = System.currentTimeMillis()
        val last = lastPluginMs[debounceKey] ?: 0L
        if (now - last < PLUGIN_DEBOUNCE_MS) return
        lastPluginMs[debounceKey] = now
        val matched = matchAndRun { t ->
            t is ShortcutTrigger.Plugin &&
                t.pluginId == pluginId &&
                t.trigger == triggerType &&
                pluginParamsMatch(t.params, eventParams)
        }
        if (matched > 0) {
            Log.i(TAG, "plugin trigger $pluginId/$triggerType matched=$matched params=$eventParams")
        }
    }

    suspend fun runById(id: String): Map<String, Any?> {
        val s = store.get(id) ?: return mapOf("ok" to false, "error" to "not found")
        return runner.run(s)
    }

    private suspend fun matchAndRun(predicate: (ShortcutTrigger) -> Boolean): Int {
        val list = store.list().filter { it.enabled }
        var count = 0
        for (s in list) {
            if (s.triggers.any(predicate)) {
                count++
                Log.i(TAG, "trigger fired shortcut=${s.id} name=${s.name}")
                runner.run(s)
            }
        }
        return count
    }

    companion object {
        private const val TAG = "ShortcutTriggers"
        /** Collapse duplicate wake/sleep signals (SCREEN_ON + USER_PRESENT + vendor). */
        const val SCREEN_DEBOUNCE_MS = 5_000L
        /** Minimum off duration before the next wake is treated as a fresh screen on. */
        const val MIN_SLEEP_MS = 1_500L
        const val WHEEL_DEBOUNCE_MS = 400L
        const val PLUGIN_DEBOUNCE_MS = 400L

        /**
         * Stored trigger params must be satisfied by the event.
         * Keys present in the trigger (non-null / non-blank) must equal the event.
         * Extra event keys are ignored.
         */
        fun pluginParamsMatch(triggerParams: Map<String, Any?>, eventParams: Map<String, Any?>): Boolean {
            for ((key, expected) in triggerParams) {
                if (expected == null) continue
                val exp = expected.toString()
                if (exp.isBlank()) continue
                val actual = eventParams[key]?.toString() ?: return false
                if (!actual.equals(exp, ignoreCase = false)) return false
            }
            return true
        }
    }
}
