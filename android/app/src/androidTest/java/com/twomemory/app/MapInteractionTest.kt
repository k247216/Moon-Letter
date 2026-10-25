package com.twomemory.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import com.twomemory.designsystem.CitySelectionMap
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test

class MapInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun basemapChoiceIsSecondaryNotALargePermanentBanner() {
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen() } }
        compose.onNodeWithText("街道底图 · 联网").assertDoesNotExist()
        compose.onNodeWithText("离线地图").assertDoesNotExist()
        compose.onNodeWithContentDescription("地图样式").performClick()
        compose.onNodeWithText("地理手账").assertExists()
        compose.onNodeWithText("街道地图").assertExists()
    }

    @Test fun searchSelectsARealNonCapitalCityAndFiltersStories() {
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen(stories = listOf(
            CityStoryUi("hu", "hu", "湖州", "10月2日", "湖州散步", "一起喝了茶", "星星"),
            CityStoryUi("hang", "hang", "杭州", "10月2日", "杭州晚风", "西湖走走", "月亮"),
        )) } }
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNodeWithContentDescription("城市名称或省份").performTextInput("湖州")
        compose.onNode(hasText("湖州") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("湖州的故事").assertExists()
        compose.onNodeWithText("湖州散步").assertExists()
        compose.onNodeWithText("杭州晚风").assertDoesNotExist()
    }

    @Test fun searchingCurrentCityBringsItBackAfterPanningAway() {
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen() } }
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNodeWithContentDescription("城市名称或省份").performTextInput("杭州")
        compose.onNode(hasText("杭州") and !hasSetTextAction()).performClick()
        val map = compose.onNodeWithContentDescription("真实中国城市地图，双指缩放、拖动，点击地图或城市选择")
        val pin = compose.onNode(hasContentDescription("杭州，0篇记录，已选择"))
        compose.waitUntil(10_000) { pin.isDisplayed() }
        map.performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNodeWithContentDescription("城市名称或省份").performTextInput("杭州")
        compose.onNode(hasText("杭州") and !hasSetTextAction()).performClick()
        compose.waitUntil(10_000) { pin.isDisplayed() }
        pin.assertIsDisplayed()
    }

    @Test fun longCityLabelsNeverOverlapOtherCityTouchTargets() {
        compose.setContent { TwoMemoryTheme {
            CitySelectionMap("阿坝藏族羌族自治州", mapOf("阿坝藏族羌族自治州" to 1, "甘孜藏族自治州" to 1), {},
                Modifier.height(420.dp))
        } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("篇记录", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        val targets = compose.onAllNodes(hasContentDescription("篇记录", substring = true) and hasClickAction()).fetchSemanticsNodes()
        targets.forEachIndexed { index, target -> targets.drop(index + 1).forEach { other ->
            assertFalse("City targets must not overlap: ${target.boundsInRoot}, ${other.boundsInRoot}",
                target.boundsInRoot.overlaps(other.boundsInRoot))
        } }
    }
}
