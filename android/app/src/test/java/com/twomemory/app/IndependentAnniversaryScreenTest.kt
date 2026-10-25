package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import com.twomemory.designsystem.TwoMemoryTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentAnniversaryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun originYearMustBeChosenBeforePlaceholderCanBeSaved() {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            IndependentAnniversaryScreen(IndependentAnniversaryState(), {}, {}, {}, LocalDate.of(2026, 10, 13))
        } } }
        compose.onNodeWithContentDescription("保存独立纪念日").assertIsNotEnabled()
        compose.onNodeWithText("请选择原始年份").assertIsDisplayed()
    }

    @Test fun savingDisablesDuplicateSubmissionEvenWhenTheDateIsValid() {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            IndependentAnniversaryScreen(IndependentAnniversaryState(draft = IndependentAnniversaryDraft(year = 2020), saving = true),
                {}, {}, {}, LocalDate.of(2026, 10, 13))
        } } }
        compose.onNodeWithContentDescription("保存独立纪念日").assertIsNotEnabled()
    }

    @Test fun capturesIndependentAnniversaryPhoneWithSeparatedDateTimeAndSaveControls() =
        captureFixture("anniversary-phone.png")

    @Test @Config(qualifiers = "w960dp-h720dp-mdpi")
    fun capturesIndependentAnniversaryTabletWithSeparatedDateTimeAndSaveControls() =
        captureFixture("anniversary-tablet.png")

    private fun captureFixture(name: String) {
        // Synthetic, not persisted, not an invented server acknowledgement.
        val fixture = IndependentAnniversaryState(draft = IndependentAnniversaryDraft(year = 2020, reminderTime = "18:45"))
        var submitted = false
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            IndependentAnniversaryScreen(fixture, {}, { submitted = true }, {}, LocalDate.of(2026, 10, 13))
        } } }
        val date = compose.onNodeWithContentDescription("选择原始纪念日期")
        val time = compose.onNodeWithContentDescription("选择本机纪念日提醒时间")
        val save = compose.onNodeWithContentDescription("保存独立纪念日")
        date.assertIsDisplayed(); time.assertIsDisplayed(); save.assertIsDisplayed().assertIsEnabled()
        val dateBounds = date.fetchSemanticsNode().boundsInRoot
        val timeBounds = time.fetchSemanticsNode().boundsInRoot
        val saveBounds = save.fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Date and time touch targets must not overlap", dateBounds.bottom <= timeBounds.top)
        assertTrue("Time and save touch targets must not overlap", timeBounds.bottom <= saveBounds.top)
        assertTrue("Save control must fit the visible page", saveBounds.bottom <= rootBounds.bottom)
        compose.onNodeWithText("原始公历日期 · 2020-9-24").assertIsDisplayed()
        compose.onNodeWithText("本机提醒时间 · 18:45").assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("../../releases/v43").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        save.performClick()
        compose.runOnIdle { assertTrue("A known valid year must allow submission", submitted) }
    }
}
