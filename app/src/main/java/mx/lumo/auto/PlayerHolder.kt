package mx.lumo.auto

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * Un solo reproductor para toda la app: el teléfono elige el video
 * y la pantalla del auto lo muestra al instante.
 */
object PlayerHolder {
    private const val SEEK_MS = 10_000L

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    fun get(context: Context): ExoPlayer {
        player?.let { return it }
        val app = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        // Permite usar los botones del volante (play/pausa/siguiente).
        session = runCatching { MediaSession.Builder(app, p).build() }.getOrNull()
        player = p
        return p
    }

    fun play(context: Context, items: List<VideoItem>, index: Int) {
        if (items.isEmpty()) return
        val p = get(context)
        p.setMediaItems(items.map { it.toMediaItem() }, index.coerceIn(0, items.lastIndex), 0L)
        p.prepare()
        p.play()
    }

    fun toggle(context: Context) {
        val p = get(context)
        when {
            p.mediaItemCount == 0 -> Unit
            p.isPlaying -> p.pause()
            else -> {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                p.play()
            }
        }
    }

    fun seekBy(context: Context, deltaMs: Long) {
        val p = get(context)
        if (p.mediaItemCount == 0) return
        val duration = p.duration
        var target = p.currentPosition + deltaMs
        if (target < 0) target = 0
        if (duration != C.TIME_UNSET && target > duration) target = duration
        p.seekTo(target)
    }

    fun back(context: Context) = seekBy(context, -SEEK_MS)
    fun forward(context: Context) = seekBy(context, SEEK_MS)

    fun isActuallyPlaying(context: Context): Boolean {
        val p = get(context)
        return p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
    }

    fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
