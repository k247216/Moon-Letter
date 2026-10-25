package com.twomemory.timeline

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun detailShowsFullTextMediaStatusAndBackAction() {
        var wentBack = false
        val header = TimelineEntryUi("entry-1", "2026年10月1日", "22:07", "小满", "今天", body = "完整的正文应该可以一直读到结尾", shared = true, mine = true)
        val state = EntryDetailUi(
            header = header,
            blocks = listOf(
                EntryBlockUi("text-1", BlockType.TEXT, "完整的正文应该可以一直读到结尾", null, null, "小满"),
                EntryBlockUi("image-1", BlockType.IMAGE, null, "/tmp/photo.jpg", null, "阿屿"),
            ),
            comments = emptyList(),
            status = MoonLetterRecordStatus.PENDING_SYNC,
        )
        composeRule.setContent {
            TwoMemoryTheme {
                EntryDetailScreen(state, onBack = { wentBack = true }, onDraftChange = {}, onSend = {})
            }
        }
        composeRule.onNodeWithText("完整的正文应该可以一直读到结尾").assertIsDisplayed()
        composeRule.onNodeWithText("小满").assertIsDisplayed()
        composeRule.onNodeWithText("已保存到本机 · 等待确认").assertDoesNotExist()
        composeRule.onNodeWithText("阿屿的视角").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回时间线").performClick()
        assertEquals(true, wentBack)
    }

    @Test
    fun missingRecordExplainsThisPhoneHasNotReadItYet() {
        composeRule.setContent {
            TwoMemoryTheme {
                EntryDetailScreen(null, onBack = {}, onDraftChange = {}, onSend = {})
            }
        }
        composeRule.onNodeWithText("正在打开…这台手机还没有读到这条记录").assertIsDisplayed()
    }

    @Test
    fun detailRendersCitySnapshotInsteadOfHidingItBehindAPlaceholder() {
        val state = EntryDetailUi(
            header = TimelineEntryUi("entry-city", "2026年10月1日", "22:07", "小满", "杭州", body = "", shared = false, mine = true),
            blocks = listOf(
                EntryBlockUi(
                    id = "city-1",
                    type = BlockType.LOCATION,
                    text = null,
                    localPath = null,
                    assetId = null,
                    author = "小满",
                    payload = "{\"city\":\"杭州\"}",
                ),
            ),
            comments = emptyList(),
        )
        composeRule.setContent {
            TwoMemoryTheme {
                EntryDetailScreen(state, onBack = {}, onDraftChange = {}, onSend = {})
            }
        }
        composeRule.onNodeWithText("📍 杭州").assertIsDisplayed()
    }

    @Test
    fun detailOpensPhotoViewerAndMovesAcrossPhotos() {
        val state = EntryDetailUi(
            header = TimelineEntryUi("entry-photo", "2026年10月1日", "22:07", "小满", "照片", body = "", shared = false, mine = true),
            blocks = listOf(
                EntryBlockUi("image-1", BlockType.IMAGE, null, "/tmp/photo-1.jpg", null, "小满"),
                EntryBlockUi("image-2", BlockType.IMAGE, null, "/tmp/photo-2.jpg", null, "小满"),
            ),
            comments = emptyList(),
        )
        composeRule.setContent {
            TwoMemoryTheme {
                EntryDetailScreen(state, onBack = {}, onDraftChange = {}, onSend = {})
            }
        }

        composeRule.onAllNodesWithContentDescription("查看第1张图片")[0].performClick()
        composeRule.onNodeWithText("图片 1 / 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("下一张图片").performClick()
        composeRule.onNodeWithText("图片 2 / 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭图片浏览").performClick()
        composeRule.onNodeWithContentDescription("关闭图片浏览").assertDoesNotExist()
    }

    @Test
    fun rejectedDeliveryKeepsResendInsideMoreActions() {
        val state = EntryDetailUi(
            header = TimelineEntryUi("entry-rejected", "2026年10月1日", "22:07", "小满", "被拒收", body = "", shared = false, mine = true),
            blocks = emptyList(),
            comments = emptyList(),
            status = MoonLetterRecordStatus.REJECTED,
        )
        composeRule.setContent {
            TwoMemoryTheme {
                EntryDetailScreen(state, onBack = {}, onDraftChange = {}, onSend = {})
            }
        }

        composeRule.onNodeWithText("重新发送").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("更多操作").performClick()
        composeRule.onNodeWithText("重新发送").assertIsDisplayed()
    }
}
