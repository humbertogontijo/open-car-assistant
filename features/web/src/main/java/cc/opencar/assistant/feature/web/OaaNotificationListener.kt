package cc.opencar.assistant.feature.web

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Optional [NotificationListenerService] so [MediaSessionManager.getActiveSessions] can
 * read now-playing metadata (title / artist / playback state).
 * Transport and cabin volume use [KeyEventInject] / [CarAudioVolume] instead —
 * [MediaController] transportControls are a no-op for Spotify on this HU.
 * Privileged [android.permission.MEDIA_CONTENT_CONTROL] also works without the listener.
 *
 * OEM / Spotify on Antora often skip [MediaController.Callback] playback edges while the
 * session stays active. [MediaController.getPlaybackState] then stays stale until we
 * re-fetch controllers from [MediaSessionManager.getActiveSessions]. Audio start/stop
 * via [AudioManager.AudioPlaybackCallback] triggers that refresh without a poll loop.
 */
class OaaNotificationListener : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var audioManager: AudioManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindLock = Any()
    @Volatile
    private var activeController: MediaController? = null

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindBest(controllers)
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Re-query sessions — cached controller state can lag OEM players.
            refreshSessions()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refreshSessions()
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    private val audioCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(
            configs: MutableList<android.media.AudioPlaybackConfiguration>?,
        ) {
            // Play/pause from the car UI often only shows up here, not on MediaController.
            refreshSessions()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        sessionManager = getSystemService(MediaSessionManager::class.java)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val component = ComponentName(this, OaaNotificationListener::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(
                sessionListener,
                component,
                mainHandler,
            )
            audioManager?.registerAudioPlaybackCallback(audioCallback, mainHandler)
            bindBest(sessionManager?.getActiveSessions(component))
        } catch (t: Throwable) {
            Log.w(TAG, "session listen failed: ${t.message}")
            publish(null)
        }
        Log.i(TAG, "notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Throwable) {
        }
        try {
            audioManager?.unregisterAudioPlaybackCallback(audioCallback)
        } catch (_: Throwable) {
        }
        unbindController()
        if (instance === this) instance = null
        snapshot = MediaSnapshot.IDLE
        Log.i(TAG, "notification listener disconnected")
    }

    private fun refreshSessions() {
        val component = ComponentName(this, OaaNotificationListener::class.java)
        try {
            bindBest(sessionManager?.getActiveSessions(component))
        } catch (t: Throwable) {
            Log.w(TAG, "refreshSessions: ${t.message}")
        }
    }

    private fun bindBest(controllers: List<MediaController>?) {
        val best = pickBest(controllers)
        // Session / audio listeners run on mainHandler, but companion
        // activeController() / refreshFromManager() can call from other threads.
        synchronized(bindLock) {
            // Prefer a fresh MediaController from getActiveSessions — a held
            // instance's getPlaybackState() goes stale when OEM skips Callback edges.
            if (best !== activeController) {
                unbindControllerLocked()
                activeController = best
                try {
                    best?.registerCallback(controllerCallback, mainHandler)
                } catch (t: Throwable) {
                    Log.w(TAG, "registerCallback: ${t.message}")
                }
            }
        }
        publish(best)
    }

    private fun unbindController() {
        synchronized(bindLock) {
            unbindControllerLocked()
        }
    }

    private fun unbindControllerLocked() {
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (_: Throwable) {
        }
        activeController = null
    }

    private fun publish(controller: MediaController?) {
        val next = MediaSnapshot.from(controller)
        val prev = snapshot
        snapshot = next
        if (next.playback != prev.playback ||
            next.title != prev.title ||
            next.artist != prev.artist
        ) {
            Log.i(
                TAG,
                "media ${prev.playback}->${next.playback} title=${next.title} pkg=${next.packageName}",
            )
        }
        if (next.playback != prev.playback) {
            WebEventHub.emitEntity(
                AndroidSettingsController.ID_MEDIA_PLAYER,
                next.playback,
                status = "ok",
            )
        }
        if (next.title != prev.title ||
            next.artist != prev.artist ||
            next.album != prev.album ||
            next.packageName != prev.packageName
        ) {
            WebEventHub.emitCatalog("media_meta")
        }
    }

    companion object {
        private const val TAG = "OaaMediaListener"

        @Volatile
        var instance: OaaNotificationListener? = null
            private set

        @Volatile
        var snapshot: MediaSnapshot = MediaSnapshot.IDLE
            private set

        /** Best active [MediaController] for metadata (needs listener or MEDIA_CONTENT_CONTROL). */
        fun activeController(context: android.content.Context): MediaController? {
            val component = ComponentName(context, OaaNotificationListener::class.java)
            val listener = instance
            return try {
                val mgr = context.getSystemService(MediaSessionManager::class.java)
                val sessions = if (listener != null) {
                    mgr?.getActiveSessions(component)
                } else {
                    mgr?.getActiveSessions(null)
                }
                pickBest(sessions)?.also { best ->
                    listener?.bindBest(sessions)
                }
            } catch (_: SecurityException) {
                null
            } catch (t: Throwable) {
                Log.w(TAG, "activeController: ${t.message}")
                null
            }
        }

        fun refreshFromManager(context: android.content.Context): MediaSnapshot {
            val controller = activeController(context)
            val next = MediaSnapshot.from(controller)
            val prev = snapshot
            snapshot = next
            if (next.playback != prev.playback) {
                Log.i(TAG, "refresh ${prev.playback}->${next.playback} title=${next.title}")
                WebEventHub.emitEntity(
                    AndroidSettingsController.ID_MEDIA_PLAYER,
                    next.playback,
                    status = "ok",
                )
            }
            if (next.title != prev.title ||
                next.artist != prev.artist ||
                next.album != prev.album ||
                next.packageName != prev.packageName
            ) {
                WebEventHub.emitCatalog("media_meta")
            }
            return next
        }

        private fun pickBest(controllers: List<MediaController>?): MediaController? {
            if (controllers.isNullOrEmpty()) return null
            fun rank(c: MediaController): Int {
                val state = c.playbackState?.state ?: PlaybackState.STATE_NONE
                val playing = state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_BUFFERING ||
                    state == PlaybackState.STATE_FAST_FORWARDING ||
                    state == PlaybackState.STATE_REWINDING
                val paused = state == PlaybackState.STATE_PAUSED
                val hasMeta = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?.isNotBlank() == true
                return when {
                    playing && hasMeta -> 5
                    playing -> 4
                    paused && hasMeta -> 3
                    paused -> 2
                    hasMeta -> 1
                    else -> 0
                }
            }
            return controllers.maxByOrNull { rank(it) }
        }
    }
}

data class MediaSnapshot(
    val packageName: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** `playing` | `paused` | `idle` */
    val playback: String,
) {
    companion object {
        val IDLE = MediaSnapshot(null, null, null, null, "idle")

        fun from(controller: MediaController?): MediaSnapshot {
            if (controller == null) return IDLE
            val md = controller.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
            val playback = when (state) {
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_FAST_FORWARDING,
                PlaybackState.STATE_REWINDING,
                PlaybackState.STATE_SKIPPING_TO_NEXT,
                PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
                -> "playing"
                PlaybackState.STATE_PAUSED -> "paused"
                else -> if (title != null) "paused" else "idle"
            }
            return MediaSnapshot(
                packageName = controller.packageName,
                title = title,
                artist = artist,
                album = album,
                playback = playback,
            )
        }
    }
}
