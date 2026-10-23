package mx.lumo.auto.car

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import mx.lumo.auto.R

/**
 * Lumo en la pantalla del auto como "app para auto estacionado".
 * Android Auto la muestra solo con el auto detenido y la cierra al avanzar.
 * A diferencia del modo plantillas, sí se puede instalar desde un APK
 * (con "Fuentes desconocidas" activado en Android Auto).
 */
class ParkedActivity : Activity() {

    private var ui: CarUi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.car_browser)
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        val decor = window.decorView
        ui = CarUi(this, decor, { decor.width }, { decor.height }).also { it.start() }
    }

    override fun onStop() {
        ui?.stop()
        ui = null
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && ui?.onTouchDown(ev.x, ev.y) == true) return true
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (CarBrowser.isFullscreen || CarBrowser.canGoBack()) {
            CarBrowser.back()
            return
        }
        super.onBackPressed()
    }

    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }
}
