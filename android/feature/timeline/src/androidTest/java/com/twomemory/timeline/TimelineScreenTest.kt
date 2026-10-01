package com.twomemory.timeline

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun timelineIsContinuousAndShowsBothIdentityMarkers() {
        var opened = ""
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    listOf(
                        TimelineEntryUi("1", "2026年9月30日", "20:18", "我", "此刻", body = "今天的正文"),
                        TimelineEntryUi("2", "2026年9月29日", "22:06", "我们", "一起", shared = true),
                    ),
                    onOpen = { opened = it },
                )
            }
        }
        composeRule.onNodeWithText("换一张").assertIsDisplayed()
        composeRule.onNodeWithText("2026年9月30日").assertIsDisplayed()
        composeRule.onNodeWithText("2026年9月29日").assertIsDisplayed()
        composeRule.onNodeWithText("共同").assertIsDisplayed()
        composeRule.onNodeWithText("今天的正文").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打开记录").performClick()
        assertEquals("1", opened)
    }
}
