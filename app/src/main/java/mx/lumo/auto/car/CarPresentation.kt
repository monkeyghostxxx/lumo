package mx.lumo.auto.car

import android.app.Presentation
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.InputDevice
import android.view.Surface
import android.view.TextureView
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import mx.lumo.auto.R
import mx.lumo.auto.mirror.Mirror
import mx.lumo.auto.music.NowPlaying
import mx.lumo.auto.ui.Accent
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min

/** Pantalla del auto: página de orilla a orilla + dock de vidrio y capas. */
class CarPresentation(
    context: Context,
    display: Display,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int
) : Presentation(context, display, R.style.Theme_Lumo_Car), CarBrowser.Host {

    private lateinit var webContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var audioOverlay: View
    private lateinit var audioLabel: TextView
    private lateinit var audioTitle: TextView
    private lateinit var ambient: FrameLayout
    private lateinit var clock: ClockView
    private lateinit var relatedSheet: View
    private lateinit var relatedList: LinearLayout
    private lateinit var dock: View
    private lateinit var toast: TextView
    private lateinit var music: View
    private lateinit var mArt: ImageView
    private lateinit var mTitle: TextView
    private lateinit var mArtist: TextView
    private lateinit var mPlay: ImageButton
    private lateinit var mirrorView: TextureView
    private var mirrorSurface: Surface? = null
    private var mirroring = false
    private var autoZoom = true
    private var zoomed = false
    private var zoomAnim: ValueAnimator? = null
    private val zoomTick = object : Runnable {
        override fun run() {
            if (!mirroring) return
            updateZoom()
            handler.postDelayed(this, 700)
        }
    }

    private var ready = false
    private var pendingMoving = false
    private var moving = false
    private var dockShown = true
    private var ambientShown = false
    private var relatedShown = false
    private var lastInteraction = SystemClock.uptimeMillis()
    private var wasEnded = false
    private var musicPlaying = false

    private val handler = Handler(Looper.getMainLooper())
    private val density get() = context.resources.displayMetrics.density

    private val hideDock = Runnable { setDockVisible(false) }
    private val hideToast = Runnable { toast.animate().alpha(0f).translationY(-8f).setDuration(260).start() }
    private val musicListener: (NowPlaying.Track?) -> Unit = { showTrack(it) }
    private val stateListener: (CarBrowser.State?) -> Unit = { onState(it) }
    private val accentListener: (Int) -> Unit = { applyAccent(it) }
    private val mirrorListener: (Boolean) -> Unit = { setMirroring(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.car_browser)
        webContainer = findViewById(R.id.web_container)
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        progress = findViewById(R.id.progress)
        audioOverlay = findViewById(R.id.audio_overlay)
        audioLabel = findViewById(R.id.audio_label)
        audioTitle = findViewById(R.id.audio_title)
        ambient = findViewById(R.id.ambient)
        relatedSheet = findViewById(R.id.related_sheet)
        relatedList = findViewById(R.id.related_list)
        dock = findViewById(R.id.dock)
        toast = findViewById(R.id.toast)
        music = findViewById(R.id.music)
        mArt = findViewById(R.id.m_art)
        mTitle = findViewById(R.id.m_title)
        mArtist = findViewById(R.id.m_artist)
        mPlay = findViewById(R.id.m_play)
        mirrorView = findViewById(R.id.mirror_view)
        mirrorView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                st.setDefaultBufferSize(surfaceWidth, surfaceHeight)
                mirrorSurface = Surface(st)
                connectMirror()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                Mirror.detach()
                mirrorSurface?.release()
                mirrorSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }

        clock = ClockView(context)
        clock.setSizePx(surfaceHeight * 0.34f)
        ambient.addView(clock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))

        click(R.id.btn_back) { CarBrowser.back() }
        click(R.id.btn_home) { CarBrowser.home(context) }
        click(R.id.btn_related) { showRelated() }
        click(R.id.btn_lyrics) { toggleLyrics() }
        click(R.id.btn_clock) { toggleAmbient() }
        click(R.id.m_prev) { NowPlaying.previous() }
        click(R.id.m_play) { NowPlaying.toggle() }
        click(R.id.m_next) { NowPlaying.next() }
        click(R.id.related_close) { hideRelated() }
        click(R.id.btn_mirror_stop) { Mirror.stop() }
        click(R.id.btn_zoom) {
            autoZoom = !autoZoom
            showToast(if (autoZoom) "Zoom al video: automático" else "Zoom al video: apagado")
            updateZoom()
        }

        val web = CarBrowser.attach(context, this)
        webContainer.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        ready = true

        applyAccent(Accent.color(context))
        Accent.listen(accentListener)
        NowPlaying.start(context, musicListener)
        CarBrowser.listenState(stateListener)
        Mirror.listen(mirrorListener)
        setMoving(pendingMoving)
        poke()
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        if (ready) {
            clock.stop()
            Mirror.unlisten(mirrorListener)
            Mirror.detach()
            Accent.unlisten(accentListener)
            CarBrowser.unlistenState(stateListener)
            NowPlaying.stop(musicListener)
            CarBrowser.detach()
            fullscreenContainer.removeAllViews()
        }
        super.onStop()
    }

    private fun click(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { v ->
            bounce(v)
            action()
            poke()
        }
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
        handler.postDelayed(hideDock, 1500)
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
        if (percent >= 100) progress.animate().alpha(0f).setDuration(300).start()
        else progress.alpha = 1f
    }

    override fun showRelated() {
        if (!ready) return
        CarBrowser.related { list ->
            if (list.isEmpty()) {
                showToast("No hay relacionados en esta página")
                return@related
            }
            fillRelated(list)
            if (!relatedShown) {
                relatedShown = true
                relatedSheet.visibility = View.VISIBLE
                relatedSheet.alpha = 0f
                relatedSheet.translationY = 120 * density
                relatedSheet.animate().alpha(1f).setDuration(200).start()
                spring(relatedSheet, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.STIFFNESS_LOW, 0.78f)
            }
            findViewById<View>(R.id.related_scroll).scrollTo(0, 0)
        }
    }

    override fun toggleAmbient() {
        if (ambientShown) hideAmbient() else showAmbient()
    }

    override fun toggleLyrics() {
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

    // --- Espejo de YouTube (app oficial, con Premium) ---

    private fun setMirroring(on: Boolean) {
        if (!ready || on == mirroring) return
        mirroring = on
        listOf(R.id.btn_back, R.id.btn_home, R.id.btn_related, R.id.btn_lyrics).forEach {
            findViewById<View>(it).visibility = if (on) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btn_mirror_stop).visibility = if (on) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_zoom).visibility = if (on) View.VISIBLE else View.GONE
        zoomAnim?.cancel()
        zoomed = false
        mirrorView.setTransform(null)
        handler.removeCallbacks(zoomTick)
        if (on) handler.post(zoomTick)
        if (on) {
            CarBrowser.pauseMedia()
            hideRelated()
            hideAmbient()
            mirrorView.visibility = View.VISIBLE
            mirrorView.alpha = 0f
            mirrorView.animate().alpha(1f).setDuration(400).start()
            webContainer.visibility = View.INVISIBLE
            connectMirror()
            showToast("Espejo activo · usa YouTube en tu teléfono")
        } else {
            mirrorView.animate().alpha(0f).setDuration(250)
                .withEndAction { if (!mirroring) mirrorView.visibility = View.GONE }.start()
            webContainer.visibility = View.VISIBLE
            showToast("Espejo detenido")
        }
        poke()
    }

    /*
     * Zoom automático: si el teléfono está vertical y YouTube está reproduciendo,
     * el reproductor ocupa solo la franja de arriba de la pantalla del teléfono.
     * Ampliamos esa franja para que llene la pantalla del auto. Al pausar
     * (para buscar otro video) vuelve a verse la app completa.
     */
    private fun updateZoom() {
        val phone = phoneSize()
        val portrait = phone.y > phone.x
        val want = autoZoom && portrait && (NowPlaying.youtubePlaying() ?: true)
        if (want == zoomed) return
        zoomed = want
        animateZoom(if (want) videoMatrix(phone) else Matrix())
    }

    private fun animateZoom(target: Matrix) {
        zoomAnim?.cancel()
        val from = FloatArray(9).also { mirrorView.getTransform(Matrix()).getValues(it) }
        val to = FloatArray(9).also { target.getValues(it) }
        val now = FloatArray(9)
        val m = Matrix()
        zoomAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            interpolator = android.view.animation.PathInterpolator(0.2f, 0.9f, 0.1f, 1f)
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                for (i in 0 until 9) now[i] = from[i] + (to[i] - from[i]) * f
                m.setValues(now)
                mirrorView.setTransform(m)
            }
            start()
        }
    }

    private fun videoMatrix(phone: Point): Matrix {
        val pw = phone.x.toFloat()
        val ph = phone.y.toFloat()
        val w = surfaceWidth.toFloat()
        val h = surfaceHeight.toFloat()
        // Cómo queda el teléfono dentro de la pantalla del auto (centrado y ajustado)
        val k = min(w / pw, h / ph)
        val ox = (w - pw * k) / 2f
        val oy = (h - ph * k) / 2f
        // Franja del reproductor de YouTube: justo debajo de la barra de estado, 16:9
        val rx = ox
        val ry = oy + statusBarPx() * k
        val rw = pw * k
        val rh = pw * 9f / 16f * k
        val s = min(w / rw, h / rh)
        return Matrix().apply {
            postTranslate(-rx, -ry)
            postScale(s, s)
            postTranslate((w - rw * s) / 2f, (h - rh * s) / 2f)
        }
    }

    private fun phoneSize(): Point {
        val p = Point()
        val d = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        d?.getRealSize(p)
        if (p.x == 0 || p.y == 0) p.set(1080, 2340)
        return p
    }

    private fun statusBarPx(): Int {
        val res = context.applicationContext.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }

    private fun connectMirror() {
        val surface = mirrorSurface ?: return
        if (!mirroring) return
        val dpi = context.resources.displayMetrics.densityDpi
        if (!Mirror.attach(surface, surfaceWidth, surfaceHeight, dpi)) {
            showToast("No se pudo iniciar el espejo")
        }
    }

    // --- Movimiento: modo audio ---

    fun setMoving(value: Boolean) {
        if (!ready) { pendingMoving = value; return }
        moving = value
        if (value) {
            hideRelated()
            audioOverlay.visibility = View.VISIBLE
            audioOverlay.alpha = 0f
            audioOverlay.animate().alpha(1f).setDuration(350).start()
        } else {
            audioOverlay.animate().alpha(0f).setDuration(350)
                .withEndAction { if (!moving) audioOverlay.visibility = View.GONE }.start()
        }
    }

    // --- Estado del video ---

    private fun onState(s: CarBrowser.State?) {
        if (!ready) return
        audioTitle.text = s?.title?.takeIf { it.isNotBlank() } ?: "Sin video"
        if (s?.playing == true) {
            lastInteraction = SystemClock.uptimeMillis()
            if (ambientShown) hideAmbient()
        }
        // Al terminar un video, muestra los relacionados.
        val ended = s?.ended == true
        if (ended && !wasEnded && !moving) showRelated()
        wasEnded = ended

        // Sin nada sonando por 2 minutos → modo ambiente.
        val idle = SystemClock.uptimeMillis() - lastInteraction > 120_000
        if (idle && s?.playing != true && !musicPlaying && !ambientShown && !moving && !mirroring) showAmbient()
    }

    // --- Dock ---

    private fun poke() {
        lastInteraction = SystemClock.uptimeMillis()
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
            dock.animate().alpha(1f).setDuration(180).start()
            dock.scaleX = 0.92f
            dock.scaleY = 0.92f
            spring(dock, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.STIFFNESS_LOW, 0.62f)
            spring(dock, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, 0.55f)
            spring(dock, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, 0.55f)
        } else {
            dock.animate().alpha(0f).translationY(36 * density).scaleX(0.94f).scaleY(0.94f)
                .setDuration(280)
                .withEndAction { if (!dockShown) dock.visibility = View.INVISIBLE }
                .start()
        }
    }

    // --- Relacionados ---

    private fun fillRelated(list: List<CarBrowser.Related>) {
        relatedList.removeAllViews()
        val w = (232 * density).toInt()
        val h = (w * 9f / 16f).toInt()
        list.forEach { item ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                isClickable = true
                setOnClickListener { v ->
                    bounce(v)
                    CarBrowser.open(context, item.url)
                    hideRelated()
                }
            }
            val img = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_art)
                clipToOutline = true
            }
            card.addView(img, LinearLayout.LayoutParams(w, h))
            val title = TextView(context).apply {
                text = item.title
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xE6FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            card.addView(title, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            })
            relatedList.addView(card)
            Thumbs.load(item.thumb, img)
        }
    }

    private fun hideRelated() {
        if (!relatedShown) return
        relatedShown = false
        relatedSheet.animate().alpha(0f).translationY(80 * density).setDuration(240)
            .withEndAction { if (!relatedShown) relatedSheet.visibility = View.GONE }.start()
    }

    // --- Modo ambiente ---

    private fun showAmbient() {
        if (ambientShown) return
        ambientShown = true
        hideRelated()
        ambient.visibility = View.VISIBLE
        ambient.animate().alpha(1f).setDuration(600).start()
        clock.start()
        setDockVisible(false)
    }

    private fun hideAmbient() {
        if (!ambientShown) return
        ambientShown = false
        lastInteraction = SystemClock.uptimeMillis()
        ambient.animate().alpha(0f).setDuration(400)
            .withEndAction { if (!ambientShown) { ambient.visibility = View.GONE; clock.stop() } }.start()
    }

    // --- Música ---

    private fun showTrack(track: NowPlaying.Track?) {
        if (!ready) return
        musicPlaying = track?.playing == true
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

    // --- Estilo ---

    private fun applyAccent(color: Int) {
        if (!ready) return
        val tint = ColorStateList.valueOf(color)
        progress.progressTintList = tint
        mPlay.backgroundTintList = tint
        audioLabel.setTextColor(color)
        clock.setAccent(color)
    }

    private fun showToast(text: String) {
        if (!ready) return
        toast.text = text
        toast.animate().cancel()
        toast.translationY = -8f
        toast.animate().alpha(1f).translationY(0f).setDuration(220).start()
        handler.removeCallbacks(hideToast)
        handler.postDelayed(hideToast, 1800)
    }

    private fun bounce(v: View) {
        v.scaleX = 0.84f
        v.scaleY = 0.84f
        spring(v, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, 0.42f)
        spring(v, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, 0.42f)
    }

    private fun spring(
        v: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        stiffness: Float,
        damping: Float
    ) {
        val anim = SpringAnimation(v, property, target)
        anim.spring.setStiffness(stiffness).setDampingRatio(damping)
        anim.start()
    }

    // --- Entrada táctil del auto ---

    fun tap(x: Float, y: Float) {
        finishDrag()
        // Un toque en el reloj solo lo cierra.
        if (ambientShown) {
            hideAmbient()
            poke()
            return
        }
        // Tocar fuera de relacionados los cierra.
        if (relatedShown && !inside(relatedSheet, x, y) && !inside(dock, x, y)) {
            hideRelated()
            poke()
            return
        }
        val wasHidden = !dockShown
        poke()
        if (wasHidden && inside(dock, x, y)) return
        val now = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, now, now, x, y)
        send(MotionEvent.ACTION_UP, now, now + 40, x, y)
    }

    private fun inside(v: View, x: Float, y: Float): Boolean =
        v.visibility == View.VISIBLE && x >= v.left && x <= v.right && y >= v.top && y <= v.bottom

    /*
     * Android Auto manda el desplazamiento en pedacitos. Los convertimos en un
     * arrastre real para que la página haga scroll natural; solo empezamos
     * cuando el movimiento es claro (un roce nunca se vuelve toque).
     */
    private var dragging = false
    private var dragDown = 0L
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var curX = 0f
    private var curY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val endDrag = Runnable { finishDrag() }
    private val resetPending = Runnable { pendingDx = 0f; pendingDy = 0f }

    fun scroll(distanceX: Float, distanceY: Float) {
        if (ambientShown) return
        lastInteraction = SystemClock.uptimeMillis()
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
            // Si relacionados está abierto y el gesto es horizontal, desliza la lista.
            val inSheet = relatedShown && abs(pendingDx) > abs(pendingDy)
            dragStartX = surfaceWidth / 2f
            dragStartY = if (inSheet) (relatedSheet.top + relatedSheet.height * 0.6f) else surfaceHeight / 2f
            curX = dragStartX
            curY = dragStartY
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

    /** Miniaturas de relacionados (pequeñas, ~15 KB cada una). */
    private object Thumbs {
        private val io = Executors.newFixedThreadPool(3)
        private val main = Handler(Looper.getMainLooper())
        private val cache = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        fun load(url: String, into: ImageView) {
            into.tag = url
            cache.get(url)?.let { into.setImageBitmap(it); return }
            io.execute {
                val bmp = runCatching {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 6000
                    c.readTimeout = 6000
                    c.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull() ?: return@execute
                cache.put(url, bmp)
                main.post {
                    if (into.tag == url) {
                        into.alpha = 0f
                        into.setImageBitmap(bmp)
                        into.animate().alpha(1f).setDuration(220).start()
                    }
                }
            }
        }
    }
}
