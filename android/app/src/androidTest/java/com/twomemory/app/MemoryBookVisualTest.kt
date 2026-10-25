package com.twomemory.app

import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.database.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import java.util.concurrent.atomic.AtomicBoolean

class MemoryBookVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actualOfflineKeepsakeLoadsBundledFontAndNaturalPhoto(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val own = UUID.randomUUID(); val other = UUID.randomUUID(); val entry = UUID.randomUUID().toString()
        val bitmap = Bitmap.createBitmap(640, 420, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) bitmap.setPixel(x,y, Color.rgb(170 + x * 60/640, 185 + y * 40/420, 165))
        val photo = File.createTempFile("memory-book-qa-", ".png", File(context.filesDir, "photos").apply { mkdirs() })
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        try {
            database.entryDao().insertEntry(EntryEntity(entry, UUID.randomUUID().toString(), own.toString(), "PERSONAL", "PUBLISHED", 1791090000000L, "Asia/Shanghai", "把晚风写给你"))
            database.entryDao().insertBlocks(listOf(
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "TEXT", 0, own.toString(), "{\"text\":\"今天走过一条安静的小路。\\n没有特别的事，却很想把这一刻留给你。\"}", null),
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "IMAGE", 1, own.toString(), "{\"localPath\":\"${photo.absolutePath}\",\"mime\":\"image/png\"}", null),
            ))
            database.commentDao().insert(CommentEntity(UUID.randomUUID().toString(), entry, other.toString(), "下次我们一起走。", null, 1791090000000L))
            val book = MemoryBookExporter.build(context, database, own, "小满", "阿屿")
            val directory = File(context.getExternalFilesDir("visual-qa"), "book-v33").apply { mkdirs() }
            ZipFile(book.file).use { zip -> zip.entries().asSequence().forEach { item ->
                val file = File(directory, item.name)
                require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                file.parentFile?.mkdirs()
                zip.getInputStream(item).use { input -> file.outputStream().use { input.copyTo(it) } }
            } }
            val loaded = AtomicBoolean(false)
            var web: WebView? = null
            compose.setContent { AndroidView(modifier = Modifier.fillMaxSize(), factory = { host ->
                WebView(host).also { view ->
                    web = view
                    view.settings.javaScriptEnabled = true
                    view.settings.allowFileAccess = true
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) { loaded.set(true) }
                    }
                    view.loadUrl(File(directory, "index.html").toURI().toString())
                }
            }) }
            compose.waitUntil(15_000) { loaded.get() }
            val fontReady = AtomicBoolean(false)
            compose.waitUntil(15_000) {
                instrumentation.runOnMainSync { web!!.evaluateJavascript("document.fonts.status==='loaded'") { fontReady.set(it == "true") } }
                fontReady.get()
            }
            File(context.getExternalFilesDir("visual-qa"), "book-v33-cover.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            // Off-screen photographs are lazy-loaded: visit the photo before
            // testing its decoded dimensions, without changing product loading.
            instrumentation.runOnMainSync { web!!.evaluateJavascript("document.images[0].scrollIntoView({block:'center'})", null) }
            val rendered = AtomicBoolean(false)
            compose.waitUntil(15_000) {
                instrumentation.runOnMainSync { web!!.evaluateJavascript("document.fonts.status==='loaded' && document.images.length===1 && document.images[0].complete && document.images[0].naturalWidth===640") { rendered.set(it == "true") } }
                rendered.get()
            }
            instrumentation.waitForIdleSync()
            File(context.getExternalFilesDir("visual-qa"), "book-v33-page.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { database.close(); photo.delete() }
    }
}
