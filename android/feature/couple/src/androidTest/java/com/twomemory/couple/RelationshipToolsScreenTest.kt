package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelationshipToolsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anniversaryRouteStartsEmptyAndNeedsBothFieldsToSave() {
        composeRule.setContent {
            TwoMemoryTheme {
                RelationshipToolsScreen(CoupleToolRoute.ANNIVERSARY, onBack = {})
            }
        }
        composeRule.onNodeWithText("纪念日与倒计时").assertIsDisplayed()
        composeRule.onNodeWithText("你们的纪念日").assertIsDisplayed()
        composeRule.onNodeWithText("保存纪念日").assertIsNotEnabled()
    }

    @Test
    fun capsuleRouteDoesNotExposePlaintextBeforeUnlock() {
        composeRule.setContent {
            TwoMemoryTheme {
                Box(Modifier.fillMaxSize()) {
                    RelationshipToolsScreen(CoupleToolRoute.CAPSULE, onBack = {},
                        initialCapsuleTitle = "信", initialCapsuleBody = "未开启的正文",
                        initialCapsuleUnlockDate = "2099-01-01T12:30", initialCapsuleLocked = true)
                }
            }
        }
        composeRule.onNodeWithText("时间胶囊已锁定").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("未开启的正文").assertDoesNotExist()
    }

    @Test
    fun exportRouteOffersExplicitScopes() {
        composeRule.setContent {
            TwoMemoryTheme {
                RelationshipToolsScreen(CoupleToolRoute.EXPORT, onBack = {})
            }
        }
        composeRule.onNodeWithText("本机已缓存的记录").assertIsDisplayed()
        composeRule.onNodeWithText("按时间范围").assertIsDisplayed()
        composeRule.onNodeWithText("全部回忆").assertIsDisplayed()
        composeRule.onNodeWithText("开始导出").assertIsDisplayed()
    }
}
