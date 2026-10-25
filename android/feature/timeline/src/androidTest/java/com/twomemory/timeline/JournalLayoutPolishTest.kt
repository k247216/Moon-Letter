package com.twomemory.timeline

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.TimelinePhoto
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class JournalLayoutPolishTest {
    @get:Rule val compose = createComposeRule()
    private fun render(alternate: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "polish-photo.png")
        context.resources.openRawResource(com.twomemory.designsystem.R.drawable.moonletter_cover_v7).use { source ->
            photo.outputStream().use { source.copyTo(it) }
        }
        compose.setContent { TwoMemoryTheme {
            JournalEntryCard(TimelineEntryUi("polish", "今天", "10:24", "小满", null,
                body = "晚风轻轻经过。", mine = true, photo = TimelinePhoto(photo.absolutePath, null)), {}, alternate = alternate)
        } }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("记录里的照片").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun shortTextStartsBesidePhotoInsteadOfFloatingAtItsMiddle() {
        render(false)
        val text = compose.onNodeWithText("晚风轻轻经过。", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithContentDescription("记录里的照片", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Text and photo should share a reading baseline", abs(text.top - photo.top) / density < 16f)
    }
    @Test fun alternatingPhotoRowsReallyReverseTheComposition() {
        render(true)
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa")!!
        File(directory, "journal-alternating-photo.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val text = compose.onNodeWithText("晚风轻轻经过。", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithContentDescription("记录里的照片", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Alternating photo should sit left of its text", photo.right < text.left)
    }
}
