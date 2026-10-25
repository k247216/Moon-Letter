package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.twomemory.couple.CoupleScreen
import com.twomemory.couple.CoupleUiState
import com.twomemory.designsystem.TwoMemoryTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Compose screen on a local JVM; no connected device or mocked screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestKeystoreApplication::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactAnniversaryEntryTest {
    @get:Rule val compose = createComposeRule()

    private val compactEntry = hasClickAction() and hasAnyDescendant(hasText("纪念日与倒计时"))
    private val allAnniversaryEntries = hasClickAction() and (
        hasAnyDescendant(hasText("纪念日与倒计时")) or
            hasAnyDescendant(hasContentDescription("查看重要日子"))
        )

    @Test fun unsetAnniversaryHasOnlyOneCompactEntranceWithoutLargePlaceholder() {
        var opened = 0
        show(onAnniversary = { opened++ })
        compose.onAllNodes(allAnniversaryEntries, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("留住重要的日子").assertDoesNotExist()
        compose.onNodeWithContentDescription("查看重要日子").assertDoesNotExist()
        compose.onNode(compactEntry, useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun confirmedAnniversaryStillHasExactlyOneEntrance() {
        show(name = "V40 合成纪念", date = LocalDate.now().plusDays(7).toString())
        compose.onAllNodes(allAnniversaryEntries, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("查看重要日子").assertDoesNotExist()
        compose.onNode(compactEntry, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun confirmedDetailsBelongToCompactRowInsteadOfSeparateCountdownBlock() {
        val date = LocalDate.now().plusDays(7).toString()
        show(name = "V40 合成纪念", date = date)
        val rowWithDetails = compactEntry and
            hasAnyDescendant(hasText("V40 合成纪念", substring = true)) and
            hasAnyDescendant(hasText(date, substring = true)) and
            hasAnyDescendant(hasText("还有 7 天", substring = true))
        compose.onNode(rowWithDetails, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun identityAndOtherToolRoutesRemainReachable() {
        val opened = mutableListOf<String>()
        show(onAnniversary = { opened += "anniversary" }, onOccasions = { opened += "cards" },
            onCapsule = { opened += "capsule" }, onExport = { opened += "export" })
        compose.onNodeWithText("林林").assertIsDisplayed()
        compose.onNodeWithText("星星").assertIsDisplayed()
        compose.onNodeWithContentDescription("修改名字").assertIsDisplayed()
        compose.onNodeWithText("纪念日与倒计时").performScrollTo().performClick()
        compose.onNodeWithText("生日与心意卡").performScrollTo().performClick()
        compose.onNodeWithText("时间胶囊").performScrollTo().performClick()
        compose.onNodeWithText("数据与导出").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("anniversary", "cards", "capsule", "export"), opened) }
    }

    private fun show(
        name: String = "",
        date: String = "",
        onAnniversary: () -> Unit = {},
        onOccasions: () -> Unit = {},
        onCapsule: () -> Unit = {},
        onExport: () -> Unit = {},
    ) {
        compose.setContent {
            TwoMemoryTheme {
                CoupleScreen(
                    state = CoupleUiState(ownName = "林林", partnerName = "星星", draftName = "林林"),
                    onOwnNameChange = {}, onThemeChange = {},
                    onOpenAnniversary = onAnniversary, onOpenOccasions = onOccasions,
                    onOpenCapsule = onCapsule, onOpenExport = onExport,
                    anniversaryName = name, anniversaryDate = date, anniversaryRepeats = false,
                )
            }
        }
    }
}
