package com.twomemory.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorAttachmentToolbar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android dialog validation; synthetic content only, no server or user storage. */
@RunWith(AndroidJUnit4::class)
class PaperDetailsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val songs = mutableListOf<Triple<String, String, String>>()
    private val url = "https://music.163.com/song?id=186016"

    private fun music(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            TwoMemoryTheme { Column(Modifier.fillMaxSize()) {
                EditorAttachmentToolbar(onMusicAdded = { title, artist, link -> songs += Triple(title, artist, link) })
            } }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.onNodeWithContentDescription("添加音乐").performClick()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label, substring = true))
    private fun edit(label: String, value: String) {
        val action = requireNotNull(field(label).fetchSemanticsNode().config[SemanticsActions.SetText].action)
        compose.runOnIdle { assertTrue(action(AnnotatedString(value))) }
    }

    @Test fun linkFirstDialogAddsOnlySuppliedSong() {
        music()
        assertTrue(field("播放链接").fetchSemanticsNode().boundsInRoot.bottom <=
            field("歌曲名称").fetchSemanticsNode().boundsInRoot.top)
        edit("播放链接", url)
        edit("歌曲名称", "晚风里的歌")
        edit("歌手 / 专辑", "合成歌手")
        compose.onNodeWithText("加入这一页").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("晚风里的歌", "合成歌手", url)), songs) }
    }

    @Test fun openSongAndInputSurviveRecreationWithoutSubmitting() {
        val restoration = StateRestorationTester(compose)
        music(restoration)
        edit("播放链接", url)
        edit("歌曲名称", "晚风里的歌")
        edit("歌手 / 专辑", "合成歌手")
        restoration.emulateSavedInstanceStateRestore()
        field("播放链接").assertTextContains(url)
        field("歌曲名称").assertTextContains("晚风里的歌")
        field("歌手 / 专辑").assertTextContains("合成歌手")
        compose.runOnIdle { assertTrue(songs.isEmpty()) }
        compose.onNodeWithText("加入这一页").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("晚风里的歌", "合成歌手", url)), songs) }
    }

    @Test fun invalidLinksCannotOpenAndTitleOnlyNoteIsKept() {
        music()
        edit("歌手 / 专辑", "合成歌手")
        compose.onNodeWithText("加入这一页").assertIsNotEnabled()
        for (link in listOf("javascript:alert(1)", "file:///song", "https://user:pass@example.com/song")) {
            edit("播放链接", link)
            compose.onNodeWithText("加入这一页").assertIsNotEnabled()
        }
        edit("播放链接", "")
        edit("歌曲名称", "想记住的歌")
        compose.onNodeWithText("加入这一页").performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("想记住的歌", "合成歌手", "")), songs) }
    }

    @Test fun searchingCitiesResetsToLatestStory() {
        val stories = listOf("杭州", "武汉").flatMap { city -> (0 until 24).map { offset ->
            CityStoryUi("$city-$offset", "$city-$offset", city, "2026年10月15日",
                "$city-故事-$offset", "合成正文", "合成作者", occurredAtEpochMillis = 10_000L - offset)
        } }
        compose.setContent { TwoMemoryTheme { CityMapPreviewScreen(stories, lastBrowsedCity = "杭州") } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(15)
        compose.onNodeWithText("杭州-故事-0").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("搜索城市").performClick()
        val input = compose.onNodeWithContentDescription("城市名称或省份")
        val action = requireNotNull(input.fetchSemanticsNode().config[SemanticsActions.SetText].action)
        compose.runOnIdle { assertTrue(action(AnnotatedString("武汉"))) }
        compose.onNode(hasText("武汉") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("武汉-故事-0").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(15)
        compose.onNodeWithText("武汉-故事-0").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("搜索城市").performClick()
        val sameInput = compose.onNodeWithContentDescription("城市名称或省份")
        val sameAction = requireNotNull(sameInput.fetchSemanticsNode().config[SemanticsActions.SetText].action)
        compose.runOnIdle { assertTrue(sameAction(AnnotatedString("武汉"))) }
        compose.onNode(hasText("武汉") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.onNodeWithText("武汉-故事-0").assertIsDisplayed()
    }
}
