package com.twomemory.timeline

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import com.twomemory.model.TimelinePhoto
import org.junit.Rule
import org.junit.Test
import java.io.File

class JournalVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun captureCoverAndJournal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = File(context.cacheDir, "journal-photo.png")
        context.resources.openRawResource(com.twomemory.designsystem.R.drawable.moonletter_cover_v7).use { input ->
            photo.outputStream().use { input.copyTo(it) }
        }
        compose.setContent {
            TwoMemoryTheme {
                TimelineScreen(listOf(
                    TimelineEntryUi("one", "今天 · 10月2日", "10:24", "小满", null,
                        body = "早上一起买了月饼，路上风很轻。\n你走在我旁边的时候，连空气都是甜的。", mine = true,
                        photo = TimelinePhoto(photo.absolutePath, null)),
                    TimelineEntryUi("two", "今天 · 10月2日", "13:51", "阿屿", null,
                        body = "忙完工作，和你在阳台一起看了月亮。\n没有说很多话，但好像什么都被看见了。",
                        attachments = listOf(TimelineAttachmentUi(BlockType.MUSIC, "月亮代表我的心", "邓丽君 · 一起听过"))),
                ))
            }
        }
        capture("journal-cover.png")
        compose.onNodeWithText("月亮代表我的心").performScrollTo()
        capture("journal-paper.png")
        compose.onNodeWithText("我们的时光").assertDoesNotExist()
    }
    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa")!!
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
