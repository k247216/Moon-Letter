package com.twomemory.timeline

import androidx.compose.ui.test.*
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun timelineIsContinuousAndShowsBothIdentityMarkers() {
        var opened = ""
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    listOf(
                        TimelineEntryUi("1", "2026年9月30日", "20:18", "我", "此刻", body = "今天的正文"),
                        TimelineEntryUi("2", "2026年9月29日", "22:06", "我们", "一起", shared = true),
                    ),
                    onOpen = { opened = it },
                )
            }
        }
        composeRule.onNodeWithText("换一张").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("首页半屏背景图").assertIsDisplayed()
        composeRule.onNodeWithText("2026年9月30日").assertIsDisplayed()
        composeRule.onNodeWithText("2026年9月29日").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("共同").assertIsDisplayed()
        composeRule.onNodeWithText("今天的正文").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("打开记录").onFirst().performClick()
        assertEquals("1", opened)
    }

    @Test
    fun emptyTimelineOffersARealFirstRecordAction() {
        var created = false
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    entries = emptyList(),
                    onCreate = { created = true },
                )
            }
        }

        composeRule.onNodeWithText("写下第一句").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, created)
    }

    @Test
    fun specialMemoriesUseDistinctLocationAndMusicCards() {
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    entries = listOf(
                        TimelineEntryUi(
                            id = "location",
                            dateLabel = "2026年9月28日",
                            timeLabel = "18:30",
                            author = "我",
                            title = "晚风经过",
                            attachments = listOf(
                                TimelineAttachmentUi(
                                    type = BlockType.LOCATION,
                                    title = "杭州 · 河边",
                                    detail = "把这一刻留在地图上",
                                ),
                            ),
                        ),
                        TimelineEntryUi(
                            id = "music",
                            dateLabel = "2026年9月27日",
                            timeLabel = "22:10",
                            author = "伴侣",
                            title = "今晚想和你分享",
                            attachments = listOf(
                                TimelineAttachmentUi(
                                    type = BlockType.MUSIC,
                                    title = "晚风播放列表",
                                    detail = "一首歌，也是一句没有说完的话",
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("杭州 · 河边").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("城市故事").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("晚风播放列表").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("一起听过").assertIsDisplayed()
    }

    @Test
    fun localDraftExplainsVisibilityWithoutExposingRetryLoop() {
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    entries = listOf(
                        TimelineEntryUi(
                            id = "draft",
                            dateLabel = "2026年9月26日",
                            timeLabel = "09:20",
                            author = "我",
                            title = "只写给自己",
                            unsent = true,
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("未同步").assertDoesNotExist()
        composeRule.onNodeWithText("仅本机可见").assertDoesNotExist()
    }

    @Test
    fun timelineHidesAllSyncStateAndAlternatesJournalBlocks() {
        composeRule.setContent {
            TwoMemoryTheme {
                TimelineScreen(
                    entries = listOf(
                        TimelineEntryUi("left", "2026年9月25日", "09:20", "我", "左边", body = "第一段"),
                        TimelineEntryUi("right", "2026年9月25日", "11:20", "伴侣", "右边", body = "第二段", rejected = true),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("timeline-row-left").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-row-right").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("已保存到本机").assertDoesNotExist()
        composeRule.onNodeWithText("发送未成功 · 点开查看").assertDoesNotExist()
    }
}
