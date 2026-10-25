package com.twomemory.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.EntryVisibility
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LittleNoteScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun oneSentenceCanBePostedWithoutTitleOrMediaControls() {
        var text by mutableStateOf(""); var posted = ""
        compose.setContent { TwoMemoryTheme { LittleNoteComposer(text, { text = it }, EntryVisibility.SHARED,
            {}, false, false, null, {}, { posted = text }, {}) } }
        compose.onNodeWithContentDescription("贴好便签").assertIsNotEnabled()
        compose.onNodeWithContentDescription("便签正文").performTextInput("今天先抱一下。")
        compose.onNodeWithContentDescription("贴好便签").assertIsEnabled().performClick()
        assertEquals("今天先抱一下。", posted)
        compose.onNodeWithText("添加图片").assertDoesNotExist()
        compose.onNodeWithText("我的记录").assertDoesNotExist()
    }
    @Test fun failedSaveKeepsTextAndAllowsRetry() {
        var retries = 0
        compose.setContent { TwoMemoryTheme { LittleNoteComposer("还没贴好的话", {}, EntryVisibility.SHARED,
            {}, false, false, "暂时没保存好，请再试一次", {}, { retries++ }, {}) } }
        compose.onNodeWithContentDescription("便签正文").assertTextContains("还没贴好的话")
        compose.onNodeWithText("暂时没保存好，请再试一次").assertIsDisplayed()
        compose.onNodeWithContentDescription("贴好便签").performClick()
        assertEquals(1, retries)
    }
    @Test fun pendingSaveCannotBePostedTwice() {
        compose.setContent { TwoMemoryTheme { LittleNoteComposer("正在收好的一句", {}, EntryVisibility.SHARED,
            {}, true, false, null, {}, {}, {}) } }
        compose.onNodeWithContentDescription("贴好便签").assertIsNotEnabled()
    }
    @Test fun capturesPhonePaper() = capture("little-note-phone.png")
    @Test @Config(qualifiers = "w800dp-h1100dp-mdpi")
    fun capturesTabletPaper() = capture("little-note-tablet.png")
    @Test @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun largeTextLandscapeKeepsPostingReachable() {
        compose.setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(1f, 1.5f)) { TwoMemoryTheme { LittleNoteComposer("今天的一小句话", {}, EntryVisibility.SHARED,
            {}, false, false, null, {}, {}, {}) } } }
        compose.onNodeWithContentDescription("贴好便签").assertIsDisplayed()
        compose.onNodeWithText("先收好").assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.setContent { TwoMemoryTheme { LittleNoteComposer("下班路上看到一只小狗，\n跑得摇摇晃晃。\n\n突然就很想你。", {}, EntryVisibility.SHARED,
            {}, false, false, null, {}, {}, {}) } }
        compose.onNodeWithContentDescription("贴好便签").assertIsDisplayed()
        val file = java.io.File("../../releases/v43/$name").also { it.parentFile.mkdirs() }
        val bitmap = compose.onRoot().captureToImage().let { it.asAndroidBitmap() }
        file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
