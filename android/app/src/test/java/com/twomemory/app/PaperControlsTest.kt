package com.twomemory.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.twomemory.designsystem.*
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.graphics.asAndroidBitmap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaperControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun wheelDateDoesNotCommitUntilConfirmedAndMonthEndsRemainValid() {
        var picked: LocalDate? = null
        compose.setContent { TwoMemoryTheme { PaperDatePickerDialog(LocalDate.of(2024, 1, 31), {}, { picked = it }) } }
        compose.onNodeWithContentDescription("月份").performScrollToNode(hasTestTag("month-2"))
        compose.onNodeWithTag("month-2").performClick()
        compose.waitForIdle()
        assertNull(picked)
        compose.onNodeWithText("好").performClick()
        assertEquals(LocalDate.of(2024, 2, 29), picked)
    }
    @Test fun confirmationAndModeSwitchWaitForTheMovingWheel() {
        compose.setContent { TwoMemoryTheme { PaperDatePickerDialog(LocalDate.of(2024, 1, 31), {}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("月份").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -60f))
        }
        compose.onNodeWithText("好").assertIsNotEnabled()
        compose.onNodeWithText("用日历找一天").assertIsNotEnabled()
        compose.onNodeWithContentDescription("月份").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText("好").assertIsEnabled()
    }
    @Test fun twoWheelsSettlingTogetherDoNotDiscardEachOthersFields() {
        var picked: LocalDate? = null
        compose.setContent { TwoMemoryTheme { PaperDatePickerDialog(LocalDate.of(2023, 1, 31), {}, { picked = it }) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("year-2024").performClick()
        compose.onNodeWithTag("month-2").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("好").performClick()
        assertEquals(2024, picked!!.year)
        assertEquals(2, picked!!.monthValue)
    }
    @Test fun reminderPickerRestoresItsUnconfirmedChoiceAcrossRecreation() {
        val restoration = StateRestorationTester(compose)
        var edited: IndependentAnniversaryDraft? = null
        restoration.setContent { TwoMemoryTheme {
            IndependentAnniversaryScreen(IndependentAnniversaryState(draft = IndependentAnniversaryDraft(
                year = 2020, reminderTime = "18:45")), { edited = it }, {}, {})
        } }
        compose.onNodeWithContentDescription("选择本机纪念日提醒时间").performClick()
        compose.onNodeWithTag("hour-19").performClick()
        compose.waitForIdle()
        assertNull(edited)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("把时间轻轻定下来").assertExists()
        compose.onNodeWithText("好").performClick()
        assertEquals("19:45", edited!!.reminderTime)
    }
    @Test fun cancellingTimeDoesNotWriteAValue() {
        var picked: LocalTime? = null; var dismissed = false
        compose.setContent { TwoMemoryTheme { PaperTimePickerDialog(LocalTime.of(23, 59),
            { dismissed = true }, { picked = it }) } }
        compose.onNodeWithText("先不改").performClick()
        assertTrue(dismissed); assertNull(picked)
    }
    @Test fun reopeningAnUntouchedTimePreservesItsMinutes() {
        var picked: LocalTime? = null
        compose.setContent { TwoMemoryTheme { PaperTimePickerDialog(LocalTime.of(23, 59), {}, { picked = it }) } }
        compose.onNodeWithText("好").performClick()
        assertEquals(LocalTime.of(23, 59), picked)
    }
    @Test fun paperButtonRetainsItsDisabledAndSingleClickContract() {
        var count = 0; var enabled by mutableStateOf(false)
        compose.setContent { TwoMemoryTheme { PaperTextButton({ count++ }, enabled = enabled) {
            androidx.compose.material3.Text("纸面操作")
        } } }
        compose.onNodeWithText("纸面操作").assertIsNotEnabled().performClick()
        assertEquals(0, count)
        compose.runOnIdle { enabled = true }
        compose.onNodeWithText("纸面操作").performClick()
        assertEquals(1, count)
    }
    @Test fun calendarAlternativeIsAvailableWithoutChangingTheInitialDate() {
        var picked: LocalDate? = null
        compose.setContent { TwoMemoryTheme { PaperDatePickerDialog(LocalDate.of(2020, 9, 24), {}, { picked = it }) } }
        compose.onNodeWithText("用日历找一天").performClick()
        compose.onNodeWithText("用滚轮慢慢选").assertExists()
        compose.onNodeWithText("好").performClick()
        assertEquals(LocalDate.of(2020, 9, 24), picked)
    }
    @Test @Config(qualifiers="w891dp-h411dp-mdpi")
    fun largeTextLandscapeStillAllowsConfirmation() {
        compose.setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(1f, 1.5f)) { TwoMemoryTheme {
            PaperDatePickerDialog(LocalDate.of(2024, 2, 29), {}, {})
        } } }
        compose.onNodeWithText("好").assertIsDisplayed()
        compose.onNodeWithText("先不改").assertIsDisplayed()
    }
    @Test fun capturesPaperDatePhone() {
        compose.setContent { TwoMemoryTheme { PaperDatePickerDialog(LocalDate.of(2024, 2, 29), {}, {}) } }
        capture("paper-date-phone.png")
    }
    @Test @Config(qualifiers="w800dp-h1100dp-mdpi")
    fun capturesPaperTimeTablet() {
        compose.setContent { TwoMemoryTheme { PaperTimePickerDialog(LocalTime.of(23, 59), {}, {}) } }
        capture("paper-time-tablet.png")
    }
    private fun capture(name: String) {
        compose.onNodeWithText("好").assertIsDisplayed()
        val image = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val file = java.io.File("../../releases/v43/$name").also { it.parentFile.mkdirs() }
        file.outputStream().use { assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
