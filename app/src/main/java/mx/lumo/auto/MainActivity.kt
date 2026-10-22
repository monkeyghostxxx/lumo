package mx.lumo.auto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import mx.lumo.auto.car.CarBrowser
import mx.lumo.auto.data.DataBudget
import mx.lumo.auto.mirror.Mirror
import mx.lumo.auto.mirror.MirrorActivity
import mx.lumo.auto.mirror.MirrorService
import mx.lumo.auto.music.NowPlaying
import mx.lumo.auto.ui.Accent
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var current: TextView
    private lateinit var status: TextView
    private lateinit var speedCard: View
    private lateinit var helpCard: View
    private lateinit var musicCard: View

    // Control remoto
    private lateinit var rTitle: TextView
    private lateinit var rTime: TextView
    private lateinit var rProgress: ProgressBar
    private lateinit var rPlay: ImageButton

    // Datos
    private lateinit var dUsed: TextView
    private lateinit var dBar: ProgressBar
    private lateinit var dInfo: TextView
    private lateinit var dModes: LinearLayout
    private lateinit var dHours: LinearLayout
    private lateinit var dGb: EditText
    private lateinit var dCut: EditText

    private lateinit var serverUrl: EditText

    private val density get() = resources.displayMetrics.density
    private val stateListener: (CarBrowser.State?) -> Unit = { showState(it) }
    private val mirrorListener: (Boolean) -> Unit = { on ->
        findViewById<TextView>(R.id.btn_mirror).text = if (on) "Detener espejo" else "Iniciar espejo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        current = findViewById(R.id.current)
        status = findViewById(R.id.status)
        speedCard = findViewById(R.id.speed_card)
        helpCard = findViewById(R.id.help_card)
        musicCard = findViewById(R.id.music_card)
        rTitle = findViewById(R.id.r_title)
        rTime = findViewById(R.id.r_time)
        rProgress = findViewById(R.id.r_progress)
        rPlay = findViewById(R.id.r_play)
        dUsed = findViewById(R.id.d_used)
        dBar = findViewById(R.id.d_bar)
        dInfo = findViewById(R.id.d_info)
        dModes = findViewById(R.id.d_modes)
        dHours = findViewById(R.id.d_hours)
        dGb = findViewById(R.id.d_gb)
        dCut = findViewById(R.id.d_cut)
        serverUrl = findViewById(R.id.server_url)

        // Control remoto
        tap(R.id.r_play) { CarBrowser.playPause() }
        tap(R.id.r_back10) { CarBrowser.seekBy(-10) }
        tap(R.id.r_fwd10) { CarBrowser.seekBy(10) }
        tap(R.id.r_next) { CarBrowser.next() }
        tap(R.id.r_related) { CarBrowser.showRelatedOnCar() }
        tap(R.id.r_lyrics) { CarBrowser.toggleLyricsOnCar() }
        tap(R.id.r_clock) { CarBrowser.toggleAmbientOnCar() }
        tap(R.id.r_vol_down) { volume(AudioManager.ADJUST_LOWER) }
        tap(R.id.r_vol_up) { volume(AudioManager.ADJUST_RAISE) }

        // Espejo
        findViewById<View>(R.id.btn_mirror).setOnClickListener {
            if (Mirror.active) startService(MirrorService.stopIntent(this))
            else startActivity(Intent(this, MirrorActivity::class.java))
        }

        // Enviar
        findViewById<View>(R.id.btn_send).setOnClickListener { send() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { send(); true } else false
        }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            CarBrowser.home(this)
            confirm()
        }

        // Datos
        dGb.setText(trim(DataBudget.lumoGb(this)))
        dCut.setText(DataBudget.cutDay(this).toString())
        findViewById<View>(R.id.d_save).setOnClickListener {
            dGb.text.toString().toFloatOrNull()?.let { DataBudget.setLumoGb(this, it) }
            dCut.text.toString().toIntOrNull()?.let { DataBudget.setCutDay(this, it) }
            hideKeyboard(dGb)
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
            refreshData()
        }

        // Servidor
        serverUrl.setText(prefs().getString("server_url", ""))
        findViewById<View>(R.id.btn_server).setOnClickListener {
            var url = serverUrl.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
            if (!url.startsWith("http")) url = "http://$url"
            prefs().edit().putString("server_url", url).apply()
            CarBrowser.open(this, url)
            hideKeyboard(serverUrl)
            confirm()
        }

        // Permisos
        findViewById<View>(R.id.btn_speed).setOnClickListener {
            requestPermissions(arrayOf(SPEED_PERMISSION), REQ_SPEED)
        }
        findViewById<View>(R.id.btn_music).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            Toast.makeText(this, "Busca Lumo en la lista y actívalo", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.btn_help).setOnClickListener {
            helpCard.visibility = if (helpCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refresh()
        }

    }

    override fun onResume() {
        super.onResume()
        CarBrowser.listenState(stateListener)
        Mirror.listen(mirrorListener)
        refresh()
    }

    override fun onPause() {
        CarBrowser.unlistenState(stateListener)
        Mirror.unlisten(mirrorListener)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // --- Control remoto ---

    private fun tap(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { v ->
            v.animate().scaleX(0.86f).scaleY(0.86f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }.start()
            if (!CarBrowser.isOnCarScreen) {
                Toast.makeText(this, "Abre Lumo en la pantalla del auto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            action()
        }
    }

    private fun volume(direction: Int) {
        getSystemService(AudioManager::class.java)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun showState(s: CarBrowser.State?) {
        if (!CarBrowser.isOnCarScreen) {
            status.text = "Auto desconectado"
            rTitle.text = "Abre Lumo en la pantalla del auto"
            rTime.text = ""
            rProgress.progress = 0
            rPlay.setImageResource(R.drawable.ic_play)
            return
        }
        status.text = "Conectado al auto"
        rTitle.text = s?.title?.takeIf { it.isNotBlank() } ?: "Sin video"
        val d = s?.duration ?: 0.0
        val c = s?.position ?: 0.0
        rProgress.progress = if (d > 0) ((c / d) * 1000).toInt() else 0
        rTime.text = if (d > 0) "${fmt(c)} / ${fmt(d)}" else ""
        rPlay.setImageResource(if (s?.playing == true) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun fmt(sec: Double): String {
        val t = sec.toLong()
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    // --- Enviar ---

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val url = ShareActivity.extractUrl(text)
        if (url != null && !text.contains(' ')) CarBrowser.open(this, url) else CarBrowser.search(this, text)
        input.setText("")
        hideKeyboard(input)
        confirm()
    }

    private fun confirm() {
        Toast.makeText(this, "Enviado a la pantalla del auto", Toast.LENGTH_SHORT).show()
        refresh()
    }

    // --- Datos ---

    private fun refreshData() {
        DataBudget.sample(this)
        val used = DataBudget.usedGb(this)
        val budget = DataBudget.lumoGb(this).toDouble()
        val q = DataBudget.quality(this)
        dUsed.text = String.format(Locale.US, "%.1f de %s GB", used, trim(budget.toFloat()))
        dBar.progress = ((used / budget).coerceIn(0.0, 1.0) * 1000).toInt()
        dInfo.text = String.format(
            Locale.US,
            "Calidad actual: %s · te alcanza para ~%.0f h · %d días para el corte",
            q.label, DataBudget.hoursLeft(this), DataBudget.daysLeft(this)
        )

        val mode = DataBudget.mode(this)
        chips(dModes, DataBudget.Mode.values().map { it.label }, DataBudget.Mode.values().indexOf(mode)) { i ->
            DataBudget.setMode(this, DataBudget.Mode.values()[i])
            refreshData()
        }
        val hourOptions = listOf(0.5f, 1f, 2f, 3f)
        val h = DataBudget.hoursPerDay(this)
        chips(dHours, hourOptions.map { if (it < 1f) "30 min" else "${it.toInt()} h" }, hourOptions.indexOf(h)) { i ->
            DataBudget.setHoursPerDay(this, hourOptions[i])
            refreshData()
        }
    }

    private fun chips(row: LinearLayout, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        row.removeAllViews()
        val accent = Accent.color(this)
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val chip = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(getColor(if (on) R.color.on_accent else R.color.text))
                setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
                if (on) backgroundTintList = ColorStateList.valueOf(accent)
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setOnClickListener { onPick(i) }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply {
                if (i > 0) marginStart = (8 * density).toInt()
            })
        }
    }

    // --- General ---

    private fun refresh() {
        current.text = "En el auto: " + CarBrowser.lastUrl(this)
        val granted = checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        // El permiso de velocidad es opcional: solo aparece dentro de la ayuda (?).
        speedCard.visibility = if (!granted && helpCard.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        musicCard.visibility = if (NowPlaying.hasAccess(this)) View.GONE else View.VISIBLE
        applyAccent(Accent.color(this))
        showState(CarBrowser.state)
        refreshData()
    }

    private fun applyAccent(color: Int) {
        val tint = ColorStateList.valueOf(color)
        rProgress.progressTintList = tint
        dBar.progressTintList = tint
        rPlay.backgroundTintList = tint
        listOf(R.id.btn_send, R.id.btn_server, R.id.btn_music, R.id.btn_speed, R.id.btn_mirror).forEach {
            findViewById<View>(it).backgroundTintList = tint
        }
    }

    private fun hideKeyboard(v: View) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun prefs() = getSharedPreferences("lumo", Context.MODE_PRIVATE)

    private fun trim(f: Float): String = if (f % 1f == 0f) f.toInt().toString() else f.toString()

    private companion object {
        const val SPEED_PERMISSION = "com.google.android.gms.permission.CAR_SPEED"
        const val REQ_SPEED = 1
    }
}
