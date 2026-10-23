package mx.lumo.auto.car

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import mx.lumo.auto.R

/** Ventana del modo plantillas (Android Auto con app de navegación). */
class CarPresentation(
    context: Context,
    display: Display,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int
) : Presentation(context, display, R.style.Theme_Lumo_Car) {

    private var ui: CarUi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ventana creada desde un servicio: hay que activarle la GPU o el video no se dibuja.
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        setContentView(R.layout.car_browser)
        val decor = window!!.decorView
        ui = CarUi(context, decor, { surfaceWidth }, { surfaceHeight }).also { it.start() }
    }

    override fun onStop() {
        ui?.stop()
        ui = null
        super.onStop()
    }

    fun tap(x: Float, y: Float) = ui?.tap(x, y)
    fun scroll(dx: Float, dy: Float) = ui?.scroll(dx, dy)
    fun setMoving(moving: Boolean) = ui?.setMoving(moving)
}
