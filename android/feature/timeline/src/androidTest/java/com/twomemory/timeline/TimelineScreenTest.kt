package com.twomemory.timeline

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun timelineIsContinuousAndShowsBothIdentityMarkers() {
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    listOf(
                        TimelineEntryUi("1", "2026年9月30日", "20:18", "我", "此刻"),
                        TimelineEntryUi("2", "2026年9月29日", "22:06", "我们", "一起", shared = true),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("2026年9月30日").assertIsDisplayed()
        composeRule.onNodeWithText("2026年9月29日").assertIsDisplayed()
        composeRule.onNodeWithText("共同记录 · 我们").assertIsDisplayed()
    }
}
