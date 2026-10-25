package com.twomemory.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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
class PhotoSourceNamespaceScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun installedBackendChangeReplacesMountedPhotoPixelsForTheSameAssetId() {
        val assetId = UUID.randomUUID().toString()
        val nonce = UUID.randomUUID().toString()
        val red = pictureBytes(Color.RED)
        val blue = pictureBytes(Color.BLUE)
        val blueRequests = AtomicInteger()
        val priorBytes = EntryPhotos.remoteBytes
        val priorFile = EntryPhotos.remoteFile
        val priorPreview = EntryPhotos.remotePreview
        val priorReading = EntryPhotos.remoteReadingPreview
        try {
            compose.runOnIdle {
                EntryPhotos.installSource("synthetic-backend-A/$nonce", preview = { requested ->
                    assertEquals(assetId, requested)
                    red
                })
            }
            // Mount once: no account key, local file, changing UUID, or setContent reset hides the bug.
            compose.activity.runOnUiThread {
                compose.activity.setContent { TwoMemoryTheme {
                    EntryPhoto(null, assetId, Modifier.height(180.dp), PHOTO,
                        useNaturalAspectRatio = false)
                } }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(PHOTO).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(PHOTO).assertIsDisplayed()
            assertTrue("Backend A must first paint red pixels", isRed(centerPixel()))

            compose.runOnIdle {
                EntryPhotos.installSource("synthetic-backend-B/$nonce", preview = { requested ->
                    assertEquals(assetId, requested)
                    blueRequests.incrementAndGet()
                    blue
                })
            }
            // Observe the mounted UI, not EntryPhotos.load or a separately warmed B cache.
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(PHOTO).fetchSemanticsNodes().isNotEmpty() &&
                    isBlue(centerPixel())
            }
            assertTrue("Backend B must supply the same UUID after installSource", blueRequests.get() > 0)
            assertTrue("Mounted photo must replace A's pixels with B's pixels", isBlue(centerPixel()))
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            // Restore providers under a clean synthetic namespace; no stale A/B pixels can leak to later tests.
            compose.runOnIdle {
                EntryPhotos.installSource("namespace-screen-test-cleanup/$nonce", bytes = priorBytes,
                    file = priorFile, preview = priorPreview, reading = priorReading)
            }
        }
    }

    private fun centerPixel(): Int {
        val screenshot = compose.onNodeWithContentDescription(PHOTO).captureToImage().asAndroidBitmap()
        return screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 220 && Color.blue(pixel) < 35
    private fun isBlue(pixel: Int) = Color.blue(pixel) > 220 && Color.red(pixel) < 35

    private fun pictureBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private companion object { const val PHOTO = "namespace-controlled-photo" }
}
