package cc.opencar.assistant.feature.web

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import kotlin.math.abs

/**
 * Cabin [CarVolumeGroup] levels via unprivileged Settings mirrors.
 *
 * Read: [settingsKey] (default `android.car.VOLUME_GROUP/{id}`, written by Car).
 * Write: only when [keyWritable] — VOLUME_UP/DOWN keys adjust the media bus on this HU.
 * Absolute set for other groups needs privileged `CarAudioManager` (OEM AutoSettings path).
 *
 * Group map comes from `platform.json` → `android.volumeGroups`.
 */
class CarAudioVolume(
    private val context: Context,
    val groupId: Int = GROUP_MEDIA,
    val keyWritable: Boolean = groupId == GROUP_MEDIA,
    private val settingsKey: String = "$SETTINGS_VOLUME_GROUP_PREFIX$groupId",
) {
    data class State(val current: Int, val min: Int, val max: Int)

    @Volatile private var lastKnown: Int? = null

    fun read(): State {
        readSettings()?.let {
            lastKnown = it.current
            return it
        }
        return State(lastKnown ?: DEFAULT_CURRENT, DEFAULT_MIN, DEFAULT_MAX)
    }

    fun readOrNull(): State? = readSettings()

    fun set(index: Int): Boolean {
        if (!keyWritable) return false
        val target = index.coerceIn(DEFAULT_MIN, DEFAULT_MAX)
        val before = read().current
        if (target == before) {
            lastKnown = target
            return true
        }
        if (!stepBy(target - before)) return false
        SystemClock.sleep(80)
        val after = readSettings()?.current
        if (after != null) {
            lastKnown = after
            return abs(after - target) <= 1
        }
        lastKnown = target
        return true
    }

    fun adjust(delta: Int): Boolean {
        if (delta == 0) return true
        return set(read().current + delta)
    }

    private fun readSettings(): State? {
        return try {
            val raw = Settings.System.getString(context.contentResolver, settingsKey)
                ?: return null
            val parts = raw.split(':', ',', ' ').mapNotNull { it.trim().toIntOrNull() }
            when {
                parts.isEmpty() -> null
                parts.size == 1 -> State(parts[0], DEFAULT_MIN, DEFAULT_MAX)
                parts.size >= 3 -> State(parts[0], parts[1], parts[2])
                else -> State(parts[0], DEFAULT_MIN, DEFAULT_MAX)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "read $settingsKey: ${t.message}")
            null
        }
    }

    private fun stepBy(delta: Int): Boolean {
        if (delta == 0) return true
        val key = if (delta > 0) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
        val steps = abs(delta)
        repeat(steps) {
            if (!KeyEventInject.send(context, key)) return false
            SystemClock.sleep(40)
        }
        return true
    }

    companion object {
        private const val TAG = "CarAudioVolume"
        private const val DEFAULT_CURRENT = 17
        private const val SETTINGS_VOLUME_GROUP_PREFIX = "android.car.VOLUME_GROUP/"

        const val DEFAULT_MIN = 0
        const val DEFAULT_MAX = 39

        /** MUSIC / OEM “Media” — only group volume keys move unprivileged. */
        const val GROUP_MEDIA = 0
    }
}
