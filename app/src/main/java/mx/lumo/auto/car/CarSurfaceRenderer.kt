package mx.lumo.auto.car

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/**
 * Toma TODA la superficie que Android Auto le da a la app y muestra
 * nuestra vista encima, sin márgenes ni paneles.
 */
class CarSurfaceRenderer(private val context: CarContext) : SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPresentation? = null
    private var moving = false
    private var lastContainer: SurfaceContainer? = null

    /** Contexto con el modo día/noche que indica Android Auto (claro u oscuro). */
    private fun themedContext(): Context {
        val night = runCatching { context.isDarkMode }.getOrDefault(true)
        val cfg = Configuration(context.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return context.createConfigurationContext(cfg)
    }

    /** Android Auto cambió entre día y noche: redibuja con el nuevo modo. */
    fun onDayNightChanged() {
        lastContainer?.let { onSurfaceAvailable(it) }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        release()
        lastContainer = surfaceContainer
        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        if (width <= 0 || height <= 0) return

        val dm = context.getSystemService(DisplayManager::class.java) ?: return
        val vd = dm.createVirtualDisplay(
            "LumoCar", width, height, surfaceContainer.dpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        ) ?: return
        virtualDisplay = vd

        val p = CarPresentation(themedContext(), vd.display, width, height)
        try {
            p.show()
        } catch (e: Exception) {
            vd.release()
            virtualDisplay = null
            return
        }
        presentation = p
        p.setMoving(moving)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        lastContainer = null
        release()
    }

    override fun onClick(x: Float, y: Float) {
        presentation?.tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        presentation?.scroll(distanceX, distanceY)
    }

    fun setMoving(value: Boolean) {
        moving = value
        presentation?.setMoving(value)
    }

    fun release() {
        runCatching { presentation?.dismiss() }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }
}
