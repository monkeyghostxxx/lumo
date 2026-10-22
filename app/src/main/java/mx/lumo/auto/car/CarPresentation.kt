package mx.lumo.auto.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import mx.lumo.auto.R
import mx.lumo.auto.music.NowPlaying
import kotlin.math.abs

/** Lo que se ve en la pantalla del auto: la página de orilla a orilla + dock de vidrio. */
class CarPresentation(
    context: Context,
    display: Display,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int
) : Presentation(context, display, R.style.Theme_Lumo_Car), CarBrowser.Host {

    private lateinit var webContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var movingOverlay: View
    private lateinit var dock: View
    private lateinit var toast: TextView
    private lateinit var music: View
    private lateinit var mArt: ImageView
    private lateinit var mTitle: TextView
    private lateinit var mArtist: TextView
    private lateinit var mPlay: ImageButton

    private var ready = false
    private var pendingMoving = false
    private var dockShown = true
    private val handler = Handler(Looper.getMainLooper())

    private val hideDock = Runnable { setDockVisible(false) }
    private val hideToast = Runnable { toast.animate().alpha(0f).setDuration(250).start() }
    private val musicListener: (NowPlaying.Track?) -> Unit = { showTrack(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.car_browser)
        webContainer = findViewById(R.id.web_container)
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        progress = findViewById(R.id.progress)
        movingOverlay = findViewById(R.id.moving_overlay)
        dock = findViewById(R.id.dock)
        toast = findViewById(R.id.toast)
        music = findViewById(R.id.music)
        mArt = findViewById(R.id.m_art)
        mTitle = findViewById(R.id.m_title)
        mArtist = findViewById(R.id.m_artist)
        mPlay = findViewById(R.id.m_play)

        findViewById<View>(R.id.btn_back).setOnClickListener { CarBrowser.back(); poke() }
        findViewById<View>(R.id.btn_home).setOnClickListener { CarBrowser.home(context); poke() }
        findViewById<View>(R.id.btn_lyrics).setOnClickListener { toggleLyrics(); poke() }
        findViewById<View>(R.id.m_prev).setOnClickListener { NowPlaying.previous(); poke() }
        mPlay.setOnClickListener { NowPlaying.toggle(); poke() }
        findViewById<View>(R.id.m_next).setOnClickListener { NowPlaying.next(); poke() }

        val web = CarBrowser.attach(context, this)
        webContainer.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        ready = true
        NowPlaying.start(context, musicListener)
        setMoving(pendingMoving)
        poke()
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
            NowPlaying.stop(musicListener)
            CarBrowser.detach()
            fullscreenContainer.removeAllViews()
        }
        super.onStop()
    }

    // --- CarBrowser.Host ---

    override fun showFullscreen(view: View) {
        if (!ready) return
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenContainer.removeAllViews()
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        fullscreenContainer.visibility = View.VISIBLE
        webContainer.visibility = View.INVISIBLE
        handler.removeCallbacks(hideDock)
        handler.postDelayed(hideDock, 1500) // en pantalla completa, solo el video
    }

    override fun hideFullscreen() {
        if (!ready) return
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE
        webContainer.visibility = View.VISIBLE
        poke()
    }

    override fun setProgress(percent: Int) {
        if (!ready) return
        progress.progress = percent
        progress.animate().cancel()
        if (percent >= 100) progress.animate().alpha(0f).setDuration(250).start()
        else progress.alpha = 1f
    }

    fun setMoving(moving: Boolean) {
        if (!ready) { pendingMoving = moving; return }
        // Solo se tapa y pausa el video; la música (Spotify) sigue sonando.
        movingOverlay.visibility = if (moving) View.VISIBLE else View.GONE
    }

    // --- Dock ---

    private fun poke() {
        setDockVisible(true)
        handler.removeCallbacks(hideDock)
        handler.postDelayed(hideDock, if (CarBrowser.isFullscreen) 2500L else 5000L)
    }

    private fun setDockVisible(show: Boolean) {
        if (!ready || show == dockShown) return
        dockShown = show
        dock.animate().cancel()
        if (show) {
            dock.visibility = View.VISIBLE
            dock.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(220).start()
        } else {
            dock.animate().alpha(0f).translationY(24f).scaleX(0.96f).scaleY(0.96f)
                .setDuration(260)
                .withEndAction { if (!dockShown) dock.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun showTrack(track: NowPlaying.Track?) {
        if (!ready) return
        if (track == null) {
            music.visibility = View.GONE
            return
        }
        music.visibility = View.VISIBLE
        mTitle.text = track.title
        mArtist.text = track.artist
        if (track.art != null) mArt.setImageBitmap(track.art) else mArt.setImageResource(R.drawable.ic_music)
        mPlay.setImageResource(if (track.playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun toggleLyrics() {
        CarBrowser.toggleLyrics { state ->
            showToast(
                when (state) {
                    "on" -> "Letra activada"
                    "off" -> "Letra desactivada"
                    "novideo" -> "Abre un video primero"
                    else -> "Este video no trae letra"
                }
            )
        }
    }

    private fun showToast(text: String) {
        if (!ready) return
        toast.text = text
        toast.animate().cancel()
        toast.animate().alpha(1f).setDuration(180).start()
        handler.removeCallbacks(hideToast)
        handler.postDelayed(hideToast, 1800)
    }

    // --- Entrada táctil del auto ---

    fun tap(x: Float, y: Float) {
        finishDrag()
        val wasHidden = !dockShown
        poke()
        // Si el dock estaba oculto y tocaste justo donde aparece, solo lo mostramos.
        if (wasHidden && hitsDock(x, y)) return
        val now = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, now, now, x, y)
        send(MotionEvent.ACTION_UP, now, now + 40, x, y)
    }

    private fun hitsDock(x: Float, y: Float): Boolean =
        x >= dock.left && x <= dock.right && y >= dock.top && y <= dock.bottom

    /*
     * Android Auto manda el desplazamiento en pedacitos. Los convertimos en un
     * arrastre real para que la página haga scroll natural, y solo empezamos
     * cuando el movimiento es claro (un roce nunca se vuelve toque).
     */
    private var dragging = false
    private var dragDown = 0L
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var curX = 0f
    private var curY = 0f
    private val endDrag = Runnable { finishDrag() }
    private val resetPending = Runnable { pendingDx = 0f; pendingDy = 0f }

    fun scroll(distanceX: Float, distanceY: Float) {
        handler.removeCallbacks(endDrag)
        handler.removeCallbacks(resetPending)
        if (!dragging) {
            pendingDx += distanceX
            pendingDy += distanceY
            if (abs(pendingDx) < START_PX && abs(pendingDy) < START_PX) {
                handler.postDelayed(resetPending, END_MS)
                return
            }
            dragging = true
            dragDown = SystemClock.uptimeMillis()
            curX = surfaceWidth / 2f
            curY = surfaceHeight / 2f
            send(MotionEvent.ACTION_DOWN, dragDown, dragDown, curX, curY)
            curX -= pendingDx
            curY -= pendingDy
            pendingDx = 0f
            pendingDy = 0f
        } else {
            curX -= distanceX
            curY -= distanceY
        }
        send(MotionEvent.ACTION_MOVE, dragDown, SystemClock.uptimeMillis(), curX, curY)
        handler.postDelayed(endDrag, END_MS)
    }

    private fun finishDrag() {
        handler.removeCallbacks(endDrag)
        handler.removeCallbacks(resetPending)
        pendingDx = 0f
        pendingDy = 0f
        if (!dragging) return
        dragging = false
        send(MotionEvent.ACTION_UP, dragDown, SystemClock.uptimeMillis(), curX, curY)
    }

    private fun send(action: Int, downTime: Long, time: Long, x: Float, y: Float) {
        val root = window?.decorView ?: return
        val e = MotionEvent.obtain(downTime, time, action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(e)
        e.recycle()
    }

    private companion object {
        const val START_PX = 24f
        const val END_MS = 120L
    }
}
