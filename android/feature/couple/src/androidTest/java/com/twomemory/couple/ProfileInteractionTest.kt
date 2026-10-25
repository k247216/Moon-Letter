package com.twomemory.couple

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class ProfileInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsAreHiddenUntilOpenedAndCloseAgain() {
        compose.setContent { TwoMemoryTheme { CoupleScreen(CoupleUiState(), {}, {}) } }
        compose.onNodeWithText("显示主题").assertDoesNotExist()
        compose.onNodeWithText("每周旧笺提醒").assertDoesNotExist()
        compose.onNodeWithText("生成配对码").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("显示主题").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("纯白").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭设置").performScrollTo().performClick()
        compose.onNodeWithText("显示主题").assertDoesNotExist()
    }

    @Test fun fourEntrancesKeepAllFiveToolCallbacksReachable() {
        val opened = mutableListOf<String>()
        compose.setContent {
            TwoMemoryTheme {
                CoupleScreen(CoupleUiState(), {}, {},
                    onOpenAnniversary = { opened += "anniversary" },
                    onOpenCapsule = { opened += "capsule" },
                    onOpenExport = { opened += "export" },
                    onOpenPastToday = { opened += "past" },
                    onOpenWeeklySummary = { opened += "weekly" })
            }
        }
        compose.onNodeWithText("纪念日与倒计时").performScrollTo().performClick()
        compose.onNodeWithText("时间胶囊").performScrollTo().performClick()
        compose.onNodeWithText("数据与导出").performScrollTo().performClick()
        compose.onNodeWithText("回看与小结").performScrollTo().performClick()
        compose.onNodeWithText("过去的今天").performClick()
        compose.onNodeWithText("回看与小结").performScrollTo().performClick()
        compose.onNodeWithText("本周小结").performClick()
        compose.runOnIdle { assertEquals(listOf("anniversary", "capsule", "export", "past", "weekly"), opened) }
    }

    @Test fun neutralInitialsAndEditingUseActualNames() {
        var saved = ""
        var avatarClicks = 0
        compose.setContent {
            var state by remember { mutableStateOf(CoupleUiState(ownName = "林林", partnerName = "星星", draftName = "林林")) }
            TwoMemoryTheme {
                CoupleScreen(state, { state = state.copy(draftName = it) }, {},
                    onSaveName = { saved = state.draftName; state = state.copy(ownName = saved) },
                    onOwnAvatarChange = { avatarClicks++ })
            }
        }
        compose.onNodeWithContentDescription("林林 头像").performClick()
        compose.onNodeWithText("林", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("星", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("修改名字").performClick()
        compose.onNodeWithText("我的名字").performTextReplacement("新的称呼")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("新的称呼").assertIsDisplayed()
        compose.runOnIdle { assertEquals("新的称呼", saved); assertEquals(1, avatarClicks) }
    }

    @Test fun settingsKeepThemeNotificationScheduleAndPairingActions() {
        var selectedTheme: MoonLetterTheme? = null
        var weekly: Boolean? = null
        var notice: Boolean? = null
        var day: DayOfWeek? = null
        var generated = 0
        var hidden = 0
        compose.setContent {
            TwoMemoryTheme {
                CoupleScreen(CoupleUiState(pairingCode = "local-test-code"), {}, { selectedTheme = it },
                    onGenerateCode = { generated++ }, onHideCode = { hidden++ },
                    onWeeklyReviewChange = { weekly = it }, onNewEntryNoticeChange = { notice = it },
                    onReviewTimeChange = { value, _ -> day = value })
            }
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("纯白").performScrollTo().performClick()
        compose.onNodeWithContentDescription("每周旧笺提醒").performScrollTo().performClick()
        compose.onNodeWithText("周一").performScrollTo().performClick()
        compose.onNodeWithContentDescription("对方写了新的").performScrollTo().performClick()
        compose.onNodeWithText("换一个新的码").performScrollTo().performClick()
        compose.onNodeWithText("藏起来").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(MoonLetterTheme.PURE_WHITE, selectedTheme)
            assertEquals(false, weekly); assertEquals(false, notice)
            assertEquals(DayOfWeek.MONDAY, day)
            assertEquals(1, generated); assertEquals(1, hidden)
        }
    }
}
