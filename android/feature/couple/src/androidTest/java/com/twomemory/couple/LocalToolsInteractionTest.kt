package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.twomemory.designsystem.TwoMemoryTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class LocalToolsInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun invalidAnniversaryCannotBeSavedOrScheduled() {
        composeRule.setContent {
            TwoMemoryTheme { Box(Modifier.fillMaxSize()) {
                RelationshipToolsScreen(CoupleToolRoute.ANNIVERSARY, onBack = {},
                    initialAnniversaryName = "认识", initialAnniversaryDate = "2026-02-30")
            } }
        }
        // Disabled semantics do not require scrolling the entire oversized card into view.
        composeRule.onNodeWithText("保存纪念日").assertIsNotEnabled()
    }

    @Test fun invalidCapsuleCannotBeLocked() {
        composeRule.setContent {
            TwoMemoryTheme { Box(Modifier.fillMaxSize()) { RelationshipToolsScreen(CoupleToolRoute.CAPSULE, onBack = {},
                initialCapsuleTitle = "信", initialCapsuleBody = "原件",
                initialCapsuleUnlockDate = "2026-02-30") } }
        }
        composeRule.onNodeWithText("保存并锁定").assertIsNotEnabled()
    }

    @Test fun existingElapsedDateTimeLetterCanBeReadWithoutReplacingIt() {
        composeRule.setContent {
            TwoMemoryTheme { Box(Modifier.fillMaxSize()) { RelationshipToolsScreen(CoupleToolRoute.CAPSULE, onBack = {},
                initialCapsuleTitle = "旧信", initialCapsuleBody = "原来的正文必须保留",
                initialCapsuleUnlockDate = "2020-01-01T12:30", initialCapsuleLocked = true) } }
        }
        composeRule.onNodeWithText("原来的正文必须保留").assertExists()
        composeRule.onNodeWithText("原来的正文必须保留").performScrollTo().assertIsDisplayed()
    }

    @Test fun settingsOffersExplicitLocalNotificationCheckAndSystemSettings() {
        var tests = 0
        var settings = 0
        composeRule.setContent {
            TwoMemoryTheme { Box(Modifier.fillMaxSize()) {
                CoupleScreen(state = CoupleUiState(), onOwnNameChange = {}, onThemeChange = {},
                    onSendTestNotification = { tests++; "通知权限未开启" },
                    onOpenNotificationSettings = { settings++ })
            } }
        }
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("发送测试通知").performScrollTo().performClick()
        composeRule.onNodeWithText("通知权限未开启").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("系统通知设置").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, tests); assertEquals(1, settings) }
    }

    @Test fun capsuleUsesSelectedDateAndHasNoDuplicateNotificationTest() {
        var saved: CapsuleDraft? = null
        composeRule.setContent { TwoMemoryTheme { Box(Modifier.fillMaxSize()) {
            RelationshipToolsScreen(CoupleToolRoute.CAPSULE, onBack = {}, onSaveCapsule = { saved = it },
                initialCapsuleTitle = "测试信", initialCapsuleBody = "保留这封信", initialCapsuleUnlockDate = "2099-01-01")
        } } }
        composeRule.onAllNodesWithText("发送测试通知").assertCountEquals(0)
        composeRule.onNodeWithText("保存并锁定").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("保留这封信", saved?.body)
            assertEquals("2099-01-01", saved?.unlockDate)
            org.junit.Assert.assertFalse(capsuleIsDue(saved!!.unlockDate))
        }
        composeRule.onAllNodesWithText("保留这封信").assertCountEquals(0)
    }
}
