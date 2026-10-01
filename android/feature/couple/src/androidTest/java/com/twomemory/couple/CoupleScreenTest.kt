package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented evidence for the rename entry. Not part of the JVM suite: it
 * needs a device or emulator, so it stays unverified until
 * `connectedDebugAndroidTest` runs it.
 */
@RunWith(AndroidJUnit4::class)
class CoupleScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(state: CoupleUiState, onSaveName: () -> Unit = {}) {
        composeRule.setContent {
            TwoMemoryTheme {
                CoupleScreen(
                    state = state,
                    onOwnNameChange = {},
                    onThemeChange = {},
                    onSaveName = onSaveName,
                )
            }
        }
    }

    @Test
    fun aMissingNamePromptsHerInsteadOfInventingOne() {
        render(CoupleUiState())
        composeRule.onNodeWithText("取个名字").assertIsDisplayed()
        composeRule.onNodeWithText("伴侣").assertIsDisplayed()
        composeRule.onNodeWithText("小满").assertDoesNotExist()
        composeRule.onNodeWithText("阿屿").assertDoesNotExist()
    }

    @Test
    fun theSaveButtonIsTheOnlyThingThatCommitsAName() {
        var saves = 0
        render(CoupleUiState(ownName = "阿屿", partnerName = "小满", draftName = "阿屿屿"), { saves++ })
        composeRule.onNodeWithContentDescription("修改名字").performClick()
        composeRule.onNodeWithText("阿屿屿").assertIsDisplayed()
        composeRule.onNodeWithText("暖米色").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()
        assertEquals(1, saves)
    }
}
