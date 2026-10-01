package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.MoonLetterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoupleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileShowsBothThemeChoicesAndVersionEvidence() {
        composeRule.setContent {
            CoupleScreen(
                state = CoupleUiState(ownName = "小满", partnerName = "阿屿", draftName = "小满"),
                onOwnNameChange = {},
                onThemeChange = {},
                versionLabel = "M1 · 0.1.0",
            )
        }
        composeRule.onNodeWithText("小满").assertIsDisplayed()
        composeRule.onNodeWithText("阿屿").assertIsDisplayed()
        composeRule.onNodeWithText("暖米色").assertIsDisplayed()
        composeRule.onNodeWithText("纯白").assertIsDisplayed()
        composeRule.onNodeWithText("月笺 · M1 · 0.1.0").assertIsDisplayed()
    }

    @Test
    fun profileDoesNotClaimFutureRoutesAreAlreadyAvailable() {
        composeRule.setContent {
            CoupleScreen(
                state = CoupleUiState(theme = MoonLetterTheme.PURE_WHITE),
                onOwnNameChange = {},
                onThemeChange = {},
            )
        }
        composeRule.onNodeWithText("纪念日与倒计时").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("打开纪念日与倒计时").assertIsDisplayed()
    }

    @Test
    fun settingsIconExplainsWhereLocalControlsLive() {
        composeRule.setContent {
            CoupleScreen(
                state = CoupleUiState(),
                onOwnNameChange = {},
                onThemeChange = {},
            )
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("月笺设置").assertIsDisplayed()
    }
}
