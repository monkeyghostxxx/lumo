package mx.lumo.auto.car

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
import androidx.media3.common.Player
import mx.lumo.auto.PlayerHolder
import mx.lumo.auto.R

/**
 * Pantalla principal en el auto. Usa NavigationTemplate solo para obtener
 * la superficie completa; todo lo visible lo dibuja CarVideoRenderer.
 */
class PlayerScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val renderer = CarVideoRenderer(carContext)
    private var moving = false
    private var pausedByMotion = false
    private var speedListener: OnCarDataAvailableListener<Speed>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = invalidate()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = invalidate()
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        PlayerHolder.get(carContext).addListener(playerListener)
        startSpeedWatch()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopSpeedWatch()
        val player = PlayerHolder.get(carContext)
        player.removeListener(playerListener)
        player.pause() // al desconectar, no sigue sonando en el teléfono
        renderer.release()
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(null) }
    }

    override fun onGetTemplate(): Template {
        val playing = PlayerHolder.isActuallyPlaying(carContext)
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(icon(if (playing) R.drawable.ic_pause else R.drawable.ic_play))
                    .setOnClickListener {
                        if (!moving) PlayerHolder.toggle(carContext)
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_list))
                    .setOnClickListener { screenManager.push(VideoListScreen(carContext)) }
                    .build()
            )
            .build()
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }

    private fun icon(res: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

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

    private fun onMovingChanged(nowMoving: Boolean) {
        if (nowMoving == moving) return
        moving = nowMoving
        val player = PlayerHolder.get(carContext)
        if (moving) {
            if (player.isPlaying) {
                player.pause()
                pausedByMotion = true
            }
        } else if (pausedByMotion) {
            pausedByMotion = false
            player.play()
        }
        renderer.setMoving(moving)
        invalidate()
    }

    private companion object {
        const val MOVING_MPS = 2.0f // ~7 km/h
    }
}
