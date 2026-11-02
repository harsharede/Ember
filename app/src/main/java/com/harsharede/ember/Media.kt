package com.harsharede.ember

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
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
    private val handler = Handler(Looper.getMainLooper())
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { controller = null; _info.value = null }
    }

    fun start(context: Context, listener: ComponentName) {
        val m = context.getSystemService(MediaSessionManager::class.java)
        manager = m
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
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { _info.value = null; return }
        val meta = c.metadata ?: run { _info.value = null; return }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) { _info.value = null; return }
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val art = (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let { b ->
            val s = 320
            (if (b.width > s) Bitmap.createScaledBitmap(b, s, (b.height * s / b.width).coerceAtLeast(1), true) else b).asImageBitmap()
        }
        _info.value = Info(c.packageName, title, artist, art, c.playbackState?.state == PlaybackState.STATE_PLAYING, c)
    }
}
