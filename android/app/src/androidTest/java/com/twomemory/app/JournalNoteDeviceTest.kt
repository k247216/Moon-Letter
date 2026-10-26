package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.timeline.*
import com.twomemory.model.BlockType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JournalNoteDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun requireLayout() {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("expectedLayout") == "tablet") {
            val config = compose.activity.resources.configuration
            assertTrue(config.screenWidthDp >= 600 && config.screenWidthDp > config.screenHeightDp)
        }
    }
    @Test fun openPaperAndReplyWithRealKeyboard() {
        requireLayout()
        val header = TimelineEntryUi("device-note", "2026年10月26日", "20:16", "我", "小便签", body = "想你啦", mine = true, littleNote = true)
        var opened by mutableStateOf(false); var draft by mutableStateOf(""); var sent = ""
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            if (!opened) JournalEntryCard(header, { opened = true })
            else EntryDetailScreen(EntryDetailUi(header, listOf(EntryBlockUi("text", BlockType.TEXT, "想你啦", null, null)),
                emptyList(), draft = draft), { opened = false }, { draft = it }, { sent = draft })
        } } }
        compose.onNodeWithContentDescription("打开小便签").performClick()
        compose.onNodeWithText("写一句回应……").performClick().performTextInput("我也想你。")
        compose.waitUntil(8_000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithText("发送").assertIsDisplayed().performClick()
        assertEquals("我也想你。", sent)
    }
    @Test fun longPreviewOpensTheCompletePaper() {
        requireLayout()
        val body = "今天的小快乐，想留给你。\n".repeat(9)
        val header = TimelineEntryUi("long-paper", "2026年10月26日", "20:16", "我", "小便签", body = body, mine = true, littleNote = true)
        var opened by mutableStateOf(false)
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            if (!opened) JournalEntryCard(header, { opened = true })
            else EntryDetailScreen(EntryDetailUi(header, listOf(EntryBlockUi("text", BlockType.TEXT, body, null, null)), emptyList()), {}, {}, {})
        } } }
        compose.onNodeWithText("轻点，读完这张纸条").assertExists()
        compose.onNodeWithContentDescription("打开小便签").performClick()
        compose.onNodeWithText(body).assertExists()
    }
}
