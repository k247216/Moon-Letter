package com.twomemory.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.MoonLetterTabs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fixedFiveTabOrderAndCenterEditor() {
        composeRule.setContent { TwoMemoryApp() }
        assert(MoonLetterTabs.map { it.label } == listOf("时光", "相册", "＋记录", "地图", "我们"))
        MoonLetterTabs.forEach {
            composeRule.onNodeWithContentDescription(it.contentDescription).assertIsDisplayed()
        }
        listOf("时光", "相册", "＋记录", "地图", "我们").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("我们的时光").assertIsDisplayed()
    }

    @Test
    fun coverActionHasAnExplicitSemanticLabel() {
        composeRule.setContent { TwoMemoryApp() }
        composeRule.onNodeWithContentDescription("更换背景图").assertIsDisplayed()
    }
}
