package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoupleScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun ownProfileAndThemeChoicesAreVisible() {
        composeRule.setContent {
            TwoMemoryTheme {
                CoupleScreen(CoupleUiState(), {}, {})
            }
        }
        composeRule.onNodeWithText("我的名字").assertIsDisplayed()
        composeRule.onNodeWithText("中秋节 · 农历八月十五").assertIsDisplayed()
        composeRule.onNodeWithText("米色").assertIsDisplayed()
        composeRule.onNodeWithText("纯白").assertIsDisplayed()
    }
}
