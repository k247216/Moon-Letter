package com.twomemory.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class EditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun personalEditorKeepsExactTimeAndToolbarOrderVisible() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(occurrenceTime = Instant.parse("2026-09-30T12:18:00Z")),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                )
            }
        }
        composeRule.onNodeWithText("2026年9月30日 · 20:18").assertIsDisplayed()
        listOf("图片", "视频", "语音", "音乐", "城市", "更多").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("发布这篇记录").assertIsDisplayed()
    }

    @Test
    fun sharedEditorShowsAuthorLabels() {
        composeRule.setContent {
            TwoMemoryTheme {
                SharedEditorRoute(
                    state = EditorUiState(),
                    blocks = listOf(SharedBlockUi("我", "今天很开心"), SharedBlockUi("对方", "我也记得")),
                    onPublish = {},
                )
            }
        }
        composeRule.onNodeWithText("同一段回忆，两个视角").assertIsDisplayed()
        composeRule.onNodeWithText("我").assertIsDisplayed()
        composeRule.onNodeWithText("对方").assertIsDisplayed()
    }
}
