package com.twomemory.timeline

import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import com.twomemory.model.TimelinePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicReference

/**
 * JVM-level evidence for the timeline contract: one local write is enough to
 * change the screen, and a rename re-labels what is already on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val me = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private val source = MutableStateFlow<List<TimelineItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TimelineViewModel({ source }, currentUserId = me, preparationDispatcher = UnconfinedTestDispatcher()).apply {
        updateNames(own = "小满呀", partner = "阿屿")
    }

    @Test
    fun preparingRecordDatesDoesNotReadTheArchiveOnTheUiThread() = runBlocking {
        val uiThread = Thread.currentThread()
        val preparingThread = AtomicReference<Thread>()
        val record = item(EntryState.PUBLISHED, me)
        val archive = object : AbstractList<TimelineItem>() {
            override val size = 1
            override fun get(index: Int): TimelineItem {
                preparingThread.set(Thread.currentThread())
                return record
            }
        }
        val model = TimelineViewModel({ kotlinx.coroutines.flow.flowOf(archive) }, currentUserId = me)
        val shown = withTimeout(5_000) { model.entries.first { it.isNotEmpty() } }
        assertEquals("2026年9月30日", shown.single().dateLabel)
        assertTrue(preparingThread.get() !== uiThread, "Archive preparation must leave the UI thread free")
    }

    private fun item(
        state: EntryState,
        authorId: UUID,
        mode: EntryMode = EntryMode.PERSONAL,
        photo: TimelinePhoto? = null,
    ) = TimelineItem(
        id = UUID.randomUUID(),
        coupleId = UUID.randomUUID(),
        mode = mode,
        state = state,
        occurredAt = Instant.parse("2026-09-30T12:18:00Z"),
        occurredTimezone = "Asia/Shanghai",
        title = "秋天的第一场雨",
        authorId = authorId,
        preview = "你那边也下雨了吗",
        photo = photo,
    )

    @Test
    fun unreadUsesRecordIdentityNotItsOldOccurrenceDateAndNeverMarksOwnDrafts() {
        val model = viewModel()
        val other = item(EntryState.PUBLISHED, partner)
        val own = item(EntryState.PUBLISHED, me)
        val draft = item(EntryState.DRAFT, partner)
        source.value = listOf(other, own, draft)
        assertEquals(listOf(true, false, false), model.entries.value.map { it.unread })
        model.updateReadIds(setOf(other.id.toString()))
        assertFalse(model.entries.value.first().unread)
        source.value = listOf(item(EntryState.PUBLISHED, partner))
        assertTrue(model.entries.value.single().unread)
    }

    @Test
    fun aSingleLocalWriteReachesTheScreenWithNoRefreshCall() {
        val viewModel = viewModel()
        assertEquals(emptyList<TimelineEntryUi>(), viewModel.entries.value)

        source.value = listOf(item(EntryState.PUBLISHED, me))

        val shown = viewModel.entries.value.single()
        assertEquals("2026年9月30日", shown.dateLabel)
        assertEquals("20:18", shown.timeLabel)
        assertEquals("你那边也下雨了吗", shown.body)
        assertEquals("小满呀", shown.author)
        assertTrue(shown.mine)
        assertFalse(shown.unsent)
        assertFalse(shown.shared)
    }

    @Test
    fun aRecordStillOnlyOnThisPhoneIsMarkedUnsent() {
        val viewModel = viewModel()
        source.value = listOf(item(EntryState.DRAFT, me))
        assertTrue(viewModel.entries.value.single().unsent)
    }

    @Test
    fun aRefusedRecordIsToldApartFromOneStillWaitingItsTurn() {
        val viewModel = viewModel()
        val stalled = item(EntryState.DRAFT, me)
        val waiting = item(EntryState.DRAFT, me)
        source.value = listOf(stalled, waiting)

        viewModel.updateRejectedIds(setOf(stalled.id.toString()))

        val shown = viewModel.entries.value
        assertTrue(shown.first().rejected)
        assertTrue(shown.first().unsent)
        assertFalse(shown.last().rejected)
        assertTrue(shown.last().unsent)
    }

    @Test
    fun aPartnersRecordIsNamedAndBadgedByWhereItCameFrom() {
        val viewModel = viewModel()
        source.value = listOf(
            item(EntryState.PUBLISHED, partner, mode = EntryMode.COLLABORATIVE),
        )

        val shown = viewModel.entries.value.single()
        assertEquals("阿屿", shown.author)
        assertTrue(shown.shared)
        assertFalse(shown.mine)
    }

    @Test
    fun renamingRelabelsWhatIsAlreadyOnScreen() {
        val viewModel = viewModel()
        source.value = listOf(item(EntryState.PUBLISHED, me), item(EntryState.PUBLISHED, partner))
        assertEquals(listOf("小满呀", "阿屿"), viewModel.entries.value.map { it.author })

        viewModel.updateNames(own = "小满", partner = "阿屿屿")

        assertEquals(listOf("小满", "阿屿屿"), viewModel.entries.value.map { it.author })
    }

    @Test
    fun missingOtherProfileUsesNeutralLabelWithoutInventingTheirIdentity() {
        val viewModel = TimelineViewModel({ source }, currentUserId = me, preparationDispatcher = UnconfinedTestDispatcher())
        source.value = listOf(item(EntryState.PUBLISHED, me), item(EntryState.PUBLISHED, partner))

        assertEquals(listOf("我", "对方"), viewModel.entries.value.map { it.author })
    }

    @Test
    fun aPictureIsCarriedThroughAsAnAddressNotAsAPlaceholder() {
        val viewModel = viewModel()
        val photo = TimelinePhoto(localPath = "/data/user/0/com.twomemory.app/files/photos/a.png", assetId = "7")
        source.value = listOf(item(EntryState.PUBLISHED, me, photo = photo))

        assertEquals(photo, viewModel.entries.value.single().photo)
    }
}
