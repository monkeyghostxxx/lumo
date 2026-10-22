package mx.lumo.auto.ui

import android.content.Context
import mx.lumo.auto.R

/**
 * Color principal monocromo: negro en modo claro, blanco en modo oscuro.
 * Sale de los recursos (values / values-night), así sigue al sistema.
 */
object Accent {
    fun color(context: Context): Int = context.getColor(R.color.accent)
}
