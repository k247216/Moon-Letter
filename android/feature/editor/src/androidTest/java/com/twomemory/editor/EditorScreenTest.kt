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
        composeRule.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test
    fun personalEditorSignsWithTheRealNameAndNeverInventsOne() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                    ownName = "阿屿屿",
                )
            }
        }
        composeRule.onNodeWithText("阿屿屿").assertIsDisplayed()
        composeRule.onNodeWithText("小满").assertDoesNotExist()
    }

    @Test
    fun anUnnamedWriterFallsBackToPronounsNotToAPlaceholderPerson() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                )
            }
        }
        composeRule.onNodeWithText("我").assertIsDisplayed()
    }

    @Test
    fun sharedEditorShowsOneRealAuthorAndPromisesThePartnerOnlyInWords() {
        composeRule.setContent {
            TwoMemoryTheme {
                SharedEditorRoute(
                    state = EditorUiState(),
                    onPublish = {},
                    ownName = "小满呀",
                )
            }
        }
        composeRule.onNodeWithText("小满呀").assertIsDisplayed()
        composeRule.onNodeWithText("先写下你的部分，TA 可以补充自己的视角。").assertIsDisplayed()
        composeRule.onNodeWithText("阿屿").assertDoesNotExist()
    }
}
