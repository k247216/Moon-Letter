package com.twomemory.timeline

import com.twomemory.model.BlockType
import com.twomemory.model.EntryBlock
import com.twomemory.model.EntryComment
import com.twomemory.model.EntryDetail
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    @Test fun durableReplyClearsComposerWhileNetworkIsStillBusy() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val releaseNetwork = CompletableDeferred<Unit>()
        var stored = 0
        var syncing = false
        val vm = EntryDetailViewModel({ source }, { _, _ -> stored++ }, me,
            syncComment = { syncing = true; releaseNetwork.await() })
        runCurrent()
        vm.updateDraft("今晚的晚风")
        vm.sendComment()
        runCurrent()
        assertEquals(1, stored)
        assertTrue(syncing)
        assertEquals(false, vm.state.value?.sending)
        assertEquals("", vm.state.value?.draft)
        vm.updateDraft("下一句")
        releaseNetwork.complete(Unit)
        runCurrent()
        assertEquals("下一句", vm.state.value?.draft)
    }

    @Test fun networkFailureDoesNotClaimAnAlreadyStoredReplyWasLost() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = EntryDetailViewModel({ source }, { _, _ -> }, me,
            syncComment = { throw IOException("网络暂时不可达") })
        runCurrent()
        vm.updateDraft("已经写进本机的回应")
        vm.sendComment()
        runCurrent()
        assertEquals("", vm.state.value?.draft)
        assertEquals(false, vm.state.value?.sending)
        assertEquals(null, vm.state.value?.error)
    }

    @Test fun scheduledSendNeverRestoresAnOlderDraftOrSendsTwice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<String>()
        val vm = viewModel { _, body -> seen += body; release.await() }
        runCurrent()
        vm.updateDraft("第一句")
        vm.sendComment()
        vm.updateDraft("第二句")
        vm.sendComment()
        runCurrent()
        assertEquals(listOf("第一句"), seen)
        assertEquals("第二句", vm.state.value?.draft)
        release.complete(Unit)
        runCurrent()
        assertEquals("第二句", vm.state.value?.draft)
        assertEquals(false, vm.state.value?.sending)
    }

    @Test fun failureFromPreviousRecordDoesNotChangeTheNewPage() {
        val release = CompletableDeferred<Unit>()
        val vm = viewModel { _, _ -> release.await(); throw IOException("旧页面失败") }
        vm.updateDraft("旧页面回应")
        vm.sendComment()
        source.value = detail().let { it.copy(entry = it.entry.copy(id = UUID.randomUUID())) }
        vm.updateDraft("新页面输入")
        release.complete(Unit)
        assertEquals("新页面输入", vm.state.value?.draft)
        assertEquals(null, vm.state.value?.error)
    }

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

    @Test
    fun privateDraftDoesNotSendCommentsToStorage() {
        source.value = detail().let { it.copy(entry = it.entry.copy(state = EntryState.DRAFT)) }
        var sends = 0
        val viewModel = viewModel { _, _ -> sends++ }
        viewModel.updateDraft("先留着")
        viewModel.sendComment()
        assertEquals(0, sends)
        assertEquals("先留着", viewModel.state.value?.draft)
    }

    @Test
    fun successfulSendDoesNotEraseANewerReplyTypedDuringRefresh() {
        val sent = CompletableDeferred<Unit>()
        val viewModel = viewModel { _, _ -> sent.await() }
        viewModel.updateDraft("第一句")
        viewModel.sendComment()
        source.value = detail(comment("收到", partner))
        viewModel.updateDraft("第二句还没发送")
        sent.complete(Unit)
        assertEquals("第二句还没发送", viewModel.state.value?.draft)
        assertEquals(false, viewModel.state.value?.sending)
    }

    @Test
    fun missingCommentAuthorNeverClaimsThePartnerWroteIt() {
        source.value = detail(comment("旧回应", partner).copy(authorId = null))
        val viewModel = viewModel()
        viewModel.updateNames("小满", "阿屿")
        assertEquals("成员", viewModel.state.value?.comments?.single()?.author)
    }

    @Test
    fun observedPendingPublishAllowsReplyEvenBeforeDatabaseStateChanges() {
        source.value = detail().let { it.copy(entry = it.entry.copy(state = EntryState.DRAFT)) }
        val seen = mutableListOf<Pair<String, String>>()
        val viewModel = viewModel { id, body -> seen += id to body }
        viewModel.updateDelivery(com.twomemory.designsystem.MoonLetterRecordStatus.PENDING_SYNC)
        viewModel.updateDraft("离线也先记下回应")
        viewModel.sendComment()
        assertEquals(listOf(entryId.toString() to "离线也先记下回应"), seen)
    }

    @Test
    fun queueRefreshPreservesUnsentReplyAndRealBlockAuthors() {
        source.value = detail().copy(blocks = listOf(
            EntryBlock(UUID.randomUUID(), BlockType.TEXT, 0L, text = "她的视角", authorId = partner),
            EntryBlock(UUID.randomUUID(), BlockType.TEXT, 1L, text = "我的视角", authorId = me),
            EntryBlock(UUID.randomUUID(), BlockType.TEXT, 2L, text = "旧内容"),
        ))
        val viewModel = viewModel()
        viewModel.updateNames("小满", "阿屿")
        viewModel.updateDraft("还没写完")
        viewModel.updateDelivery(com.twomemory.designsystem.MoonLetterRecordStatus.UPLOADING_MEDIA)
        assertEquals("还没写完", viewModel.state.value?.draft)
        assertEquals(listOf("阿屿", "小满", null), viewModel.state.value?.blocks?.map { it.author })
    }
}
