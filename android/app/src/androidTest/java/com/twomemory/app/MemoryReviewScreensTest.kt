package com.twomemory.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryReviewScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pastTodayShowsOnlyTheHistoricalReviewCopy() {
        composeRule.setContent {
            TwoMemoryTheme {
                MemoryReviewScreen(
                    route = MemoryReviewRoute.PAST_TODAY,
                    memories = listOf(ReviewMemoryUi("past:e1", "2025年10月1日", "20:18", "小满", "去看月亮", "湖边很安静")),
                    onBack = {},
                    onOpenEntry = {},
                )
            }
        }
        composeRule.onNodeWithText("过去的今天").assertIsDisplayed()
        composeRule.onNodeWithText("湖边很安静").assertIsDisplayed()
    }

    @Test
    fun weeklySummaryExplainsItsDeterministicSources() {
        composeRule.setContent {
            TwoMemoryTheme {
                MemoryReviewScreen(
                    route = MemoryReviewRoute.WEEKLY_SUMMARY,
                    memories = listOf(ReviewMemoryUi("week:e1", "2026年10月1日", "20:18", "阿屿", null, "今天读完了一章", listOf("照片"), "杭州")),
                    onBack = {},
                    onOpenEntry = {},
                )
            }
        }
        composeRule.onNodeWithText("本周小结").assertIsDisplayed()
        composeRule.onNodeWithText("不调用 AI", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("杭州").assertIsDisplayed()
    }
}
