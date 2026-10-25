package com.twomemory.designsystem

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.*
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.twomemory.model.CityCatalog
import com.twomemory.model.CityOption
import com.twomemory.model.CityMapLevels
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opt-in online streets; only public tile coordinates leave the device, never story content. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StreetCityMap(selectedCity: String?, counts: Map<String, Int>, onCitySelected: (CityOption) -> Unit,
    modifier: Modifier = Modifier, focusRequest: Int = 0, fitStoriesRequest: Int = 0) {
    val currentCallback by rememberUpdatedState(onCitySelected)
    val catalog = remember {
        JSONArray(CityCatalog.majorCities.map {
            JSONObject().put("name", it.name).put("latitude", it.latitude).put("longitude", it.longitude).put("level", CityMapLevels.level(it))
        })
    }
    val data = remember(selectedCity, counts, focusRequest, fitStoriesRequest) {
        JSONObject().put("selected", CityCatalog.find(selectedCity)?.name.orEmpty())
            .put("fitStoriesRequest", fitStoriesRequest)
            .put("focusRequest", focusRequest).put("counts", JSONObject(counts)).put("cities", catalog).toString()
    }
    val latestData by rememberUpdatedState(data)
    var ready by remember { mutableStateOf(false) }
    val sentData = remember { AtomicReference<String?>(null) }
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            val active = AtomicBoolean(true)
            tag = active
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.setGeolocationEnabled(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString + " MoonLetter/0.1 (+https://github.com/k247216/Moon-Letter)"
            addJavascriptInterface(object {
                @JavascriptInterface fun selectCity(name: String) {
                    val city = CityCatalog.find(name) ?: return
                    Handler(Looper.getMainLooper()).post { if (active.get()) currentCallback(city) }
                }
            }, "MoonCity")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.host != "moonletter.local") return null
                    val name = uri.lastPathSegment ?: return null
                    val type = when (name) {
                        "street-map.html" -> "text/html"
                        "leaflet.js" -> "application/javascript"
                        "leaflet.css" -> "text/css"
                        "china-provinces.geojson", "china-cities.geojson" -> "application/json"
                        else -> return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
                    }
                    return WebResourceResponse(type, "UTF-8", context.assets.open("map/$name"))
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.hasGesture() && request.url.toString() == "https://www.openstreetmap.org/copyright") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    }
                    return true
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "https://moonletter.local/map/street-map.html") {
                        view.evaluateJavascript("initMoonMap($latestData)", null)
                        sentData.set(latestData)
                        ready = true
                    }
                }
            }
            loadUrl("https://moonletter.local/map/street-map.html")
        }
    }, update = { view -> if (ready && sentData.getAndSet(data) != data) view.evaluateJavascript("resizeMoonMap();updateMoonMap($data)", null) },
        onRelease = { view ->
            (view.tag as? AtomicBoolean)?.set(false)
            view.removeJavascriptInterface("MoonCity")
            view.stopLoading(); view.destroy()
        })
}
