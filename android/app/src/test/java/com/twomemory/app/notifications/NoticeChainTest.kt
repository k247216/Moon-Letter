package com.twomemory.app.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.app.SyncSession
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryDao
import com.twomemory.database.EntryEntity
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The notice chain as far as the JVM can prove it: a real Room archive, the real
 * session record, real SharedPreferences and the notifications the platform would
 * show. Only the tap target needs a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoticeChainTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var entries: EntryDao
    private lateinit var preferences: NotificationPreferences

    private val coupleId = UUID.randomUUID()
    private val ownId = UUID.randomUUID()
    private val partnerId = UUID.randomUUID()
    private val dayMillis = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        context = app
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        entries = database.entryDao()
        preferences = NotificationPreferences(context)
        SyncSession.save(context, "token", coupleId, ownId)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFirstPartnerRecordIsLearnedNotAnnounced() = runBlocking {
        publish("a", partnerId, ago(3))
        NewEntryNotice.announceIfNeeded(context, entries)
        assertEquals(0, posted().size)
        assertEquals("a", preferences.lastNotifiedEntryId)
    }

    @Test
    fun aNewerPartnerRecordIsAnnouncedOnceAndNeverAgain() = runBlocking {
        publish("a", partnerId, ago(3))
        NewEntryNotice.announceIfNeeded(context, entries)
        publish("b", partnerId, ago(1))
        NewEntryNotice.announceIfNeeded(context, entries)
        val notices = posted()
        assertEquals(1, notices.size)
        assertEquals("TA 写了一条新的", titleOf(notices[0]))
        assertEquals("b", preferences.lastNotifiedEntryId)
        NewEntryNotice.announceIfNeeded(context, entries)
        assertEquals(1, posted().size)
    }

    @Test
    fun aBackdatedRecordAnnouncesWhenItIsTheOneThatJustArrived() = runBlocking {
        publish("yesterday", partnerId, ago(1))
        NewEntryNotice.announceIfNeeded(context, entries)
        // Writing about last weekend is ordinary: an older date must not make the
        // record invisible to the notice.
        publish("last-weekend", partnerId, ago(9))
        NewEntryNotice.announceIfNeeded(context, entries)
        assertEquals(1, posted().size)
        assertEquals("last-weekend", preferences.lastNotifiedEntryId)
    }

    @Test
    fun nothingIsAnnouncedAboutMyOwnRecordOrADraft() = runBlocking {
        publish("mine", ownId, ago(1))
        publish("theirs-draft", partnerId, ago(1), state = "DRAFT")
        NewEntryNotice.announceIfNeeded(context, entries)
        assertEquals(0, posted().size)
        assertNull(preferences.lastNotifiedEntryId)
    }

    @Test
    fun aSwitchedOffNoticeStaysSilentAndRemembersNothing() = runBlocking {
        publish("a", partnerId, ago(3))
        NewEntryNotice.announceIfNeeded(context, entries)
        preferences.newEntryNoticeEnabled = false
        publish("b", partnerId, ago(1))
        NewEntryNotice.announceIfNeeded(context, entries)
        assertEquals(0, posted().size)
        assertNull(preferences.lastNotifiedEntryId)
    }

    @Test
    fun aSwitchedOffReviewPicksNothingEvenFromAnOldArchive() = runBlocking {
        publish("old", ownId, ago(400))
        preferences.weeklyReviewEnabled = false
        assertNull(pickReviewEntryId(context, entries))
    }

    @Test
    fun theWeeklyReviewStaysQuietUntilASharedRecordIsOldEnough() = runBlocking {
        publish("recent", ownId, ago(2), state = "PUBLISHED")
        assertNull(pickReviewEntryId(context, entries))
        publish("older", ownId, ago(40), state = "PUBLISHED")
        assertEquals("older", pickReviewEntryId(context, entries))
        publish("almost-a-year", ownId, ago(358), state = "PUBLISHED")
        assertEquals("almost-a-year", pickReviewEntryId(context, entries))
    }

    private fun posted(): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun titleOf(notification: Notification): String? =
        notification.extras.getString(Notification.EXTRA_TITLE)

    private fun ago(days: Long) = System.currentTimeMillis() - days * dayMillis

    private suspend fun publish(
        entryId: String,
        authorId: UUID,
        occurredAtEpochMillis: Long,
        state: String = "PUBLISHED",
    ) {
        entries.insertEntry(
            EntryEntity(
                id = entryId,
                coupleId = coupleId.toString(),
                authorId = authorId.toString(),
                mode = "PERSONAL",
                state = state,
                occurredAtEpochMillis = occurredAtEpochMillis,
                occurredTimezone = "Asia/Shanghai",
                title = "一条记录",
            ),
        )
    }
}
