package mx.lumo.auto.ui

import android.content.Context

/** Temas de color para el vidrio. */
object Accent {
    data class Option(val name: String, val color: Int)

    val options = listOf(
        Option("Lima", 0xFFE8FF5A.toInt()),
        Option("Hielo", 0xFF8FE3FF.toInt()),
        Option("Violeta", 0xFFB9A3FF.toInt()),
        Option("Coral", 0xFFFF8A73.toInt()),
        Option("Neutro", 0xFFF2F2F5.toInt())
    )

    private val listeners = mutableSetOf<(Int) -> Unit>()

    fun index(context: Context): Int =
        prefs(context).getInt(KEY, 0).coerceIn(0, options.lastIndex)

    fun color(context: Context): Int = options[index(context)].color

    fun set(context: Context, i: Int) {
        prefs(context).edit().putInt(KEY, i).apply()
        val c = options[i].color
        listeners.toList().forEach { it(c) }
    }

    fun listen(l: (Int) -> Unit) { listeners += l }
    fun unlisten(l: (Int) -> Unit) { listeners -= l }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("lumo", Context.MODE_PRIVATE)
    private const val KEY = "accent"
}
