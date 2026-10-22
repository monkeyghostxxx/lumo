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
import android.widget.ProgressBar
import mx.lumo.auto.R
import kotlin.math.abs

/** Lo que se ve en la pantalla del auto: una sola vista, de orilla a orilla. */
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

    private var ready = false
    private var pendingMoving = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.car_browser)
        webContainer = findViewById(R.id.web_container)
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        progress = findViewById(R.id.progress)
        movingOverlay = findViewById(R.id.moving_overlay)

        val web = CarBrowser.attach(context, this)
        webContainer.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        ready = true
        setMoving(pendingMoving)
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
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
    }

    override fun hideFullscreen() {
        if (!ready) return
        fullscreenContainer.removeAllViews()
        fullscreenContainer.visibility = View.GONE
        webContainer.visibility = View.VISIBLE
    }

    override fun setProgress(percent: Int) {
        if (!ready) return
        progress.progress = percent
        progress.animate().cancel()
        if (percent >= 100) {
            progress.animate().alpha(0f).setDuration(250).start()
        } else {
            progress.alpha = 1f
        }
    }

    fun setMoving(moving: Boolean) {
        if (!ready) { pendingMoving = moving; return }
        movingOverlay.visibility = if (moving) View.VISIBLE else View.GONE
    }

    // --- Entrada táctil del auto ---

    fun tap(x: Float, y: Float) {
        finishDrag()
        val now = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, now, now, x, y)
        send(MotionEvent.ACTION_UP, now, now + 40, x, y)
    }

    /*
     * Android Auto manda el desplazamiento en pedacitos. Los convertimos en un
     * arrastre real (dedo abajo → mover → dedo arriba) para que cualquier página
     * haga scroll de forma natural. Solo se empieza cuando el movimiento es claro,
     * así un roce mínimo nunca se convierte en un toque accidental.
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
