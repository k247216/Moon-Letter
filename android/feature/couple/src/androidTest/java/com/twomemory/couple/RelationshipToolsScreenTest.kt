package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                RelationshipToolsScreen(CoupleToolRoute.CAPSULE, onBack = {})
            }
        }
        composeRule.onNodeWithText("开启日期前，这台手机不显示正文，导出不包含它；对面那台也读不到这封信。").assertIsDisplayed()
        composeRule.onNodeWithText("保存并锁定").assertIsDisplayed()
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
