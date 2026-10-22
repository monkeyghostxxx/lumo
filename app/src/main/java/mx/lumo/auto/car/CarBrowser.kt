package mx.lumo.auto.car

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder

/**
 * Un solo navegador que vive mientras la app está abierta.
 * Si cambias a Maps y regresas, el video sigue donde estaba.
 */
object CarBrowser {
    const val HOME = "https://m.youtube.com/"
    private const val PREFS = "lumo"
    private const val KEY_LAST = "last_url"

    interface Host {
        fun showFullscreen(view: View)
        fun hideFullscreen()
        fun setProgress(percent: Int)
    }

    private var webView: WebView? = null
    private var wrapper: MutableContextWrapper? = null
    private var host: Host? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    val isFullscreen get() = fullscreenCallback != null

    fun attach(context: Context, newHost: Host): WebView {
        val w = webView ?: create(context)
        wrapper?.baseContext = context
        (w.parent as? ViewGroup)?.removeView(w)
        host = newHost
        if (w.url == null) w.loadUrl(lastUrl(context))
        w.onResume()
        return w
    }

    fun detach() {
        exitFullscreen()
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        host = null
    }

    /** Abre un enlace en el auto (o lo deja listo para cuando se conecte). */
    fun open(context: Context, url: String) {
        saveLast(context, url)
        webView?.loadUrl(url)
    }

    fun search(context: Context, query: String) {
        val q = query.trim()
        val url = when {
            q.startsWith("http://") || q.startsWith("https://") -> q
            Patterns.WEB_URL.matcher(q).matches() && !q.contains(' ') -> "https://$q"
            else -> "https://m.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")
        }
        open(context, url)
    }

    fun home(context: Context) = open(context, HOME)

    fun back() {
        val w = webView ?: return
        when {
            isFullscreen -> exitFullscreen()
            w.canGoBack() -> w.goBack()
        }
    }

    fun exitFullscreen() {
        val cb = fullscreenCallback ?: return
        fullscreenCallback = null
        host?.hideFullscreen()
        runCatching { cb.onCustomViewHidden() }
    }

    fun pauseMedia() = js(
        "(function(){var p=false;document.querySelectorAll('video').forEach(function(v){" +
            "if(!v.paused){v.pause();p=true}});if(p)window.__lumo=true})()"
    )

    fun resumeMedia() = js(
        "(function(){if(window.__lumo){window.__lumo=false;" +
            "var v=document.querySelector('video');if(v)v.play()}})()"
    )

    private fun js(code: String) {
        webView?.evaluateJavascript(code, null)
    }

    fun lastUrl(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST, HOME) ?: HOME

    private fun saveLast(context: Context, url: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST, url).apply()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(context: Context): WebView {
        val wrap = MutableContextWrapper(context)
        val w = WebView(wrap)
        w.setBackgroundColor(Color.BLACK)
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webViewClient = object : WebViewClient() {
            // Bloquea "abrir en la app de YouTube" y otros esquemas raros:
            // todo se queda en la pantalla del auto.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("http")) saveLast(view.context, url)
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                val h = host
                if (h == null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenCallback = callback
                h.showFullscreen(view)
            }

            override fun onHideCustomView() {
                if (fullscreenCallback == null) return
                fullscreenCallback = null
                host?.hideFullscreen()
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                host?.setProgress(newProgress)
            }
        }

        wrapper = wrap
        webView = w
        return w
    }
}
