package mx.lumo.auto.car

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import mx.lumo.auto.data.DataBudget
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/**
 * Un solo navegador que vive mientras la app está abierta. Lo usan la pantalla
 * del auto y el control remoto del teléfono (mismo proceso, mismo hilo).
 */
object CarBrowser {
    const val HOME = "https://m.youtube.com/"
    private const val PREFS = "lumo"
    private const val KEY_LAST = "last_url"

    interface Host {
        fun showFullscreen(view: View)
        fun hideFullscreen()
        fun setProgress(percent: Int)
        fun showRelated()
        fun toggleAmbient()
        fun toggleLyrics()
    }

    data class State(
        val title: String,
        val position: Double,
        val duration: Double,
        val paused: Boolean,
        val ended: Boolean,
        val watching: Boolean
    ) {
        val playing get() = !paused && !ended
    }

    data class Related(val id: String, val title: String) {
        val url get() = "https://m.youtube.com/watch?v=$id"
        val thumb get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    }

    private var webView: WebView? = null
    private var wrapper: MutableContextWrapper? = null
    private var host: Host? = null
    private var appContext: Context? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lyricsOn = false

    val isFullscreen get() = fullscreenCallback != null
    val isOnCarScreen get() = host != null

    // --- Estado en vivo (para el control remoto, el reloj y relacionados) ---

    var state: State? = null
        private set
    private val stateListeners = mutableSetOf<(State?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var ticks = 0
    private val poller = object : Runnable {
        override fun run() {
            poll()
            main.postDelayed(this, 1000)
        }
    }

    fun listenState(l: (State?) -> Unit) { stateListeners += l; l(state) }
    fun unlistenState(l: (State?) -> Unit) { stateListeners -= l }

    private fun emitState() = stateListeners.toList().forEach { it(state) }

    // --- Ciclo de vida ---

    fun attach(context: Context, newHost: Host): WebView {
        appContext = context.applicationContext
        val w = webView ?: create(context)
        wrapper?.baseContext = context
        (w.parent as? ViewGroup)?.removeView(w)
        host = newHost
        if (w.url == null) w.loadUrl(lastUrl(context))
        w.onResume()
        main.removeCallbacks(poller)
        main.post(poller)
        return w
    }

    fun detach() {
        main.removeCallbacks(poller)
        exitFullscreen()
        webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        host = null
        appContext?.let { DataBudget.sample(it) }
        state = null
        emitState()
    }

    // --- Navegación ---

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

    // --- Controles del video ---

    fun playPause() = js("(function(){var v=document.querySelector('video');if(v){if(v.paused||v.ended)v.play();else v.pause()}})()")

    fun seekBy(seconds: Int) =
        js("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($seconds))})()")

    fun next() {
        val w = webView ?: return
        w.evaluateJavascript(
            "(function(){var b=document.querySelector('.ytp-next-button,button[aria-label*=\"iguiente\"],button[aria-label*=\"Next\"]');" +
                "if(b){b.click();return 'ok'}return 'no'})()"
        ) { raw ->
            if (raw?.trim('"') != "ok") related { list -> list.firstOrNull()?.let { r -> appContext?.let { open(it, r.url) } } }
        }
    }

    fun pauseMedia() = js(
        "(function(){document.querySelectorAll('video').forEach(function(v){v.pause()})})()"
    )

    // Atajos para el control remoto del teléfono
    fun showRelatedOnCar() { host?.showRelated() }
    fun toggleAmbientOnCar() { host?.toggleAmbient() }
    fun toggleLyricsOnCar() { host?.toggleLyrics() }

    /** Videos relacionados / sugeridos que YouTube muestra en la página actual. */
    fun related(result: (List<Related>) -> Unit) {
        val w = webView ?: return result(emptyList())
        w.evaluateJavascript(RELATED_JS) { raw ->
            val list = runCatching {
                val arr = JSONArray(JSONTokener(raw).nextValue() as String)
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Related(o.getString("id"), o.getString("t"))
                }
            }.getOrDefault(emptyList())
            result(list)
        }
    }

