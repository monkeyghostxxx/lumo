package mx.lumo.auto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import mx.lumo.auto.car.CarBrowser

/** "Compartir → Lumo" desde YouTube (o cualquier app) manda el enlace al auto. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = extractUrl(text)
        if (url != null) {
            CarBrowser.open(this, url)
            Toast.makeText(this, "Enviado a la pantalla del auto", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No encontré un enlace para abrir", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        fun extractUrl(text: String): String? {
            val m = Patterns.WEB_URL.matcher(text)
            if (!m.find()) return null
            val found = m.group()
            return if (found.startsWith("http")) found else "https://$found"
        }
    }
}
