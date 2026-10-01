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
class FutureFeatureScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun albumGroupsRealMediaByMonthAndReturnsToEntry() {
        composeRule.setContent {
            TwoMemoryTheme {
                AlbumPreviewScreen(
                    media = listOf(
                        AlbumMediaUi("b1", "e1", "2026年10月", "2026年10月1日", "22:07", "小满", "IMAGE", null, null),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("共同相册").assertIsDisplayed()
        composeRule.onNodeWithText("2026年10月").assertIsDisplayed()
        composeRule.onNodeWithText("2026年10月1日").assertIsDisplayed()
    }

    @Test
    fun mapKeepsCityStoriesReadableWithoutRealtimeLocation() {
        composeRule.setContent {
            TwoMemoryTheme {
                CityMapPreviewScreen(
                    stories = listOf(CityStoryUi("b1", "e1", "杭州", "2026年10月1日", "月亮落在湖边", "散步回来的路上", "阿屿")),
                )
            }
        }
        composeRule.onNodeWithText("我们的城市").assertIsDisplayed()
        composeRule.onNodeWithText("杭州").assertIsDisplayed()
        composeRule.onNodeWithText("散步回来的路上").assertIsDisplayed()
    }
}
