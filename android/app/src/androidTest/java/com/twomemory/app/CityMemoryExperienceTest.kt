package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

class CityMemoryExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyMapDoesNotInventHangzhouOrAVisit() {
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen() } }
        compose.onNodeWithText("全国回忆地图").assertExists()
        compose.onNodeWithText("先选一座城市").assertExists()
        compose.onNodeWithContentDescription("杭州，0篇记录，已选择").assertDoesNotExist()
        compose.onNodeWithText("故事留在去过的地方").assertDoesNotExist()
    }

    private fun stories() = listOf(
        CityStoryUi("old", "old", "杭州", "2026年9月1日", "湖边的风", "一起把这一刻收进回忆。", "小满", occurredAtEpochMillis = 100),
        CityStoryUi("latest", "latest", "武汉", "2026年10月4日", "江边散步", "晚风吹过，想起上次来这里。", "阿屿", occurredAtEpochMillis = 900),
        CityStoryUi("middle", "middle", "杭州", "2026年10月1日", "再见西湖", "隔了一段时间，再走同一条小路。", "小满", occurredAtEpochMillis = 400),
    )

    @Test fun asynchronouslyLoadedStoriesSelectLatestWithoutOverridingRestoredCity() {
        val loaded = mutableStateOf(emptyList<CityStoryUi>())
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen(loaded.value) } }
        compose.onNodeWithText("全国回忆地图").assertExists()
        compose.runOnIdle { loaded.value = stories() }
        compose.onNodeWithText("武汉的故事").assertExists()
        compose.onNodeWithText("江边散步").assertExists()
        compose.onNodeWithContentDescription("搜索城市").performClick()
        compose.onNodeWithContentDescription("城市名称或省份").performTextInput("杭州")
        compose.onNode(hasText("杭州") and !hasSetTextAction()).performClick()
        compose.runOnIdle { loaded.value = stories() + CityStoryUi("future", "future", "湖州", "2026年10月5日", "新故事", "正文", "阿屿", occurredAtEpochMillis = 1200) }
        compose.onNodeWithText("杭州的故事").assertExists()
    }

    @Test fun savedCityWinsAndOverviewShowsRealCountsThenOpensCityStories() {
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen(stories(), lastBrowsedCity = "杭州市") } }
        compose.onNodeWithText("杭州的故事").assertExists()
        compose.onNodeWithText("第一次留下故事 · 2026年9月1日").assertExists()
        compose.onNodeWithText("最近一次回忆 · 2026年10月1日").assertExists()
        compose.onNodeWithText("2 座有故事的城市 · 2 个省级地区 · 3 篇城市记录").assertDoesNotExist()
        compose.onNodeWithText("查看全部足迹").performClick()
        compose.onNodeWithText("全国回忆地图").assertExists()
        compose.onNodeWithTag("city-memory-杭州").assertExists()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("武汉，1篇记录").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithContentDescription("杭州，2篇记录").fetchSemanticsNodes().isNotEmpty()
        }
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa")!!
        directory.mkdirs()
        File(directory, "map-v22-overview.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("city-memory-武汉").performClick()
        compose.onNodeWithText("武汉的故事").assertExists()
        compose.onNodeWithText("江边散步").assertExists()
        File(directory, "map-v22-city.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("随机翻一座城市").performClick()
        compose.onNodeWithText("杭州的故事").assertExists()
    }

    @Test fun overviewRefitsWhenADistantStoryArrivesAsynchronously() {
        val loaded = mutableStateOf(listOf(stories().first()))
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen(loaded.value) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("杭州，1篇记录，已选择").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("查看全部足迹").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("杭州，1篇记录").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { loaded.value = loaded.value + CityStoryUi("far", "far", "乌鲁木齐", "2026年10月4日", "远方来信", "正文", "阿屿", occurredAtEpochMillis = 1000) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("乌鲁木齐，1篇记录").fetchSemanticsNodes().any { it.boundsInRoot.width > 0 }
        }
        compose.onNodeWithContentDescription("乌鲁木齐，1篇记录").assertIsDisplayed()
    }
}
