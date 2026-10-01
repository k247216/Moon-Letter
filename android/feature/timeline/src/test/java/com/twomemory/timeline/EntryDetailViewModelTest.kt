package com.twomemory.timeline

import com.twomemory.model.BlockType
import com.twomemory.model.EntryBlock
import com.twomemory.model.EntryComment
import com.twomemory.model.EntryDetail
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-level evidence for an open record: a reply that arrives later lands on
 * the page she is already looking at, and never costs her the words she is
 * typing at that moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest {

    private val me = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private val entryId = UUID.randomUUID()
    private val source = MutableStateFlow<EntryDetail?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        source.value = detail()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun detail(vararg comments: EntryComment) = EntryDetail(
        entry = TimelineItem(
            id = entryId,
            coupleId = UUID.randomUUID(),
            mode = EntryMode.COLLABORATIVE,
            state = EntryState.PUBLISHED,
            occurredAt = Instant.parse("2026-09-30T12:18:00Z"),
            occurredTimezone = "Asia/Shanghai",
            title = "雨后的阳台",
            authorId = partner,
            preview = "花开了",
        ),
        blocks = listOf(
            EntryBlock(UUID.randomUUID(), BlockType.TEXT, 0L, text = "花开了"),
        ),
        comments = comments.toList(),
    )

    private fun comment(body: String, authorId: UUID) = EntryComment(
        id = UUID.randomUUID(),
        entryId = entryId,
        authorId = authorId,
        body = body,
        createdAt = Instant.parse("2026-09-30T13:00:00Z"),
    )

    private fun viewModel(onSend: suspend (String, String) -> Unit = { _, _ -> }) =
        EntryDetailViewModel({ source }, onSend, currentUserId = me)

    @Test
    fun aReplyThatLandsLaterReachesThePageSheIsAlreadyOn() {
        val viewModel = viewModel()
        assertEquals(emptyList<EntryCommentUi>(), viewModel.state.value?.comments)

        source.value = detail(comment("我也看到了", partner))

        assertEquals(listOf("我也看到了"), viewModel.state.value?.comments?.map { it.body })
    }

    @Test
    fun anIncomingReplyNeverEatsWhatSheIsTyping() {
        val viewModel = viewModel()
        viewModel.updateDraft("我想说")

        source.value = detail(comment("我也看到了", partner))

        assertEquals("我想说", viewModel.state.value?.draft)
        assertEquals(1, viewModel.state.value?.comments?.size)
    }

    @Test
    fun oneTapSendsExactlyOneTrimmedReplyUnderTheRecordItBelongsTo() {
        val seen = mutableListOf<Pair<String, String>>()
        val viewModel = viewModel { id, body -> seen += id to body }
        viewModel.updateDraft("  写得真好  ")

        viewModel.sendComment()

        assertEquals(listOf(entryId.toString() to "写得真好"), seen)
        assertEquals("", viewModel.state.value?.draft)
    }

    @Test
    fun anEmptyReplyNeverReachesStorage() {
        var sends = 0
        val viewModel = viewModel { _, _ -> sends++ }
        viewModel.updateDraft("   ")

        viewModel.sendComment()

        assertEquals(0, sends)
    }

    @Test
    fun aFailedSendKeepsHerWordsAndSaysWhy() {
        val viewModel = viewModel { _, _ -> throw IOException("设备离线") }
        viewModel.updateDraft("写得真好")

        viewModel.sendComment()

        assertEquals("写得真好", viewModel.state.value?.draft)
        assertTrue(viewModel.state.value?.error?.contains("设备离线") == true)
    }

    @Test
    fun renamingRelabelsTheOpenPageIncludingItsReplies() {
        source.value = detail(comment("我也看到了", me), comment("我也看到了", partner))
        val viewModel = viewModel()

        viewModel.updateNames(own = "小满呀", partner = "阿屿")

        assertEquals(listOf("小满呀", "阿屿"), viewModel.state.value?.comments?.map { it.author })
        assertEquals("阿屿", viewModel.state.value?.header?.author)
    }
}
