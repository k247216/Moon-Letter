package com.twomemory.couple

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RelationshipToolsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anniversaryRouteShowsLunarRuleAndSaveState() {
        composeRule.setContent {
            TwoMemoryTheme {
                RelationshipToolsScreen(CoupleToolRoute.ANNIVERSARY, onBack = {})
            }
        }
        composeRule.onNodeWithText("中秋，是你们的纪念日").assertIsDisplayed()
        composeRule.onNodeWithText("农历八月十五").assertIsDisplayed()
        composeRule.onNodeWithText("保存纪念日").assertIsDisplayed()
    }

    @Test
    fun capsuleRouteDoesNotExposePlaintextBeforeUnlock() {
        composeRule.setContent {
            TwoMemoryTheme {
                RelationshipToolsScreen(CoupleToolRoute.CAPSULE, onBack = {})
            }
        }
        composeRule.onNodeWithText("开启日期前，正文不会在任何设备的详情页、通知或导出结果中返回。").assertIsDisplayed()
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

    @Test
    fun explicitSolarAnniversaryGetsAnHonestLocalPreview() {
        assertEquals(
            "距离 2026-10-06 还有 5 天",
            anniversaryCountdown("2026-10-06", repeatsYearly = false, today = LocalDate.of(2026, 10, 1)),
        )
        assertEquals(null, anniversaryCountdown("农历八月十五", repeatsYearly = true, today = LocalDate.of(2026, 10, 1)))
    }
}
