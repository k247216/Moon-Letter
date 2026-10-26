package com.twomemory.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.timeline.*
import com.twomemory.model.BlockType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JournalNoteReadingTest {
    @get:Rule val compose = createComposeRule()
    private fun note(id: String, body: String) = TimelineEntryUi(id, "2026年10月26日", "20:16", "我", "小便签",
        body = body, mine = true, littleNote = true)
    @Test fun shortPaperFitsContentAndSignatureIsOutsidePaper() {
        compose.setContent { TwoMemoryTheme { Column(Modifier.fillMaxSize()) {
            JournalEntryCard(note("short", "想你啦"), {})
            JournalEntryCard(note("long", "今天有好多好多事情想要和你说。\n".repeat(8)), {})
        } } }
        val short = compose.onNodeWithTag("note-paper-short", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val long = compose.onNodeWithTag("note-paper-long", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(short.right - short.left < long.right - long.left)
        assertTrue(short.bottom - short.top < long.bottom - long.top)
        val signature = compose.onNodeWithTag("note-signature-short", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(signature.top >= short.bottom)
    }
    @Test fun noteDetailHasOneDateAndKeepsReplyDraftAndSendBehaviour() {
        var draft by mutableStateOf("新的一句"); var sent = ""
        compose.setContent { TwoMemoryTheme { EntryDetailScreen(EntryDetailUi(note("reading", "想你啦"),
            listOf(EntryBlockUi("body", BlockType.TEXT, "想你啦", null, null)),
            listOf(EntryCommentUi("reply", "对方", "我也是 ♡", "10月26日 20:20", false)), draft = draft),
            {}, { draft = it }, { sent = draft }) } }
        compose.onAllNodesWithText("2026年10月26日", substring = true).assertCountEquals(1)
        compose.onNodeWithText("我也是 ♡").assertExists()
        compose.onNodeWithText("发送").performClick()
        assertEquals("新的一句", sent)
    }
    @Test fun capturesSavedNoteAndReplyPaper() = captureReading("note-reading-phone.png")
    @Test @Config(qualifiers = "w800dp-h1100dp-mdpi")
    fun capturesTabletReading() = captureReading("note-reading-tablet.png")
    @Test @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun enlargedTextWrapsWithoutClippingPaperOrPosting() {
        compose.setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(1f, 1.5f)) { TwoMemoryTheme { Column {
                JournalEntryCard(note("large", "等下次见面，一起去看看秋天吧 ♡"), {})
            } } } }
        compose.onNodeWithText("等下次见面，一起去看看秋天吧 ♡").assertIsDisplayed()
        compose.onNodeWithTag("note-signature-large", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w411dp-h891dp-560dpi")
    fun highDensityShortPaperDoesNotIntroduceExtraWrapsOrEllipsis() {
        val body = "等你回家一起吃饭，今天也很想你。\n等你回家一起吃饭，今天也很想你。"
        compose.setContent { TwoMemoryTheme { JournalEntryCard(note("dense", body), {}) } }
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(body, useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(2, results.single().lineCount)
        assertFalse(results.single().hasVisualOverflow)
        assertFalse(results.single().isLineEllipsized(1))
    }
    private fun captureReading(name: String) {
        compose.setContent { TwoMemoryTheme { EntryDetailScreen(EntryDetailUi(note("reading", "今天的小快乐，想分给你一半。"),
            listOf(EntryBlockUi("body", BlockType.TEXT, "今天的小快乐，\n想分给你一半。", null, null)),
            listOf(EntryCommentUi("reply", "你", "那我把另一半的想念，也贴在这里 ♡", "10月26日 20:20", false))),
            {}, {}, {}) } }
        val file = java.io.File("../../releases/v45/$name").also { it.parentFile.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
