package cc.opencar.assistant.feature.web

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import cc.opencar.assistant.api.AndroidVolumeGroup
import cc.opencar.assistant.api.EntityContract
import cc.opencar.assistant.api.EntityType
import cc.opencar.assistant.feature.memory.ExternalSettingsApplier
import cc.opencar.assistant.support.I18nBundle

/**
 * Android radios, brightness, and HA-style media_player for automations.
 *
 * Media: [OcaNotificationListener] / MediaController for now-playing metadata;
 * [KeyEventInject] for transport + cabin volume writes; [CarAudioVolume] for level read.
 *
 * Cabin volume groups come from `platform.json` → `android.volumeGroups` (via [volumeGroups]).
 */
class AndroidSettingsController(
    private val context: Context,
    private val volumeGroups: List<AndroidVolumeGroup> = emptyList(),
) : ExternalSettingsApplier {
    private val wifi: WifiManager?
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val bluetooth: BluetoothAdapter?
        get() {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return mgr?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        }

    private val mediaGroupId: Int =
        volumeGroups.firstOrNull { it.keyWritable }?.groupId
            ?: volumeGroups.firstOrNull { it.groupId == CarAudioVolume.GROUP_MEDIA }?.groupId
            ?: CarAudioVolume.GROUP_MEDIA

    private val mediaCabinVolume by lazy {
        val def = volumeGroups.firstOrNull { it.groupId == mediaGroupId }
        CarAudioVolume(
            context,
            mediaGroupId,
            keyWritable = def?.keyWritable ?: true,
            settingsKey = def?.settingsKey ?: "android.car.VOLUME_GROUP/$mediaGroupId",
        )
    }
    private val cabinVolumes by lazy {
        volumeGroups.associate { g ->
            g.entityId to CarAudioVolume(
                context,
                g.groupId,
                keyWritable = g.keyWritable,
                settingsKey = g.settingsKey,
            )
        }
    }

    val cabinVolIds: Set<String> = volumeGroups.map { it.entityId }.toSet()
    val allIds: Set<String> = BASE_IDS + cabinVolIds
    val writableIds: Set<String> =
        BASE_IDS + volumeGroups.filter { it.keyWritable }.map { it.entityId }

    fun status(): Map<String, Any?> {
        val w = wifi
        val b = bluetooth
        val media = mediaSnapshot()
        return mapOf(
            "wifiEnabled" to (w?.isWifiEnabled == true),
            "wifiAvailable" to (w != null),
            "bluetoothEnabled" to (b?.isEnabled == true),
            "bluetoothAvailable" to (b != null),
            "brightness" to brightnessOrNull(),
            "canWriteSettings" to Settings.System.canWrite(context),
            "mediaListenerEnabled" to isMediaListenerEnabled(),
            "media" to mapOf(
                "playback" to media.playback,
                "title" to media.title,
                "artist" to media.artist,
                "album" to media.album,
                "package" to media.packageName,
            ),
        )
    }

    fun setWifi(enable: Boolean): Map<String, Any?> {
        val w = wifi ?: return mapOf("ok" to false, "error" to "wifi unavailable", "status" to status())
        return try {
            @Suppress("DEPRECATION")
            val ok = w.setWifiEnabled(enable)
            mapOf("ok" to ok, "status" to status())
        } catch (t: Throwable) {
            Log.w(TAG, "setWifi failed: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"), "status" to status())
        }
    }

    fun setBluetooth(enable: Boolean): Map<String, Any?> {
        val b = bluetooth ?: return mapOf("ok" to false, "error" to "bluetooth unavailable", "status" to status())
        return try {
            @Suppress("DEPRECATION")
            val ok = if (enable) b.enable() else b.disable()
            mapOf("ok" to ok, "status" to status())
        } catch (t: Throwable) {
            Log.w(TAG, "setBluetooth failed: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"), "status" to status())
        }
    }

    fun setBrightness(level: Int): Map<String, Any?> {
        if (!Settings.System.canWrite(context)) {
            return mapOf(
                "ok" to false,
                "error" to "WRITE_SETTINGS not granted",
                "status" to status(),
            )
        }
        val clamped = level.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val ok = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped,
            )
            mapOf("ok" to ok, "brightness" to clamped, "status" to status())
        } catch (t: Throwable) {
            Log.w(TAG, "setBrightness failed: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"), "status" to status())
        }
    }

    /** Accepts HA-style states, transport commands, or volume (`volume:N` / up / down / bare int). */
    private fun applyMediaPlayer(value: String): Boolean {
        val v = value.trim().lowercase().replace('-', '_')
        when {
            v == "volume_up" || v == "vol_up" -> return mediaCabinVolume.adjust(1)
            v == "volume_down" || v == "vol_down" -> return mediaCabinVolume.adjust(-1)
            v.startsWith("volume_level:") || v.startsWith("volume_level_") -> {
                val level = v.removePrefix("volume_level").trimStart(':', '_').toFloatOrNull()
                    ?: return false
                val st = mediaCabinVolume.read()
                val max = st.max
                val min = st.min
                val idx = (min + level.coerceIn(0f, 1f) * (max - min)).toInt()
                return mediaCabinVolume.set(idx)
            }
            v.startsWith("volume:") || v.startsWith("volume_") -> {
                val rest = v.removePrefix("volume").trimStart(':', '_')
                if (rest == "up" || rest == "down") return false
                val n = rest.toIntOrNull() ?: return false
                return mediaCabinVolume.set(n)
            }
            v.toIntOrNull() != null -> return mediaCabinVolume.set(v.toInt())
        }
        return sendMediaKey(v)
    }

    /** Play / pause / next / previous / stop via media key inject. */
    fun mediaCommand(command: String): Map<String, Any?> {
        val cmd = command.trim().lowercase().replace('-', '_')
        val ok = sendMediaKey(cmd)
        return mapOf(
            "ok" to ok,
            "command" to command,
            "error" to if (ok) null else "transport failed: $command",
            "status" to status(),
        )
    }

    fun setPlayback(state: String): Map<String, Any?> {
        val target = state.trim().lowercase()
        val cmd = when (target) {
            "playing", "play", "1", "on", "true" -> "play"
            "paused", "pause", "0", "off", "false" -> "pause"
            "idle", "stop" -> "stop"
            else -> return mapOf("ok" to false, "error" to "unknown playback: $state", "status" to status())
        }
        return mediaCommand(cmd)
    }

    private fun sendMediaKey(cmd: String): Boolean {
        val key = mediaKeyCode(cmd) ?: return false
        return KeyEventInject.send(context, key)
    }

    private fun mediaKeyCode(cmd: String): Int? = when (cmd) {
        "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
        "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "play_pause", "playpause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        "next", "skip_next" -> KeyEvent.KEYCODE_MEDIA_NEXT
        "previous", "prev", "skip_previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
        "playing", "on" -> KeyEvent.KEYCODE_MEDIA_PLAY
        "paused" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "idle", "off" -> KeyEvent.KEYCODE_MEDIA_STOP
        else -> null
    }

    /** Opens the system notification-listener settings so now-playing can be read. */
    fun openMediaListenerSettings(): Map<String, Any?> {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("ok" to true)
        } catch (t: Throwable) {
            Log.w(TAG, "openMediaListenerSettings: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"))
        }
    }

    /** Opens manage-write-settings for brightness control. */
    fun openWriteSettings(): Map<String, Any?> {
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            mapOf("ok" to true)
        } catch (t: Throwable) {
            Log.w(TAG, "openWriteSettings: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"))
        }
    }

    /**
     * Best-effort enable of [OcaNotificationListener] via Secure settings
     * (works from shell / privileged; no-op for normal apps).
     */
    fun tryEnableMediaListener(): Boolean {
        if (isMediaListenerEnabled()) return true
        val component = ComponentName(context, OcaNotificationListener::class.java).flattenToString()
        return try {
            val cr = context.contentResolver
            val key = "enabled_notification_listeners"
            val cur = Settings.Secure.getString(cr, key).orEmpty()
            val parts = cur.split(':').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            if (parts.add(component)) {
                Settings.Secure.putString(cr, key, parts.joinToString(":"))
            }
            isMediaListenerEnabled()
        } catch (t: Throwable) {
            Log.d(TAG, "tryEnableMediaListener: ${t.message}")
            false
        }
    }

    override suspend fun apply(id: String, value: String): Boolean {
        return when (id) {
            ID_WIFI -> {
                val on = value == "1" || value.equals("true", true) || value.equals("on", true)
                setWifi(on)["ok"] == true
            }
            ID_BT -> {
                val on = value == "1" || value.equals("true", true) || value.equals("on", true)
                setBluetooth(on)["ok"] == true
            }
            ID_BRIGHTNESS -> {
                val n = value.toIntOrNull() ?: return false
                setBrightness(n)["ok"] == true
            }
            ID_MEDIA_PLAYER -> applyMediaPlayer(value)
            in cabinVolIds -> {
                val n = value.toIntOrNull() ?: return false
                cabinVolumes[id]?.set(n) == true
            }
            else -> false
        }
    }

    override suspend fun read(id: String): String? {
        return when (id) {
            ID_WIFI -> if (wifi?.isWifiEnabled == true) "1" else "0"
            ID_BT -> if (bluetooth?.isEnabled == true) "1" else "0"
            ID_BRIGHTNESS -> brightnessOrNull()?.toString()
            ID_MEDIA_PLAYER -> mediaSnapshot().playback
            in cabinVolIds ->
                cabinVolumes[id]?.readOrNull()?.current?.toString()
            else -> null
        }
    }

    fun entityMaps(
        i18n: I18nBundle?,
        persist: Map<String, Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        val st = status()
        fun label(key: String, fb: String) = i18n?.t(key, fb) ?: fb
        val canWrite = st["canWriteSettings"] == true
        val brightness = brightnessOrNull()
        val media = mediaSnapshot()

        val wifiBt = listOf(
            boolEntity(
                id = ID_WIFI,
                label = label("control.android_wifi", "Wi‑Fi"),
                hint = label("control.android_wifi.hint", "Pin to reapply on boot / gear"),
                enabled = st["wifiEnabled"] == true,
                available = st["wifiAvailable"] == true,
                pin = persist[ID_WIFI],
                onLabel = label("common.on", "On"),
                offLabel = label("common.off", "Off"),
                group = "android",
            ),
            boolEntity(
                id = ID_BT,
                label = label("control.android_bluetooth", "Bluetooth"),
                hint = label("control.android_bluetooth.hint", "Pin to reapply on boot / gear"),
                enabled = st["bluetoothEnabled"] == true,
                available = st["bluetoothAvailable"] == true,
                pin = persist[ID_BT],
                onLabel = label("common.on", "On"),
                offLabel = label("common.off", "Off"),
                group = "android",
            ),
        )

        val brightnessEntity = EntityContract.enrich(
            mapOf(
                "id" to ID_BRIGHTNESS,
                "group" to "android",
                "entity" to EntityType.ANDROID.id,
                "label" to label("control.android_brightness", "Brightness"),
                "hint" to label(
                    "control.android_brightness.hint",
                    "Screen brightness (0–255). Needs Modify system settings.",
                ),
                "description" to label(
                    "control.android_brightness.hint",
                    "Screen brightness (0–255). Needs Modify system settings.",
                ),
                "input" to "int",
                "icon" to "display",
                "writable" to canWrite,
                "value" to (brightness?.toString() ?: "0"),
                "valueLabel" to (brightness?.toString()),
                "min" to BRIGHTNESS_MIN,
                "max" to BRIGHTNESS_MAX,
                "step" to 5,
                "status" to if (brightness != null) "ok" else if (canWrite) "ok" else "denied",
                "needsPrivilege" to !canWrite,
                "stale" to false,
                "history" to false,
                "persistEnabled" to (persist[ID_BRIGHTNESS]?.get("enabled") == true),
                "persistValue" to (persist[ID_BRIGHTNESS]?.get("value") as? String),
                "persistLabel" to (persist[ID_BRIGHTNESS]?.get("value") as? String),
            ),
        )

        val playbackOpts = listOf(
            mapOf("value" to "playing", "label" to label("media_player.playing", "Playing")),
            mapOf("value" to "paused", "label" to label("media_player.paused", "Paused")),
            mapOf("value" to "idle", "label" to label("media_player.idle", "Idle")),
        )
        val commandOpts = listOf(
            mapOf("value" to "play", "label" to label("media_player.play", "Play")),
            mapOf("value" to "pause", "label" to label("media_player.pause", "Pause")),
            mapOf("value" to "play_pause", "label" to label("media_player.play_pause", "Play/Pause")),
            mapOf("value" to "next", "label" to label("media_player.next", "Next")),
            mapOf("value" to "previous", "label" to label("media_player.previous", "Previous")),
            mapOf("value" to "stop", "label" to label("media_player.stop", "Stop")),
        )
        val playbackLabel = when (media.playback) {
            "playing" -> label("media_player.playing", "Playing")
            "paused" -> label("media_player.paused", "Paused")
            else -> label("media_player.idle", "Idle")
        }
        // Always expose a volume range for the card UI (CarVolumeGroup 0).
        val vol = mediaCabinVolume.read()
        val mediaAttrs = linkedMapOf<String, Any?>().apply {
            media.title?.let { put("media_title", it) }
            media.artist?.let { put("media_artist", it) }
            media.album?.let { put("media_album", it) }
            media.packageName?.let { put("app_id", it) }
            put("listener_enabled", isMediaListenerEnabled())
            // Pause|Prev|Next|Play|Stop|Volume — HA supported_features subset.
            put("supported_features", MEDIA_FEATURES)
            put("options", playbackOpts)
            put("commands", commandOpts)
            put("volume", vol.current)
            put("volume_min", vol.min)
            put("volume_max", vol.max)
            val span = (vol.max - vol.min).coerceAtLeast(1)
            put("volume_level", (vol.current - vol.min).toDouble() / span)
        }
        val mediaPlayerEntity = EntityContract.enrich(
            mapOf(
                "id" to ID_MEDIA_PLAYER,
                "group" to "android",
                "entity" to EntityType.MEDIA_PLAYER.id,
                "domain" to EntityType.MEDIA_PLAYER.id,
                "label" to label("control.media_player_vehicle", "Media player"),
                "hint" to label(
                    "control.media_player_vehicle.hint",
                    "Active session (Spotify, radio, …). Metadata needs notification access.",
                ),
                "description" to label(
                    "control.media_player_vehicle.hint",
                    "Active session (Spotify, radio, …). Metadata needs notification access.",
                ),
                "input" to "media_player",
                "icon" to "sound",
                "writable" to true,
                "value" to media.playback,
                "valueLabel" to playbackLabel,
                "mediaTitle" to media.title,
                "mediaArtist" to media.artist,
                "mediaAlbum" to media.album,
                "volume" to vol.current,
                "volumeMin" to vol.min,
                "volumeMax" to vol.max,
                "min" to vol.min,
                "max" to vol.max,
                "step" to 1,
                "options" to playbackOpts,
                "status" to "ok",
                "needsPrivilege" to (!isMediaListenerEnabled() && media.title == null),
                "stale" to false,
                "history" to false,
                "attributes" to mediaAttrs,
                "persistEnabled" to (persist[ID_MEDIA_PLAYER]?.get("enabled") == true),
                "persistValue" to (persist[ID_MEDIA_PLAYER]?.get("value") as? String),
                "persistLabel" to (persist[ID_MEDIA_PLAYER]?.get("value") as? String),
            ),
        )

        val cabinVolEntities = volumeGroups.map { def ->
            val cv = cabinVolumes.getValue(def.entityId)
            val stVol = cv.read()
            val pin = persist[def.entityId]
            val labelKey = "control.${def.entityId}"
            val hintKey = "control.${def.entityId}.hint"
            EntityContract.enrich(
                mapOf(
                    "id" to def.entityId,
                    "group" to "sound",
                    "entity" to EntityType.ANDROID.id,
                    "label" to label(labelKey, def.entityId),
                    "hint" to label(hintKey, "Cabin volume group ${def.groupId}"),
                    "description" to label(hintKey, "Cabin volume group ${def.groupId}"),
                    "input" to "int",
                    "icon" to "sound",
                    "writable" to def.keyWritable,
                    "value" to stVol.current.toString(),
                    "valueLabel" to stVol.current.toString(),
                    "min" to stVol.min,
                    "max" to stVol.max,
                    "step" to 1,
                    "status" to "ok",
                    "needsPrivilege" to !def.keyWritable,
                    "stale" to false,
                    "history" to false,
                    "persistEnabled" to (pin?.get("enabled") == true),
                    "persistValue" to (pin?.get("value") as? String),
                    "persistLabel" to (pin?.get("value") as? String),
                ),
            )
        }

        return wifiBt + listOf(brightnessEntity, mediaPlayerEntity) + cabinVolEntities
    }

    private fun boolEntity(
        id: String,
        label: String,
        hint: String,
        enabled: Boolean,
        available: Boolean,
        pin: Map<String, Any?>?,
        onLabel: String,
        offLabel: String,
        group: String,
    ): Map<String, Any?> {
        val value = if (enabled) "1" else "0"
        val pinEnabled = pin?.get("enabled") == true
        val pVal = pin?.get("value") as? String
        return EntityContract.enrich(
            mapOf(
                "id" to id,
                "group" to group,
                "entity" to EntityType.ANDROID.id,
                "label" to label,
                "hint" to hint,
                "description" to hint,
                "input" to "bool",
                "icon" to "system",
                "writable" to available,
                "value" to value,
                "valueLabel" to if (enabled) onLabel else offLabel,
                "status" to if (available) "ok" else "unavailable",
                "needsPrivilege" to false,
                "stale" to false,
                "history" to false,
                "persistEnabled" to pinEnabled,
                "persistValue" to pVal,
                "persistLabel" to pVal,
            ),
        )
    }

    private fun mediaSnapshot(): MediaSnapshot =
        OcaNotificationListener.refreshFromManager(context)

    private fun brightnessOrNull(): Int? = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Throwable) {
        null
    }

    private fun isMediaListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    companion object {
        private const val TAG = "AndroidSettings"
        const val ID_WIFI = "android_wifi"
        const val ID_BT = "android_bluetooth"
        const val ID_BRIGHTNESS = "android_brightness"
        /** HU active media session — HA-style media_player domain. */
        const val ID_MEDIA_PLAYER = "media_player_vehicle"
        private const val BRIGHTNESS_MIN = 1
        private const val BRIGHTNESS_MAX = 255
        /** Pause(1)|Prev(16)|Next(32)|Stop(4096)|Play(16384)|Volume(4) — HA subset. */
        private const val MEDIA_FEATURES = 1 + 4 + 16 + 32 + 4096 + 16384

        private val BASE_IDS = setOf(ID_WIFI, ID_BT, ID_BRIGHTNESS, ID_MEDIA_PLAYER)
    }
}
