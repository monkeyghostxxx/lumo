package mx.lumo.auto

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import mx.lumo.auto.car.CarBrowser
import mx.lumo.auto.music.NowPlaying

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var current: TextView
    private lateinit var speedCard: View
    private lateinit var helpCard: View
    private lateinit var musicCard: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        input = findViewById(R.id.input)
        current = findViewById(R.id.current)
        speedCard = findViewById(R.id.speed_card)
        helpCard = findViewById(R.id.help_card)
        musicCard = findViewById(R.id.music_card)

        findViewById<View>(R.id.btn_send).setOnClickListener { send() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { send(); true } else false
        }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            CarBrowser.home(this)
            confirm()
        }
        findViewById<View>(R.id.btn_speed).setOnClickListener {
            requestPermissions(arrayOf(SPEED_PERMISSION), REQ_SPEED)
        }
        findViewById<View>(R.id.btn_music).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            Toast.makeText(this, "Busca Lumo en la lista y actívalo", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.btn_help).setOnClickListener {
            helpCard.visibility = if (helpCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val url = ShareActivity.extractUrl(text)
        if (url != null && !text.contains(' ')) CarBrowser.open(this, url) else CarBrowser.search(this, text)
        input.setText("")
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
        confirm()
    }

    private fun confirm() {
        Toast.makeText(this, "Enviado a la pantalla del auto", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun refresh() {
        current.text = "En el auto: " + CarBrowser.lastUrl(this)
        val granted = checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        speedCard.visibility = if (granted) View.GONE else View.VISIBLE
        musicCard.visibility = if (NowPlaying.hasAccess(this)) View.GONE else View.VISIBLE
    }

    private companion object {
        const val SPEED_PERMISSION = "com.google.android.gms.permission.CAR_SPEED"
        const val REQ_SPEED = 1
    }
}
