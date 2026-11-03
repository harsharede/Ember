package com.harsharede.ember

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's playing now (YouTube, Spotify, podcasts…), for the Now Playing card. Uses notification access, nothing more. */
object NowPlaying {
    data class Info(val packageName: String, val title: String, val artist: String, val art: ImageBitmap?, val playing: Boolean, val controller: MediaController)

    private val _info = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> = _info.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var listenerName: ComponentName? = null
    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null; _info.value = null
            // Another app may still be playing: look again.
            runCatching { pick(manager!!.getActiveSessions(listenerName)) }
        }
    }

    fun start(context: Context, listener: ComponentName) {
        val m = context.getSystemService(MediaSessionManager::class.java)
        manager = m
        listenerName = listener
        appContext = context.applicationContext
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            pick(m.getActiveSessions(listener))
        }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback); controller = null
        _info.value = null
    }

    /** The playing session if there is one, otherwise the most recent. */
    private fun pick(sessions: List<MediaController>) {
        // An empty list doesn't clear a player found through its notification: that one reports its own end.
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
            ?: run { publish(); return }
        if (best?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, handler)
        publish()
    }

    /**
     * A media session found on a notification (the player's own notification carries it). Some apps' sessions don't show
     * up in the active list on every phone, so this is a second way in. A playing one wins over a paused one.
     */
    fun offer(context: Context, token: MediaSession.Token) {
        if (controller?.sessionToken == token) return
        val c = runCatching { MediaController(context.applicationContext, token) }.getOrNull() ?: return
        val current = controller
        val currentPlaying = current?.playbackState?.state == PlaybackState.STATE_PLAYING
        val newPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (current != null && (currentPlaying || !newPlaying)) return
        current?.unregisterCallback(callback)
        controller = c
        c.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { _info.value = null; return }
        val state = c.playbackState?.state
        val meta = c.metadata
        val live = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_BUFFERING
        // Nothing to show only when there's neither a track nor a player that's playing or paused.
        if (meta == null && !live) { _info.value = null; return }
        // Many video apps leave the title empty between videos: show the app's name rather than hiding the controls.
        val appName = runCatching { EmberApp.instance.apps.labelForPackage(c.packageName) }.getOrDefault(c.packageName)
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() } ?: appName
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val art = (meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let { b ->
            val s = 320
            (if (b.width > s) Bitmap.createScaledBitmap(b, s, (b.height * s / b.width).coerceAtLeast(1), true) else b).asImageBitmap()
        }
        _info.value = Info(c.packageName, title, artist, art, state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING, c)
    }
}
