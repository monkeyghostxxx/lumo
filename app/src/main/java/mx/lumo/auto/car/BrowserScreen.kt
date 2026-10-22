package mx.lumo.auto.car

import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import mx.lumo.auto.R

/**
 * Pantalla única en el auto. NavigationTemplate solo se usa para obtener
 * la pantalla completa; Android Auto solo dibuja encima la lupa (su teclado).
 * Todo lo demás está en el dock de vidrio.
 */
class BrowserScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val renderer = CarSurfaceRenderer(carContext)
    private val handler = Handler(Looper.getMainLooper())
    private var moving = false
    private var speedListener: OnCarDataAvailableListener<Speed>? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        startSpeedWatch()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacksAndMessages(null)
        stopSpeedWatch()
        CarBrowser.pauseMedia() // al desconectar, no sigue sonando en el teléfono
        renderer.release()
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(null) }
    }

    override fun onGetTemplate(): Template {
        val strip = ActionStrip.Builder()
            .addAction(action(R.drawable.ic_search) { screenManager.push(SearchScreen(carContext)) })
            .build()
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }

    private fun action(icon: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
            .setOnClickListener { onClick() }
            .build()

    // --- Seguridad: pausa automática cuando el auto se mueve ---

    private fun startSpeedWatch() {
        runCatching {
            val info = carContext.getCarService(CarHardwareManager::class.java).carInfo
            val listener = OnCarDataAvailableListener<Speed> { speed ->
                val value = speed.displaySpeedMetersPerSecond
                if (value.status == CarValue.STATUS_SUCCESS) {
                    onMovingChanged((value.value ?: 0f) > MOVING_MPS)
                }
            }
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            speedListener = listener
        }
    }

    private fun stopSpeedWatch() {
        val listener = speedListener ?: return
        runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(listener)
        }
        speedListener = null
    }

    // En movimiento: modo audio. La imagen se tapa y el sonido sigue.
    private fun onMovingChanged(nowMoving: Boolean) {
        if (nowMoving == moving) return
        moving = nowMoving
        renderer.setMoving(moving)
    }

    fun onDayNightChanged() = renderer.onDayNightChanged()

    private companion object {
        const val MOVING_MPS = 2.0f // ~7 km/h
    }
}
