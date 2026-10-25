package mx.lumo.auto

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Inicio de sesión de YouTube dentro de Kali. Las cookies se comparten con el
 * navegador del auto, así que si Google permite el acceso, tu cuenta Premium
 * (sin anuncios) queda activa también en la pantalla del auto.
 */
class LoginActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
                // Al volver a YouTube ya con sesión, terminamos.
                if (url.startsWith("https://m.youtube.com")) {
                    Toast.makeText(this@LoginActivity, "Sesión lista en Kali", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        web.loadUrl(
            "https://accounts.google.com/ServiceLogin?service=youtube&passive=true" +
                "&continue=https%3A%2F%2Fm.youtube.com%2F"
        )
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }
}
