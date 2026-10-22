package mx.lumo.auto.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class LumoCarAppService : CarAppService() {
    // App personal (sideload): acepta cualquier host de Android Auto.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        private var screen: BrowserScreen? = null
        private var night: Boolean? = null

        override fun onCreateScreen(intent: Intent): Screen {
            night = carContext.isDarkMode
            return BrowserScreen(carContext).also { screen = it }
        }

        // Día / noche de Android Auto
        override fun onCarConfigurationChanged(newConfiguration: Configuration) {
            val now = carContext.isDarkMode
            if (now != night) {
                night = now
                screen?.onDayNightChanged()
            }
        }
    }
}
