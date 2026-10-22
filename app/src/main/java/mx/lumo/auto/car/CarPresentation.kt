package mx.lumo.auto.car

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import mx.lumo.auto.PlayerHolder
import mx.lumo.auto.R

/** La interfaz que se ve en la pantalla del auto. */
class CarPresentation(
    context: Context,
    display: Display,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int
) : Presentation(context, display, R.style.Theme_Lumo_Car) {

    private lateinit var playerView: PlayerView
    private lateinit var safeArea: View
    private lateinit var controls: View
    private lateinit var emptyText: View
    private lateinit var movingOverlay: View
    private lateinit var title: TextView
    private lateinit var time: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnPlay: ImageButton

    private var ready = false
    private var pendingArea: Rect? = null
    private var pendingMoving = false
    private var controlsShown = true

    private val handler = Handler(Looper.getMainLooper())
    private val player get() = PlayerHolder.get(context)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }
    private val ticker = object : Runnable {
        override fun run() {
            refreshProgress()
            handler.postDelayed(this, 500)
        }
    }
    private val autoHide = Runnable { if (player.isPlaying) showControls(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.car_player)

        playerView = findViewById(R.id.player_view)
        safeArea = findViewById(R.id.safe_area)
        controls = findViewById(R.id.controls)
        emptyText = findViewById(R.id.empty)
        movingOverlay = findViewById(R.id.moving_overlay)
        title = findViewById(R.id.title)
        time = findViewById(R.id.time)
        progress = findViewById(R.id.progress)
        btnPlay = findViewById(R.id.btn_play)

        findViewById<View>(R.id.root).setOnClickListener { showControls(!controlsShown) }
        btnPlay.setOnClickListener { PlayerHolder.toggle(context); bump() }
        findViewById<View>(R.id.btn_back).setOnClickListener { PlayerHolder.back(context); bump() }
        findViewById<View>(R.id.btn_fwd).setOnClickListener { PlayerHolder.forward(context); bump() }

        playerView.player = player
        player.addListener(listener)
        ready = true

        pendingArea?.let { applyVisibleArea(it) }
        setMoving(pendingMoving)
        refresh()
        handler.post(ticker)
        bump()
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
            player.removeListener(listener)
            playerView.player = null
        }
        super.onStop()
    }

    fun applyVisibleArea(area: Rect) {
        if (!ready) { pendingArea = area; return }
        safeArea.setPadding(
            area.left.coerceAtLeast(0),
            area.top.coerceAtLeast(0),
            (surfaceWidth - area.right).coerceAtLeast(0),
            (surfaceHeight - area.bottom).coerceAtLeast(0)
        )
    }

    fun setMoving(moving: Boolean) {
        if (!ready) { pendingMoving = moving; return }
        movingOverlay.visibility = if (moving) View.VISIBLE else View.GONE
    }

    /** Reenvía un toque del auto a nuestras vistas. */
    fun tap(x: Float, y: Float) {
        val root = window?.decorView ?: return
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(down)
        root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    fun scrub(distanceX: Float) {
        if (!ready || player.mediaItemCount == 0) return
        PlayerHolder.seekBy(context, (-distanceX * 60).toLong())
        bump()
    }

    // --- UI ---

    private fun bump() {
        showControls(true)
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 3500)
    }

    private fun showControls(show: Boolean) {
        if (!ready) return
        controlsShown = show
        controls.animate().cancel()
        if (show) {
            controls.visibility = View.VISIBLE
            controls.animate().alpha(1f).setDuration(180).start()
            handler.removeCallbacks(autoHide)
            handler.postDelayed(autoHide, 3500)
        } else {
            controls.animate().alpha(0f).setDuration(220)
                .withEndAction { if (!controlsShown) controls.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun refresh() {
        if (!ready) return
        val p = player
        val hasMedia = p.mediaItemCount > 0
        emptyText.visibility = if (hasMedia) View.GONE else View.VISIBLE
        title.text = p.currentMediaItem?.mediaMetadata?.title ?: ""
        btnPlay.setImageResource(
            if (PlayerHolder.isActuallyPlaying(context)) R.drawable.ic_pause else R.drawable.ic_play
        )
        if (!p.isPlaying) showControls(true)
        refreshProgress()
    }

    private fun refreshProgress() {
        if (!ready) return
        val p = player
        val duration = p.duration
        val position = p.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0) {
            progress.progress = 0
            time.text = ""
            return
        }
        progress.progress = ((position * 1000) / duration).toInt()
        time.text = "${PlayerHolder.formatTime(position)} / ${PlayerHolder.formatTime(duration)}"
    }
}
