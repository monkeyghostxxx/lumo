package mx.lumo.auto.car

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/**
 * Recibe la superficie de la pantalla del auto, crea una pantalla virtual
 * encima y muestra ahí nuestra propia interfaz (video + controles).
 */
class CarVideoRenderer(private val context: CarContext) : SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPresentation? = null
    private var visibleArea: Rect? = null
    private var moving = false

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        release()
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

        val p = CarPresentation(context, vd.display, width, height)
        try {
            p.show()
        } catch (e: Exception) {
            vd.release()
            virtualDisplay = null
            return
        }
        presentation = p
        visibleArea?.let { p.applyVisibleArea(it) }
        p.setMoving(moving)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        presentation?.applyVisibleArea(visibleArea)
    }

    override fun onStableAreaChanged(stableArea: Rect) = Unit

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = release()

    // Toques en la pantalla del auto (Android Auto con Car API 5+)
    override fun onClick(x: Float, y: Float) {
        presentation?.tap(x, y)
    }

    // Deslizar horizontalmente = adelantar / regresar
    override fun onScroll(distanceX: Float, distanceY: Float) {
        presentation?.scrub(distanceX)
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
