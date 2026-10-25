package com.twomemory.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PaletteHarmonyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun choiceControlsUseJournalColorsRatherThanDefaultPurple() {
        compose.setContent { TwoMemoryTheme {
            assertEquals(TwoMemoryColors.WarmBeigeSage, MaterialTheme.colorScheme.secondary)
            assertEquals(TwoMemoryColors.WarmBeigeAccentSoft, MaterialTheme.colorScheme.secondaryContainer)
            assertEquals(TwoMemoryColors.WarmBeigeLine, MaterialTheme.colorScheme.outlineVariant)
        } }
    }
}
