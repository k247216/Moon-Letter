package com.twomemory.app

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
    @Test fun paperChoicesUpdateLiveWithoutReplacingWriting() {
        var paper by mutableStateOf(com.twomemory.model.NotePaperAppearance())
        compose.setContent { TwoMemoryTheme { LittleNoteComposer("今天的小心情", {}, EntryVisibility.SHARED,
            {}, false, false, null, {}, {}, {}, paper = paper, onPaperChange = { paper = it }) } }
        compose.onNodeWithText("换张纸 · 贴贴 / 奶油").performScrollTo().performClick()
        compose.onNodeWithContentDescription("纸样：信笺").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("纸色：浅紫").performScrollTo().performClick().assertIsSelected()
        assertEquals(com.twomemory.model.NotePaperStyle.LETTER, paper.style)
        assertEquals(com.twomemory.model.NotePaperColor.LILAC, paper.color)
        compose.onNodeWithContentDescription("便签正文").assertTextContains("今天的小心情")
        compose.onNodeWithContentDescription("贴好便签").assertIsDisplayed()
    }
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
    @Test fun capturesStationeryChoices() {
        var paper by mutableStateOf(com.twomemory.model.NotePaperAppearance())
        compose.setContent { TwoMemoryTheme { LittleNoteComposer("下班路上看到一只小狗。\n突然就很想你。", {}, EntryVisibility.SHARED,
            {}, false, false, null, {}, {}, {}, paper = paper, onPaperChange = { paper = it }) } }
        compose.onNodeWithText("换张纸 · 贴贴 / 奶油").performScrollTo().performClick()
        compose.onNodeWithContentDescription("纸样：信笺").performScrollTo().performClick()
        compose.onNodeWithContentDescription("纸色：浅紫").performScrollTo().performClick()
        saveCapture("little-note-choices.png")
    }
    @Test fun capturesTimelinePaperFamily() {
        compose.setContent { TwoMemoryTheme {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                com.twomemory.model.NotePaperStyle.entries.forEachIndexed { index, style ->
                    com.twomemory.timeline.JournalEntryCard(com.twomemory.timeline.TimelineEntryUi(
                        "sample-$index", "2026年10月26日", "20:16", if (index == 1) "你" else "我", "小便签",
                        body = listOf("风有点凉，想把手放进你的口袋。", "今天忙忙碌碌。\n还好，回家有你。", "等下次见面，一起去看看秋天吧 ♡")[index],
                        mine = index != 1, littleNote = true,
                        notePaper = com.twomemory.model.NotePaperAppearance(style,
                            listOf(com.twomemory.model.NotePaperColor.CREAM, com.twomemory.model.NotePaperColor.ROSE,
                                com.twomemory.model.NotePaperColor.MIST)[index])), {}, alternate = index == 1)
                }
            }
        } }
        saveCapture("little-note-timeline.png")
    }
    private fun capture(name: String) {
        compose.setContent { TwoMemoryTheme { LittleNoteComposer("下班路上看到一只小狗，\n跑得摇摇晃晃。\n\n突然就很想你。", {}, EntryVisibility.SHARED,
            {}, false, false, null, {}, {}, {}) } }
        compose.onNodeWithContentDescription("贴好便签").assertIsDisplayed()
        saveCapture(name)
    }
    private fun saveCapture(name: String) {
        val file = java.io.File("../../releases/v45/$name").also { it.parentFile.mkdirs() }
        val bitmap = compose.onRoot().captureToImage().let { it.asAndroidBitmap() }
        file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
