package mx.lumo.auto.mirror

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.view.Surface

/**
 * Espejo de una app del teléfono (YouTube con tu cuenta Premium) en la
 * pantalla del auto. La pantalla virtual se crea una sola vez por sesión
 * (Android 14 no permite crearla dos veces) y solo se le cambia la superficie
 * cuando Android Auto recrea la pantalla.
 */
object Mirror {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    val active get() = projection != null

    fun listen(l: (Boolean) -> Unit) { listeners += l; l(active) }
    fun unlisten(l: (Boolean) -> Unit) { listeners -= l }

    internal fun started(p: MediaProjection) {
        projection = p
        emit()
    }

    internal fun stopped() {
        display?.release()
        display = null
        projection = null
        emit()
    }

    fun stop() {
        projection?.stop() // dispara stopped() vía el callback del servicio
    }

    /** Conecta (o reconecta) la imagen del teléfono a una superficie del auto. */
    fun attach(surface: Surface, width: Int, height: Int, dpi: Int): Boolean {
        val p = projection ?: return false
        val d = display
        return try {
            if (d == null) {
                display = p.createVirtualDisplay(
                    "LumoMirror", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null
                )
            } else {
                d.resize(width, height, dpi)
                d.surface = surface
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun detach() {
        display?.surface = null
    }

    private fun emit() = listeners.toList().forEach { it(active) }
}
