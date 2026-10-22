package mx.lumo.auto.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat

/** Lo que suena en Spotify (o cualquier app de música) y sus controles. */
object NowPlaying {

    data class Track(
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val playing: Boolean,
        val pkg: String
    )

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val listeners = mutableSetOf<(Track?) -> Unit>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = emit()
        override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
        override fun onSessionDestroyed() = setController(null)
    }

    /** ¿La app oficial de YouTube está reproduciendo ahora? null = no lo sabemos. */
    fun youtubePlaying(): Boolean? {
        if (manager == null) return null
        val c = controller ?: return false
        return c.packageName == YOUTUBE && c.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start(context: Context, listener: (Track?) -> Unit) {
        listeners += listener
        if (manager != null) {
            listener(current())
            return
        }
        if (!hasAccess(context)) {
            listener(null)
            return
        }
        val app = context.applicationContext
        appContext = app
        val m = app.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(app, MusicListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            manager = m
            pick(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            listener(null)
        }
    }

    fun stop(listener: (Track?) -> Unit) {
        listeners -= listener
        if (listeners.isNotEmpty()) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        manager = null
        setController(null)
    }

    fun toggle() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    fun pause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
    }

    private fun pick(list: List<MediaController>) {
        val own = appContext?.packageName
        val candidates = list.filter { it.packageName != own }
        val best = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull { it.packageName == SPOTIFY }
            ?: candidates.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) setController(best) else emit()
    }

    private fun setController(c: MediaController?) {
        controller?.let { runCatching { it.unregisterCallback(callback) } }
        controller = c
        c?.registerCallback(callback, main)
        emit()
    }

    private fun current(): Track? {
        val c = controller ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return Track(title, artist, art, playing, c.packageName)
    }

    private fun emit() {
        val t = current()
        listeners.toList().forEach { it(t) }
    }

    private const val SPOTIFY = "com.spotify.music"
    private const val YOUTUBE = "com.google.android.youtube"
}
