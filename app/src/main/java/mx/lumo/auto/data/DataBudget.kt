package mx.lumo.auto.data

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

/**
 * Presupuesto de datos: mide lo que gasta Lumo y escoge la calidad de video
 * para que los GB que le asignas te alcancen hasta tu día de corte.
 */
object DataBudget {

    enum class Quality(val yt: String, val height: Int, val label: String, val gbPerHour: Double) {
        Q360("medium", 360, "360p", 0.3),
        Q480("large", 480, "480p", 0.55),
        Q720("hd720", 720, "720p", 1.0),
        Q1080("hd1080", 1080, "1080p", 1.7)
    }

    enum class Mode(val label: String) { AUTO("Inteligente"), MAX("Máxima"), SAVER("Ahorro") }

    private const val PREFS = "lumo_data"
    private const val GB = 1_000_000_000.0

    // --- Ajustes ---

    fun lumoGb(c: Context): Float = p(c).getFloat("lumo_gb", 16f)
    fun cutDay(c: Context): Int = p(c).getInt("cut_day", 1)
    fun hoursPerDay(c: Context): Float = p(c).getFloat("hours_day", 1f)
    fun mode(c: Context): Mode = runCatching { Mode.valueOf(p(c).getString("mode", "MAX")!!) }.getOrDefault(Mode.AUTO)

    fun setLumoGb(c: Context, v: Float) = p(c).edit().putFloat("lumo_gb", v.coerceIn(0.5f, 500f)).apply()
    fun setCutDay(c: Context, v: Int) = p(c).edit().putInt("cut_day", v.coerceIn(1, 31)).apply()
    fun setHoursPerDay(c: Context, v: Float) = p(c).edit().putFloat("hours_day", v).apply()
    fun setMode(c: Context, m: Mode) = p(c).edit().putString("mode", m.name).apply()

    // --- Medición ---

    /** Suma lo que la app ha gastado desde la última medición. */
    fun sample(c: Context) {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx < 0 || tx < 0) return
        val raw = rx + tx
        val prefs = p(c)
        val cycle = cycleStart(c).toEpochDay()
        var used = if (prefs.getLong("cycle", -1) == cycle) prefs.getLong("used", 0) else 0L
        val last = prefs.getLong("raw_last", -1)
        used += when {
            last < 0 -> 0
            raw >= last -> raw - last
            else -> raw // el teléfono se reinició
        }
        prefs.edit().putLong("raw_last", raw).putLong("used", used).putLong("cycle", cycle).apply()
    }

    fun usedGb(c: Context): Double {
        val prefs = p(c)
        if (prefs.getLong("cycle", -1) != cycleStart(c).toEpochDay()) return 0.0
        return prefs.getLong("used", 0) / GB
    }

    fun remainingGb(c: Context): Double = (lumoGb(c) - usedGb(c)).coerceAtLeast(0.0)

    fun daysLeft(c: Context): Long =
        ChronoUnit.DAYS.between(LocalDate.now(), cycleStart(c).plusMonths(1).withCut(cutDay(c))).coerceAtLeast(1)

    /** La mejor calidad que cabe en lo que queda, según tus horas de uso por día. */
    fun quality(c: Context): Quality = when (mode(c)) {
        Mode.MAX -> Quality.Q1080
        Mode.SAVER -> Quality.Q480
        Mode.AUTO -> {
            val perHour = remainingGb(c) / daysLeft(c) / hoursPerDay(c).coerceAtLeast(0.25f)
            Quality.values().lastOrNull { it.gbPerHour <= perHour } ?: Quality.Q360
        }
    }

    fun hoursLeft(c: Context): Double = remainingGb(c) / quality(c).gbPerHour

    private fun cycleStart(c: Context): LocalDate {
        val today = LocalDate.now()
        val day = cutDay(c)
        val thisMonth = today.withCut(day)
        return if (!today.isBefore(thisMonth)) thisMonth else today.minusMonths(1).withCut(day)
    }

    private fun LocalDate.withCut(day: Int): LocalDate = withDayOfMonth(min(day, lengthOfMonth()))

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
