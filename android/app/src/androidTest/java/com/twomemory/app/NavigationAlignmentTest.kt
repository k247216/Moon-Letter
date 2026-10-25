package com.twomemory.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.platform.LocalDensity
import com.twomemory.designsystem.MoonLetterBottomNavigation
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFiveLabelsShareOneBaseline() {
        compose.setContent { TwoMemoryTheme { MoonLetterBottomNavigation("timeline") {} } }
        val labels = listOf("时光", "相册", "＋记录", "地图", "我们")
        val bounds = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        bounds.drop(1).forEach { assertEquals(bounds.first().top, it.top, 1f) }
    }

    @Test fun compactTabsKeepAccessibleTargetsAndSelection() {
        var density = 1f
        compose.setContent { density = LocalDensity.current.density
            TwoMemoryTheme { MoonLetterBottomNavigation("timeline") {} }
        }
        val tab = compose.onNodeWithContentDescription("时光")
        tab.assertIsSelected()
        val height = tab.fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("A compact tab remains tappable: $height dp", height in 48f..62f)
    }
}
