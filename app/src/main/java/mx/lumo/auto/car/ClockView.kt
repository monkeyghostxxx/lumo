package mx.lumo.auto.car

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reloj de modo ambiente: cada dígito "rueda" como un contador
 * cuando cambia. Solo hora y fecha, nada más.
 */
class ClockView(context: Context) : LinearLayout(context) {

    private val digits = List(4) { DigitSlot(context) }
    private val colon = TextView(context)
    private val date = TextView(context)
    private val row = LinearLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var colonOn = true

    private val tick = object : Runnable {
        override fun run() {
            update(animate = true)
            colonOn = !colonOn
            colon.animate().alpha(if (colonOn) 1f else 0.25f).setDuration(400).start()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER
        digits[0].let { row.addView(it) }
        digits[1].let { row.addView(it) }
        colon.text = ":"
        colon.gravity = Gravity.CENTER
        colon.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        row.addView(colon)
        digits[2].let { row.addView(it) }
        digits[3].let { row.addView(it) }
        addView(row)

        date.gravity = Gravity.CENTER
        date.setTextColor(0x99FFFFFF.toInt())
        date.letterSpacing = 0.08f
        date.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        addView(date, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = 8
        })
    }

    fun setSizePx(digitPx: Float) {
        digits.forEach { it.setSize(digitPx) }
        colon.setTextSize(TypedValue.COMPLEX_UNIT_PX, digitPx * 0.9f)
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, (digitPx * 0.13f).coerceAtLeast(18f))
    }

    fun setAccent(color: Int) {
        colon.setTextColor(color)
    }

    fun start() {
        if (running) return
        running = true
        update(animate = false)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun update(animate: Boolean) {
        val now = LocalDateTime.now()
        val pattern = if (DateFormat.is24HourFormat(context)) "HHmm" else "hhmm"
        val text = now.format(DateTimeFormatter.ofPattern(pattern))
        digits.forEachIndexed { i, slot -> slot.set(text[i], animate) }
        val d = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX")))
        date.text = d.replaceFirstChar { it.titlecase(Locale("es", "MX")) }
    }

    /** Un dígito que entra desde abajo y sale hacia arriba. */
    private class DigitSlot(context: Context) : FrameLayout(context) {
        private var current: TextView? = null
        private var value: Char? = null
        private var sizePx = 160f
        private val ease = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)

        init {
            clipChildren = true
            clipToPadding = true
        }

        fun setSize(px: Float) {
            sizePx = px
            current?.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            layoutParams = (layoutParams ?: LayoutParams(0, 0)).apply {
                width = (px * 0.62f).toInt()
                height = (px * 1.18f).toInt()
            }
        }

        fun set(ch: Char, animate: Boolean) {
            if (value == ch) return
            value = ch
            val old = current
            val next = make(ch)
            addView(next)
            current = next
            val h = (sizePx * 1.18f)
            if (!animate || old == null) {
                old?.let { removeView(it) }
                return
            }
            next.translationY = h * 0.9f
            next.alpha = 0f
            next.animate().translationY(0f).alpha(1f).setDuration(650).setInterpolator(ease).start()
            old.animate().translationY(-h * 0.9f).alpha(0f).setDuration(650).setInterpolator(ease)
                .withEndAction { removeView(old) }.start()
        }

        private fun make(ch: Char): TextView = TextView(context).apply {
            text = ch.toString()
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
            fontFeatureSettings = "tnum"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    }
}
