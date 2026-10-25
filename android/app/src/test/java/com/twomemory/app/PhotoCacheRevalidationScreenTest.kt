package com.twomemory.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCacheRevalidationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun missingLocalDetailDoesNotRemainVisibleFromItsOldDetailCache() =
        verifyRemovedSource(preloadDetail = true)

    @Test fun missingLocalDetailDoesNotRemainVisibleFromItsOldPreviewCache() =
        verifyRemovedSource(preloadDetail = false)

    private fun verifyRemovedSource(preloadDetail: Boolean) {
        val file = File(compose.activity.filesDir, "photos/${UUID.randomUUID()}.png")
        file.parentFile!!.mkdirs()
        file.writeBytes(pictureBytes())
        runBlocking { requireNotNull(EntryPhotos.load(compose.activity, file.absolutePath, null, preloadDetail)) }
        assertTrue(file.delete())
        showPhoto(file.absolutePath, null)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("被检查的照片").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun temporarilyUnavailableOriginalStillKeepsAValidPreviewVisible() {
        val priorPreview = EntryPhotos.remotePreview
        val priorFile = EntryPhotos.remoteFile
        val priorBytes = EntryPhotos.remoteBytes
        val attempted = AtomicInteger()
        val assetId = UUID.randomUUID().toString()
        try {
            EntryPhotos.remotePreview = { pictureBytes() }
            EntryPhotos.remoteFile = { _, _ -> attempted.incrementAndGet(); error("Synthetic temporary outage") }
            showPhoto(null, assetId)
            compose.waitUntil(10_000) { attempted.get() > 0 }
            compose.onNodeWithContentDescription("被检查的照片").assertIsDisplayed()
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            EntryPhotos.remotePreview = priorPreview
            EntryPhotos.remoteFile = priorFile
            EntryPhotos.remoteBytes = priorBytes
        }
    }

    private fun showPhoto(path: String?, asset: String?) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            EntryPhoto(path, asset, Modifier.height(220.dp), "被检查的照片",
                original = true, useNaturalAspectRatio = false)
        } } }
    }

    private fun pictureBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray()
        } } finally { bitmap.recycle() }
    }
}
