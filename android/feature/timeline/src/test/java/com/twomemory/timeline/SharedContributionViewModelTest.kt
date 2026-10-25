package com.twomemory.timeline

import com.twomemory.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharedContributionViewModelTest {
    private val own = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private val entryId = UUID.randomUUID()
    private val source = MutableStateFlow<EntryDetail?>(null)

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun equalNamesDoNotCollapseTwoActualContributors() {
        source.value = detail(listOf(block(0, own), block(1, partner)))
        val vm = viewModel()
        vm.updateNames("小满", "小满")
        val page = requireNotNull(vm.state.value)
        assertEquals(setOf(own, partner), page.contributorIds)
        assertEquals(listOf(own, partner), page.blocks.map { it.authorId })
        assertEquals(listOf(true, false), page.blocks.map { it.mine })
        assertEquals(listOf("小满", "小满"), page.blocks.map { it.author })
    }

    @Test fun oneAuthorWithManyBlocksAndPartnerReplyRemainsOneContributor() {
        val comment = EntryComment(UUID.randomUUID(), entryId, partner, "我的回应", Instant.parse("2026-09-30T13:00:00Z"))
        source.value = detail(listOf(block(0, own), block(1, own))).copy(comments = listOf(comment))
        val page = requireNotNull(viewModel().state.value)
        assertEquals(setOf(own), page.contributorIds)
        assertEquals(1, page.comments.size)
        assertFalse(page.comments.single().mine)
    }

    @Test fun imageOnlyPartnerContributionCountsWithoutPartnerText() {
        source.value = detail(listOf(block(0, own), EntryBlock(UUID.randomUUID(), BlockType.IMAGE, 1,
            assetId = UUID.randomUUID().toString(), authorId = partner)))
        val page = requireNotNull(viewModel().state.value)
        assertEquals(setOf(own, partner), page.contributorIds)
        assertEquals(BlockType.IMAGE, page.blocks.last().type)
        assertEquals(partner, page.blocks.last().authorId)
        assertEquals(null, page.blocks.last().text)
    }

    @Test fun unknownAuthorAndEntryCreatorAreNotInventedBlockContributors() {
        source.value = detail(listOf(block(0, null), block(1, own))).let {
            it.copy(entry = it.entry.copy(authorId = partner))
        }
        val page = requireNotNull(viewModel().state.value)
        assertEquals(setOf(own), page.contributorIds)
        assertEquals(null, page.blocks.first().authorId)
        assertFalse(page.blocks.first().mine)
    }

    @Test fun authorIdsFollowOriginalBlockOrderIncludingReturningAuthor() {
        val first = block(0, own)
        val second = block(1, own)
        val third = block(2, partner)
        val fourth = block(3, own)
        source.value = detail(listOf(fourth, second, third, first))
        val page = requireNotNull(viewModel().state.value)
        assertEquals(listOf(first.id, second.id, third.id, fourth.id).map(UUID::toString), page.blocks.map { it.id })
        assertEquals(listOf(own, own, partner, own), page.blocks.map { it.authorId })
    }

    @Test fun incomingContributionAndRenameKeepDraftTitleAndOccurrenceLabels() {
        source.value = detail(listOf(block(0, own)))
        val vm = viewModel()
        vm.updateDraft("尚未发送的回应")
        val before = requireNotNull(vm.state.value).header
        source.value = requireNotNull(source.value).copy(blocks = listOf(block(0, own), block(1, partner)))
        vm.updateNames("改名后的我", "改名后的TA")
        val after = requireNotNull(vm.state.value)
        assertEquals("尚未发送的回应", after.draft)
        assertEquals(before.title, after.header.title)
        assertEquals(before.dateLabel, after.header.dateLabel)
        assertEquals(before.timeLabel, after.header.timeLabel)
        assertEquals(setOf(own, partner), after.contributorIds)
        assertTrue(after.blocks.first().mine)
    }

    private fun viewModel() = EntryDetailViewModel({ source }, { _, _ -> }, own)
    private fun block(order: Long, author: UUID?) = EntryBlock(UUID.randomUUID(), BlockType.TEXT, order,
        text = "第${order}段", authorId = author)
    private fun detail(blocks: List<EntryBlock>) = EntryDetail(TimelineItem(entryId, UUID.randomUUID(),
        EntryMode.COLLABORATIVE, EntryState.PUBLISHED, Instant.parse("2026-09-30T12:18:00Z"),
        "Asia/Shanghai", "月亮升起来的时候", own), blocks, emptyList())
}
