package com.twomemory.timeline

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

@RunWith(AndroidJUnit4::class)
class EntryMemoryPageContractTest {
    @get:Rule val composeRule = createComposeRule()
    private fun page(status: MoonLetterRecordStatus) = EntryDetailUi(
        header = TimelineEntryUi("memory", "2026年10月1日", "22:07", "我", "雨后的散步",
            body = "", shared = true, mine = true),
        blocks = emptyList(), comments = emptyList(), status = status,
    )

    @Test fun publishedPageHidesSyncChipsButDraftAndRejectionStayVisible() {
        val status = mutableStateOf(MoonLetterRecordStatus.PENDING_SYNC)
        composeRule.setContent {
            TwoMemoryTheme { EntryDetailScreen(page(status.value), onBack = {}, onDraftChange = {}, onSend = {}) }
        }
        listOf(MoonLetterRecordStatus.PENDING_SYNC, MoonLetterRecordStatus.SYNCED,
            MoonLetterRecordStatus.LOCAL_SAVED, MoonLetterRecordStatus.UPLOADING_MEDIA,
            MoonLetterRecordStatus.RETRYING).forEach { value ->
            composeRule.runOnIdle { status.value = value }
            composeRule.onNodeWithText(value.label).assertDoesNotExist()
            composeRule.onNodeWithText("共同记录").assertIsDisplayed()
            composeRule.onNodeWithText("写一句回应……").assertIsDisplayed()
        }
        composeRule.runOnIdle { status.value = MoonLetterRecordStatus.DRAFT }
        composeRule.onNodeWithText("草稿").assertIsDisplayed()
        composeRule.runOnIdle { status.value = MoonLetterRecordStatus.REJECTED }
        composeRule.onNodeWithText("服务器拒收了这条").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多操作").assertIsDisplayed()
    }

    @Test fun consecutiveBlocksKeepOneRealAuthorHeadingAndTheirReadingOrder() {
        val state = page(MoonLetterRecordStatus.SYNCED).copy(blocks = listOf(
            EntryBlockUi("first", BlockType.TEXT, "第一段", null, null, author = "小满"),
            EntryBlockUi("second", BlockType.TEXT, "第二段", null, null, author = "小满"),
            EntryBlockUi("third", BlockType.TEXT, "TA 的补写", null, null, author = "阿屿"),
        ))
        composeRule.setContent {
            TwoMemoryTheme { EntryDetailScreen(state, onBack = {}, onDraftChange = {}, onSend = {}) }
        }
        composeRule.onAllNodesWithText("小满的视角").assertCountEquals(1)
        val first = composeRule.onNodeWithText("第一段").fetchSemanticsNode()
        val second = composeRule.onNodeWithText("第二段").fetchSemanticsNode()
        assertEquals(true, first.boundsInRoot.top < second.boundsInRoot.top)
        composeRule.onNodeWithText("阿屿的视角").performScrollTo().assertIsDisplayed()
    }

    @Test fun privateDraftHasNeitherSendNorAppendAction() {
        var append = 0
        composeRule.setContent {
            TwoMemoryTheme { EntryDetailScreen(page(MoonLetterRecordStatus.DRAFT),
                onBack = {}, onDraftChange = {}, onSend = {}, onAddPerspective = { append++ }) }
        }
        composeRule.onNodeWithText("发送").assertDoesNotExist()
        composeRule.onNodeWithText("补写我的视角").assertDoesNotExist()
        composeRule.onNodeWithText("还没有回应，说一句给 TA 听的话。").assertDoesNotExist()
        assertEquals(0, append)
    }

    @Test fun pendingPublishOverridesDatabaseDraftForReplyComposer() {
        composeRule.setContent {
            TwoMemoryTheme { EntryDetailScreen(page(MoonLetterRecordStatus.DRAFT),
                onBack = {}, onDraftChange = {}, onSend = {}, delivery = MoonLetterRecordStatus.PENDING_SYNC) }
        }
        composeRule.onNodeWithText("写一句回应……").assertIsDisplayed()
        composeRule.onNodeWithText("这条记录目前只在这台手机上，不会出现在伴侣的时间轴。").assertDoesNotExist()
    }

    @Test fun privateDraftExplainsUnpublishedStateWithoutPermanentLocalClaim() {
        composeRule.setContent {
            TwoMemoryTheme { EntryDetailScreen(page(MoonLetterRecordStatus.DRAFT),
                onBack = {}, onDraftChange = {}, onSend = {}) }
        }
        composeRule.onNodeWithText("尚未发布", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("写一句回应……").assertDoesNotExist()
    }

    @Test fun musicDisplaysKnownSongMetadataInsteadOfBareLink() {
        val state = page(MoonLetterRecordStatus.SYNCED).copy(blocks = listOf(
            EntryBlockUi("song", BlockType.MUSIC, null, null, null,
                payload = "{\"title\":\"晴天\",\"artist\":\"周杰伦\",\"url\":\"https://music.163.com/song?id=186016\"}"),
        ))
        composeRule.setContent {
            TwoMemoryTheme { EntryDetailScreen(state, onBack = {}, onDraftChange = {}, onSend = {}) }
        }
        composeRule.onNodeWithText("晴天").assertIsDisplayed()
        composeRule.onNodeWithText("周杰伦").assertIsDisplayed()
        capture("entry-detail-music-v9.png")
    }

    private fun capture(name: String) {
        val directory = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa"))
        File(directory, name).outputStream().use {
            assertEquals(true, composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
