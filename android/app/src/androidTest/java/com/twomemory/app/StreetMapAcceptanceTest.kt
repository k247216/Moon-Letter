package com.twomemory.app

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class StreetMapAcceptanceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun packagedStreetMapUsesGeographicPinsAndCallsNativeCitySelection() {
        compose.setContent {
            TwoMemoryTheme {
                CityMapPreviewScreen(listOf(
                    CityStoryUi("h", "eh", "杭州", "10月2日", "杭州晚风", "湖边散步", "小满"),
                    CityStoryUi("s", "es", "上海", "10月1日", "上海周末", "外滩晚风", "阿屿"),
                ))
            }
        }
        compose.onNodeWithContentDescription("地图样式").performClick()
        compose.onNodeWithText("街道地图").performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = AtomicBoolean(false)
        val earlyDiagnostics = java.util.concurrent.atomic.AtomicReference<String?>(null)
        fun webView(view: View): WebView? = when (view) {
            is WebView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { webView(view.getChildAt(it)) }
            else -> null
        }
        compose.waitUntil(20_000) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                activity?.window?.decorView?.let(::webView)?.let { view ->
                    view.evaluateJavascript("JSON.stringify({innerH:innerHeight,nativeW:${view.width},nativeH:${view.height},domH:document.getElementById('map').clientHeight,cssH:getComputedStyle(document.getElementById('map')).height,cssPosition:getComputedStyle(document.getElementById('map')).position,asset:document.querySelector('style').textContent.includes('100vh'),cachedH:typeof map !== 'undefined' && map ? map.getSize().y : -1})") {
                        earlyDiagnostics.set(it)
                        File(instrumentation.targetContext.getExternalFilesDir("visual-qa"), "map-early-diagnostics.json").writeText(it)
                    }
                    view.evaluateJavascript(
                        "typeof map !== 'undefined' && !!map && markers.length >= 300 && map.getSize().x > 100 && map.getSize().y > 100",
                    ) { ready.set(it == "true") }
                }
            }
            ready.get()
        }
        val attributionClear = AtomicBoolean(false)
        // At national zoom the two nearby story labels collide, but both real dots must remain.
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            webView(activity.window.decorView)!!.evaluateJavascript("map.setView([30.7,120.8],4);arrangePins()", null)
        }
        val nearbyStoryStillVisible = AtomicBoolean(false)
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
                webView(activity.window.decorView)!!.evaluateJavascript(
                    "markers[cities.findIndex(c => c.name === '上海')].getElement().style.display !== 'none'",
                ) { nearbyStoryStillVisible.set(it == "true") }
            }
            nearbyStoryStillVisible.get()
        }
        compose.waitUntil(5_000) {
          instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            webView(activity.window.decorView)!!.evaluateJavascript(
                "document.querySelector('.leaflet-control-attribution').getBoundingClientRect().bottom <= innerHeight - 12",
            ) { attributionClear.set(it == "true") }
            webView(activity.window.decorView)!!.evaluateJavascript("JSON.stringify({height:innerHeight,bottom:document.querySelector('.leaflet-control-attribution').getBoundingClientRect().bottom,margin:getComputedStyle(document.querySelector('.leaflet-control-attribution')).marginBottom})") {
                File(instrumentation.targetContext.getExternalFilesDir("visual-qa"), "map-attribution.json").writeText(it)
            }
          }
          attributionClear.get()
        }
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            webView(activity.window.decorView)!!.evaluateJavascript(
                "window.v22OriginalMarker=markers[0];markers[cities.findIndex(c => c.name === '上海')].fire('click')", null,
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("上海的故事").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("上海周末").assertIsDisplayed()
        compose.onNodeWithText("杭州晚风").assertDoesNotExist()
        val stableMarkers = AtomicBoolean(false)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            webView(activity.window.decorView)!!.evaluateJavascript("markers[0]===window.v22OriginalMarker") { stableMarkers.set(it == "true") }
        }
        compose.waitUntil(2_000) { stableMarkers.get() }
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            webView(activity.window.decorView)!!.evaluateJavascript("map.setView([40,80],7)", null)
        }
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNodeWithContentDescription("城市名称或省份").performTextInput("上海")
        compose.onNode(hasText("上海") and !hasSetTextAction()).performClick()
        val refocused = AtomicBoolean(false)
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
                webView(activity.window.decorView)!!.evaluateJavascript(
                    "map.getBounds().contains([31.2304,121.4737])",
                ) { refocused.set(it == "true") }
            }
            refocused.get()
        }
        val fallbackReady = AtomicBoolean(false)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            webView(activity.window.decorView)!!.evaluateJavascript(
                "!!map.getPane('fallback') && map.getPane('fallback').style.zIndex === '190'",
            ) { fallbackReady.set(it == "true") }
        }
        compose.waitUntil(2_000) { fallbackReady.get() }
        android.os.SystemClock.sleep(10_000)
        instrumentation.waitForIdleSync()
        val directory = instrumentation.targetContext.getExternalFilesDir("visual-qa")!!
        val diagnostics = java.util.concurrent.atomic.AtomicReference<String?>(null)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            val view = webView(activity.window.decorView)!!
            view.evaluateJavascript("JSON.stringify({width:innerWidth,height:innerHeight,mapWidth:map.getSize().x,mapHeight:map.getSize().y,children:document.getElementById('map').children.length,tiles:document.querySelectorAll('.leaflet-tile-loaded').length,status:document.getElementById('status').textContent})") { diagnostics.set(it) }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "map-webview-draw.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.waitUntil(5_000) { diagnostics.get() != null }
        File(directory, "map-render-diagnostics.json").writeText(diagnostics.get()!!)
        File(directory, "map-streets.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("地图样式").performClick()
        compose.onNodeWithText("地理手账").performClick()
        compose.onNodeWithContentDescription("搜索城市").assertIsDisplayed()
    }
}