    /** Activa / desactiva subtítulos (la letra, cuando el video la trae). */
    fun toggleLyrics(result: (String) -> Unit) {
        val w = webView ?: return result("novideo")
        w.evaluateJavascript(
            "(function(){var p=document.getElementById('movie_player');" +
                "if(!p||!document.querySelector('video'))return 'novideo';" +
                "try{if(p.toggleSubtitles){p.toggleSubtitles();return 'ok'}}catch(e){}" +
                "var b=document.querySelector('.ytp-subtitles-button,button[aria-label*=\"ubt\"],button[aria-label*=\"aption\"]');" +
                "if(b){b.click();return 'ok'}return 'none'})()"
        ) { raw ->
            when (raw?.trim('"')) {
                "ok" -> {
                    lyricsOn = !lyricsOn
                    result(if (lyricsOn) "on" else "off")
                }
                "novideo" -> result("novideo")
                else -> result("none")
            }
        }
    }

    // --- Calidad según el presupuesto de datos ---

    private fun applyQuality() {
        val ctx = appContext ?: return
        val q = DataBudget.quality(ctx)
        js(
            "(function(q,h){try{var now=Date.now();localStorage.setItem('yt-player-quality'," +
                "JSON.stringify({data:JSON.stringify({quality:h,previousQuality:h}),expiration:now+31536000000,creation:now}))}catch(e){}" +
                "var p=document.getElementById('movie_player');if(!p)return;" +
                "try{if(p.setPlaybackQualityRange)p.setPlaybackQualityRange(q,q);if(p.setPlaybackQuality)p.setPlaybackQuality(q)}catch(e){}})" +
                "('${q.yt}',${q.height})"
        )
    }

    private fun poll() {
        val w = webView ?: return
        ticks++
        w.evaluateJavascript(STATE_JS) { raw ->
            state = parseState(raw)
            emitState()
            if (state?.watching == true && ticks % 8 == 0) applyQuality()
        }
        if (ticks % 30 == 0) appContext?.let { DataBudget.sample(it) }
    }

    private fun parseState(raw: String?): State? = runCatching {
        val o = JSONObject(JSONTokener(raw).nextValue() as String)
        State(
            o.optString("t"),
            o.optDouble("c", 0.0),
            o.optDouble("d", 0.0),
            o.optBoolean("p", true),
            o.optBoolean("e", false),
            o.optBoolean("w", false)
        )
    }.getOrNull()

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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("http")) saveLast(view.context, url)
                view.evaluateJavascript(PAGE_CSS, null)
                scheduleQuality()
            }

            // YouTube cambia de video sin recargar la página.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                lyricsOn = false
                if (url.startsWith("http")) saveLast(view.context, url)
                view.evaluateJavascript(PAGE_CSS, null)
                scheduleQuality()
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

    private fun scheduleQuality() {
        main.postDelayed({ applyQuality() }, 1500)
        main.postDelayed({ applyQuality() }, 5000)
    }

    private const val STATE_JS =
        "(function(){var v=document.querySelector('video');var path=location.pathname;" +
            "return JSON.stringify({t:document.title.replace(/ - YouTube$/,''),c:v?v.currentTime:0," +
            "d:(v&&isFinite(v.duration))?v.duration:0,p:v?v.paused:true,e:v?v.ended:false," +
            "w:path.indexOf('/watch')===0||path.indexOf('/shorts')===0})})()"

    private const val RELATED_JS =
        "(function(){var cur=(location.search.match(/[?&]v=([^&]+)/)||[])[1];var seen={},out=[];" +
            "document.querySelectorAll('a[href*=\"/watch?v=\"]').forEach(function(a){" +
            "var m=a.href.match(/[?&]v=([\\w-]{11})/);if(!m)return;var id=m[1];if(id===cur||seen[id])return;" +
            "var t='';var h=a.querySelector('h3,h4,.media-item-headline,.compact-media-item-headline');" +
            "if(h)t=h.textContent;if(!t)t=a.getAttribute('title')||'';if(!t)t=a.textContent||'';" +
            "t=t.replace(/\\s+/g,' ').trim();if(t.length<3)return;seen[id]=1;out.push({id:id,t:t.slice(0,110)})});" +
            "return JSON.stringify(out.slice(0,24))})()"

    // Subtítulos grandes y legibles en la pantalla del auto.
    private const val PAGE_CSS =
        "(function(){if(document.getElementById('lumo-css'))return;" +
            "var s=document.createElement('style');s.id='lumo-css';" +
            "s.textContent='.ytp-caption-segment,.captions-text span{font-size:clamp(22px,3.4vw,44px)!important;" +
            "line-height:1.35!important;background:rgba(0,0,0,.55)!important;color:#fff!important;" +
            "border-radius:10px!important;padding:2px 10px!important;font-weight:600!important}';" +
            "document.documentElement.appendChild(s)})()"
}
