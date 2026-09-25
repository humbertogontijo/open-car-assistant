package cc.opencar.assistant.feature.web

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Optional [NotificationListenerService] so [MediaSessionManager.getActiveSessions] can
 * read now-playing metadata (title / artist / playback state).
 * Transport and cabin volume use [KeyEventInject] / [CarAudioVolume] instead —
 * [MediaController] transportControls are a no-op for Spotify on this HU.
 * Privileged [android.permission.MEDIA_CONTENT_CONTROL] also works without the listener.
 */
class OcaNotificationListener : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    @Volatile
    private var activeController: MediaController? = null

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindBest(controllers)
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publish(activeController)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, OcaNotificationListener::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
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
        unbindController()
        if (instance === this) instance = null
        snapshot = MediaSnapshot.IDLE
        Log.i(TAG, "notification listener disconnected")
    }

    private fun bindBest(controllers: List<MediaController>?) {
        val best = pickBest(controllers)
        if (best?.sessionToken == activeController?.sessionToken) {
            publish(activeController)
            return
        }
        unbindController()
        activeController = best
        try {
            best?.registerCallback(controllerCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "registerCallback: ${t.message}")
        }
        publish(best)
    }

    private fun unbindController() {
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (_: Throwable) {
        }
        activeController = null
    }

    private fun publish(controller: MediaController?) {
        snapshot = MediaSnapshot.from(controller)
    }

    companion object {
        private const val TAG = "OcaMediaListener"

        @Volatile
        var instance: OcaNotificationListener? = null
            private set

        @Volatile
        var snapshot: MediaSnapshot = MediaSnapshot.IDLE
            private set

        /** Best active [MediaController] for metadata (needs listener or MEDIA_CONTENT_CONTROL). */
        fun activeController(context: android.content.Context): MediaController? {
            val listener = instance
            if (listener != null) {
                listener.activeController?.let { return it }
                val component = ComponentName(context, OcaNotificationListener::class.java)
                return try {
                    val mgr = context.getSystemService(MediaSessionManager::class.java)
                    pickBest(mgr?.getActiveSessions(component))?.also { best ->
                        listener.bindBest(listOf(best))
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "activeController: ${t.message}")
                    null
                }
            }
            return try {
                val mgr = context.getSystemService(MediaSessionManager::class.java)
                pickBest(mgr?.getActiveSessions(null))
            } catch (_: SecurityException) {
                null
            } catch (t: Throwable) {
                Log.w(TAG, "activeController(null): ${t.message}")
                null
            }
        }

        fun refreshFromManager(context: android.content.Context): MediaSnapshot {
            val controller = activeController(context)
            return MediaSnapshot.from(controller).also { snapshot = it }
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
