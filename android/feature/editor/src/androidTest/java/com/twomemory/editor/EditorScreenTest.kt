package com.twomemory.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.designsystem.MoonLetterRecordStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class EditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun personalEditorKeepsExactTimeAndToolbarOrderVisible() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(occurrenceTime = Instant.parse("2026-09-30T12:18:00Z")),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                )
            }
        }
        composeRule.onNodeWithText("2026年9月30日 · 12:18").assertIsDisplayed()
        listOf("图片", "视频", "语音", "音乐", "城市", "更多").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("完成").assertIsDisplayed()
        composeRule.onNodeWithText("更多").assertIsDisplayed()
    }

    @Test
    fun personalEditorKeepsLocalSaveStateOutOfWritingSurface() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(recordStatus = MoonLetterRecordStatus.LOCAL_SAVED),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                )
            }
        }
        composeRule.onNodeWithText("已保存到本机").assertDoesNotExist()
    }

    @Test
    fun personalEditorDoesNotExposeAutomaticRetryMechanics() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(recordStatus = MoonLetterRecordStatus.RETRYING),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                )
            }
        }

        composeRule.onNodeWithText("发送没成功，正在自动重试").assertDoesNotExist()
    }

    @Test
    fun personalEditorSignsWithTheRealNameAndNeverInventsOne() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                    ownName = "阿屿屿",
                )
            }
        }
        composeRule.onNodeWithText("阿屿屿").assertIsDisplayed()
        composeRule.onNodeWithText("小满").assertDoesNotExist()
    }

    @Test
    fun anUnnamedWriterFallsBackToPronounsNotToAPlaceholderPerson() {
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                )
            }
        }
        composeRule.onAllNodesWithText("我").onLast().assertIsDisplayed()
    }

    @Test
    fun mediaButtonsUseRealCallbacksInsteadOfExplanationDialogs() {
        var videoPicked = false
        var audioPicked = false
        var music: Triple<String, String, String>? = null
        var city = ""
        composeRule.setContent {
            TwoMemoryTheme {
                EditorAttachmentToolbar(
                    onImageClick = {},
                    onVideoClick = { videoPicked = true },
                    onAudioClick = { audioPicked = true },
                    onMusicAdded = { title, artist, url -> music = Triple(title, artist, url) },
                    onCitySelected = { city = it.name },
                )
            }
        }
        composeRule.onNodeWithContentDescription("添加视频").performClick()
        composeRule.onNodeWithContentDescription("添加语音").performClick()
        composeRule.onNodeWithContentDescription("添加音乐").performClick()
        composeRule.onNodeWithText("歌曲名称").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithContentDescription("添加城市").performClick()
        composeRule.onNodeWithText("在地图上选择城市").assertIsDisplayed()
        composeRule.waitUntil(10000) { composeRule.onAllNodes(hasContentDescription("杭州，", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(hasContentDescription("杭州，", substring = true)).performClick()
        composeRule.onNodeWithText("加入这一页").performClick()
        assertEquals(true, videoPicked)
        assertEquals(true, audioPicked)
        assertEquals("杭州", city)
        assertEquals(null, music)
    }

    @Test
    fun sharedEditorInvitesTheOtherWriterWithoutInventingTheirIdentity() {
        composeRule.setContent {
            TwoMemoryTheme {
                SharedEditorRoute(
                    state = EditorUiState(),
                    onPublish = {},
                    ownName = "小满呀",
                )
            }
        }
        composeRule.onNodeWithText("小满呀").assertIsDisplayed()
        composeRule.onNodeWithText("一段时光，两种心情").assertIsDisplayed()
        composeRule.onNodeWithText("留一处给 TA").assertIsDisplayed()
        composeRule.onNodeWithText("阿屿").assertDoesNotExist()
    }

    @Test
    fun sharedEditorKeepsPartnerPerspectiveReadOnly() {
        composeRule.setContent {
            TwoMemoryTheme {
                SharedEditorRoute(
                    state = EditorUiState(
                        partnerName = "阿屿",
                        partnerBody = "这是 TA 的视角",
                    ),
                    onPublish = {},
                    ownName = "小满呀",
                )
            }
        }
        composeRule.onNodeWithText("阿屿的视角 · 仅 TA 可修改").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("这是 TA 的视角").assertIsDisplayed()
    }
}
