package com.twomemory.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** V41: real index and real Compose city screen, using offline synthetic stories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestKeystoreApplication::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CityLatestMemoryTest {
    @get:Rule val compose = createComposeRule()
    private val storyList = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)

    @Test fun unsortedLatestTextStoryMustNotBorrowOlderLocalOrCloudPhoto() {
        val older = story("old-local", "杭州", 100).copy(photoLocalPath = "/synthetic/old.jpg")
        val middle = story("middle-cloud", "杭州", 200).copy(photoAssetId = "synthetic-old-asset")
        val newest = story("new-text", "杭州市", 900)
        val memory = CityMemoryIndex(listOf(middle, newest, older)).cities.single()
        assertEquals("new-text", memory.cover.entryId)
        assertNull(memory.cover.photoLocalPath)
        assertNull(memory.cover.photoAssetId)
        assertEquals(listOf("new-text", "middle-cloud", "old-local"), memory.stories.map { it.entryId })
    }

    @Test fun latestPhotoStoryKeepsItsOwnPhotoAndExistingTimeOrdering() {
        val old = story("old", "武汉", 100).copy(photoLocalPath = "/synthetic/old.jpg")
        val latest = story("latest", "武汉", 900).copy(photoLocalPath = "/synthetic/latest.jpg")
        val middle = story("middle", "武汉", 500)
        val memory = CityMemoryIndex(listOf(old, latest, middle)).cities.single()
        assertEquals("latest", memory.cover.entryId)
        assertEquals("/synthetic/latest.jpg", memory.cover.photoLocalPath)
        assertEquals(listOf("latest", "middle", "old"), memory.stories.map { it.entryId })
        assertEquals("old", memory.first.entryId)
    }

    @Test fun overviewShowsLatestTextTitleInsteadOfOlderPhotoStoryTitle() {
        val old = story("old-photo", "杭州", 100).copy(title = "合成旧照片标题", photoLocalPath = "/synthetic/old.jpg")
        val latest = story("latest-text", "杭州", 900).copy(title = "合成最新文字标题")
        show(listOf(old, latest), "杭州")
        compose.onNodeWithText("查看全部足迹").performClick()
        compose.onNodeWithText("合成最新文字标题").assertIsDisplayed()
        compose.onNodeWithText("合成旧照片标题").assertDoesNotExist()
        compose.onNodeWithContentDescription("杭州的代表照片").assertDoesNotExist()
    }

    @Test fun overviewShowsLatestSnippetWhenTitleIsBlank() {
        val old = story("old-photo", "杭州", 100).copy(title = "合成旧照片标题", photoLocalPath = "/synthetic/old.jpg")
        val latest = story("latest-text", "杭州", 900).copy(title = "  ", preview = "合成最新正文摘要")
        show(listOf(old, latest), "杭州")
        compose.onNodeWithText("查看全部足迹").performClick()
        compose.onNodeWithText("合成最新正文摘要").assertIsDisplayed()
        compose.onNodeWithText("合成旧照片标题").assertDoesNotExist()
    }


    @Test fun randomOtherCityStartsAtItsLatestStory() {
        // Exactly two cities make random selection deterministic without replacing it.
        show(longStories("杭州") + longStories("武汉"), "杭州")
        scrollPastLatest("杭州")
        compose.onNodeWithText("随机翻一座城市").performClick()
        compose.onNodeWithText("武汉的故事").assertIsDisplayed()
        compose.onNodeWithText("武汉-最新故事").assertIsDisplayed()
    }

    @Test fun randomOnlyCityStillResetsToLatestStory() {
        show(longStories("杭州"), "杭州")
        scrollPastLatest("杭州")
        compose.onNodeWithText("随机翻一座城市").performClick()
        compose.onNodeWithText("杭州的故事").assertIsDisplayed()
        compose.onNodeWithText("杭州-最新故事").assertIsDisplayed()
    }


    @Test fun restoringScreenKeepsReaderScrollUntilAnotherExplicitCitySelection() {
        val restoration = StateRestorationTester(compose)
        val stories = longStories("杭州")
        restoration.setContent {
            TwoMemoryTheme {
                CityMapPreviewScreen(stories, lastBrowsedCity = "杭州", memoryScope = "v41-synthetic-restoration")
            }
        }
        scrollPastLatest("杭州")
        // Item zero is the date heading; scroll index 15 starts story offset 14.
        compose.onNodeWithText("杭州-旧故事-14").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("杭州-旧故事-14").assertIsDisplayed()
        compose.onNodeWithText("杭州-最新故事").assertIsNotDisplayed()
        compose.onNodeWithText("随机翻一座城市").performClick()
        compose.onNodeWithText("杭州-最新故事").assertIsDisplayed()
    }

    @Test fun lastBrowsedCityWinsOverTheNationallyNewestStory() {
        show(listOf(story("hangzhou", "杭州", 100), story("wuhan", "武汉", 900)), "杭州市")
        compose.onNodeWithText("杭州的故事").assertIsDisplayed()
        compose.onNodeWithText("hangzhou").assertIsDisplayed()
        compose.onNodeWithText("查看全部足迹").performClick()
        compose.onNodeWithText("全国回忆地图").assertExists()
        compose.onNodeWithTag("city-memory-杭州").assertExists()
    }

    @Test fun nationallyEmptyMapKeepsOverviewAndNoInventedStoryCity() {
        show(emptyList(), null)
        compose.onNodeWithText("全国回忆地图").assertExists()
        compose.onNodeWithText("先选一座城市").assertExists()
        compose.onNodeWithText("随机翻一座城市").assertIsNotEnabled()
        compose.onNodeWithText("杭州的故事").assertDoesNotExist()
    }

    private fun scrollPastLatest(city: String) {
        compose.onNodeWithText("$city-最新故事").assertIsDisplayed()
        compose.onNode(storyList).performScrollToIndex(15)
        compose.onNodeWithText("$city-最新故事").assertIsNotDisplayed()
    }


    private fun show(stories: List<CityStoryUi>, lastCity: String?) {
        compose.setContent {
            TwoMemoryTheme {
                CityMapPreviewScreen(stories, lastBrowsedCity = lastCity, memoryScope = "v41-synthetic-city-test")
            }
        }
    }

    private fun longStories(city: String): List<CityStoryUi> = (0 until 24).map { offset ->
        val title = if (offset == 0) "$city-最新故事" else "$city-旧故事-$offset"
        story("$city-$offset", city, 10_000L - offset).copy(title = title)
    }

    private fun story(id: String, city: String, time: Long) = CityStoryUi(
        id = id, entryId = id, city = city, dateLabel = "2026年10月14日", title = id,
        preview = "合成城市正文，用于验证真实故事列表滚动。", author = "合成作者",
        occurredAtEpochMillis = time,
    )
}
