package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
class IndependentAnniversaryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun openProfile() {
        SyncSession.save(compose.activity, "synthetic-independent-navigation", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme { AppNavigation() } } }
        compose.onNodeWithContentDescription("我们").performClick()
    }
    @Test fun anniversaryEntryOpensItsIndependentDateEditorNotTheCardLibrary() {
        openProfile()
        compose.onNodeWithText("纪念日与倒计时").performScrollTo().performClick()
        compose.onNodeWithContentDescription("保存独立纪念日").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("值得记住的日子").assertDoesNotExist()
    }
    @Test fun birthdaysAndSurpriseCardsRemainAccessibleThroughTheirOwnEntry() {
        openProfile()
        compose.onNodeWithText("生日与心意卡").performScrollTo().performClick()
        compose.onNodeWithText("值得记住的日子").assertIsDisplayed()
        compose.onNodeWithContentDescription("保存独立纪念日").assertDoesNotExist()
    }
}
