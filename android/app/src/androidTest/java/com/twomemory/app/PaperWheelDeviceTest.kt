package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.twomemory.designsystem.PaperDatePickerDialog
import com.twomemory.designsystem.TwoMemoryTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PaperWheelDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun monthEndSelectionConfirmsTheDateVisibleOnTheWheel() {
        var chosen: LocalDate? = null
        compose.setContent { TwoMemoryTheme { PaperDatePickerDialog(LocalDate.of(2024, 1, 31), {}, { chosen = it }) } }
        compose.onNodeWithTag("month-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("好").assertIsEnabled().performClick()
        assertEquals(LocalDate.of(2024, 2, 29), chosen)
    }
}
