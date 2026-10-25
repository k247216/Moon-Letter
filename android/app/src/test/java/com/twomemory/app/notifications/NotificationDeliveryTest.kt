package com.twomemory.app.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.app.MainActivity
import com.twomemory.app.SyncSession
import com.twomemory.app.TestKeystoreApplication
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryEntity
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Posting acceptance only: these tests cannot establish receipt or reading on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationDeliveryTest {
    private lateinit var app: Application
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        manager = requireNotNull(app.getSystemService(NotificationManager::class.java))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
        manager.cancelAll()
    }

    @Test
    fun deniedRuntimePermissionReturnsFalseWithoutPosting() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val outcome: Any? = MoonLetterNotifier.showNewEntry(app, "entry-denied")

        assertEquals(false, outcome)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun globallyDisabledNotificationsReturnFalseWithoutPosting() {
        shadowOf(manager).setNotificationsEnabled(false)

        val outcome: Any? = MoonLetterNotifier.showNewEntry(app, "entry-disabled")

        assertEquals(false, outcome)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun blockedNewEntryChannelReturnsFalseWithoutPosting() {
        blockChannel("new_entry")

        val outcome: Any? = MoonLetterNotifier.showNewEntry(app, "entry-blocked")

        assertEquals(false, outcome)
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("new_entry").importance)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun acceptedNewEntryReturnsTrueAndKeepsExistingGenericCopy() {
        val outcome: Any? = MoonLetterNotifier.showNewEntry(app, "secret-entry-title-and-body")

        assertEquals(true, outcome)
        val posted = manager.activeNotifications.single()
        assertEquals(2002, posted.id)
        assertEquals("new_entry", posted.notification.channelId)
        assertEquals("TA 写了一条新的", posted.notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("点开看看", posted.notification.extras.getString(Notification.EXTRA_TEXT))
        assertPrivateAndQuiet(posted.notification, "secret-entry-title-and-body")
    }

    @Test
    fun newEntryReplacementUpdatesTheExistingImmutableTapTarget() {
        MoonLetterNotifier.showNewEntry(app, "entry-first")
        val originalTap = manager.activeNotifications.single().notification.contentIntent

        MoonLetterNotifier.showNewEntry(app, "entry-latest")

        val replacement = manager.activeNotifications.single()
        assertEquals(2002, replacement.id)
        assertEquals(originalTap, replacement.notification.contentIntent)
        assertEntryTarget(originalTap, "entry-latest", 2002)
    }

    @Test
    fun weeklyReviewCanPostWithoutABoundSessionAndReplacesOnlyItsOwnId() {
        val first: Any? = MoonLetterNotifier.showWeeklyReview(app, "review-old")
        MoonLetterNotifier.showNewEntry(app, "entry-current")
        val latest: Any? = MoonLetterNotifier.showWeeklyReview(app, "review-latest")

        assertEquals(true, first)
        assertEquals(true, latest)
        assertEquals(setOf(2001, 2002), manager.activeNotifications.map { it.id }.toSet())
        val review = manager.activeNotifications.single { it.id == 2001 }.notification
        assertEquals("weekly_review", review.channelId)
        assertEntryTarget(review.contentIntent, "review-latest", 2001)
        assertPrivateAndQuiet(review, "review-latest")
    }

    @Test
    fun blockedWeeklyChannelDoesNotPreventNewEntryPosting() {
        blockChannel("weekly_review")

        val review: Any? = MoonLetterNotifier.showWeeklyReview(app, "review-blocked")
        val entry: Any? = MoonLetterNotifier.showNewEntry(app, "entry-allowed")

        assertEquals(false, review)
        assertEquals(true, entry)
        assertEquals(2002, manager.activeNotifications.single().id)
    }

    @Test
    fun commentUsesItsOwnChannelAndFixedIdWithPrivateGenericCopy() {
        val outcome = showComment("secret-comment-body")

        assertEquals(true, outcome)
        val posted = manager.activeNotifications.single()
        assertEquals(2003, posted.id)
        assertEquals("partner_replies", posted.notification.channelId)
        assertNotNull(manager.getNotificationChannel("partner_replies"))
        assertFalse(posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank())
        assertFalse(posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank())
        assertPrivateAndQuiet(posted.notification, "secret-comment-body")
        assertEntryTarget(posted.notification.contentIntent, "secret-comment-body", 2003)
    }

    @Test
    fun commentReplacementUpdatesItsTapWithoutReplacingEntryOrReview() {
        MoonLetterNotifier.showWeeklyReview(app, "review")
        MoonLetterNotifier.showNewEntry(app, "entry")
        showComment("comment-first-entry")
        val originalTap = manager.activeNotifications.single { it.id == 2003 }.notification.contentIntent

        assertEquals(true, showComment("comment-latest-entry"))

        assertEquals(setOf(2001, 2002, 2003), manager.activeNotifications.map { it.id }.toSet())
        val replacement = manager.activeNotifications.single { it.id == 2003 }.notification
        assertEquals(originalTap, replacement.contentIntent)
        assertEntryTarget(originalTap, "comment-latest-entry", 2003)
        assertEntryTarget(manager.activeNotifications.single { it.id == 2002 }.notification.contentIntent, "entry", 2002)
        assertEntryTarget(manager.activeNotifications.single { it.id == 2001 }.notification.contentIntent, "review", 2001)
    }

    @Test
    fun blockedReplyChannelReturnsFalseWithoutPosting() {
        blockChannel("partner_replies")

        assertEquals(false, showComment("comment-blocked"))

        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("partner_replies").importance)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun globallyDisabledCommentReturnsFalseWithoutPosting() {
        shadowOf(manager).setNotificationsEnabled(false)

        assertEquals(false, showComment("comment-disabled"))

        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    @Config(application = TestKeystoreApplication::class)
    fun weeklyReviewDoesNotPostAnOldAccountsSelectionUnderANewBinding() = runBlocking {
        val accepted = weeklyReviewDuringQuery {
            SyncSession.save(app, "weekly-account-b", UUID.randomUUID(), UUID.randomUUID())
        }

        assertFalse(accepted)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    @Config(application = TestKeystoreApplication::class)
    fun weeklyReviewRechecksPreferenceAfterSelection() = runBlocking {
        val accepted = weeklyReviewDuringQuery {
            NotificationPreferences(app).weeklyReviewEnabled = false
        }

        assertFalse(accepted)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    @Config(application = TestKeystoreApplication::class)
    fun weeklyReviewPostsTheSelectedEntryWhenItsBindingRemainsCurrent() = runBlocking {
        assertTrue(weeklyReviewDuringQuery {})

        val posted = manager.activeNotifications.single()
        assertEquals(2001, posted.id)
        assertEntryTarget(posted.notification.contentIntent, "weekly-account-a-entry", 2001)
        assertFalse(shadowOf(posted.notification.contentIntent).savedIntent
            .getStringExtra("com.twomemory.app.NOTICE_SCOPE").isNullOrBlank())
    }

    private suspend fun weeklyReviewDuringQuery(duringQuery: () -> Unit): Boolean {
        val coupleId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        SyncSession.save(app, "weekly-account-a", coupleId, userId)
        NotificationPreferences(app).weeklyReviewEnabled = true
        val armed = AtomicBoolean(false)
        val queried = AtomicBoolean(false)
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .setQueryCallback(object : androidx.room.RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                    if (sqlQuery.contains("FROM entries", ignoreCase = true) && armed.compareAndSet(true, false)) {
                        queried.set(true)
                        duringQuery()
                    }
                }
            }, Executor { it.run() })
            .build()
        try {
            database.entryDao().insertEntry(EntryEntity(
                id = "weekly-account-a-entry", coupleId = coupleId.toString(), authorId = userId.toString(),
                mode = "PERSONAL", state = "PUBLISHED",
                occurredAtEpochMillis = System.currentTimeMillis() - 400L * 24 * 60 * 60 * 1000,
                occurredTimezone = "Asia/Shanghai", title = "Private old entry",
            ))
            armed.set(true)
            val accepted = deliverWeeklyReview(app, database.entryDao())
            assertTrue("The race must run during the real Room selection query", queried.get())
            return accepted
        } finally {
            database.close()
        }
    }

    private fun blockChannel(id: String) {
        manager.createNotificationChannel(NotificationChannel(id, "Disabled by user", NotificationManager.IMPORTANCE_NONE))
    }

    private fun assertEntryTarget(tap: PendingIntent, entryId: String, requestCode: Int) {
        val pending = shadowOf(tap)
        assertEquals(requestCode, pending.requestCode)
        assertTrue(pending.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertTrue(pending.flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        val intent = pending.savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(entryId, intent.getStringExtra(MainActivity.EXTRA_OPEN_ENTRY))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    private fun assertPrivateAndQuiet(notice: Notification, secret: String) {
        assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
        assertTrue(notice.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notice.flags and Notification.FLAG_AUTO_CANCEL != 0)
        val publicNotice = notice.publicVersion
        assertNotNull("Lock screen needs an explicit generic public version", publicNotice)
        assertFalse(publicNotice.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank())
        assertFalse(publicNotice.extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank())
        for (version in listOf(notice, publicNotice)) {
            for (key in version.extras.keySet()) {
                assertFalse("Private entry information leaked in $key", version.extras.get(key).toString().contains(secret))
            }
        }
    }

    // Reflect only the not-yet-added API so this test file compiles before production changes.
    // Existing Unit-returning APIs above deliberately use Any? for runtime RED assertions.
    private fun showComment(entryId: String): Any? {
        val method = MoonLetterNotifier::class.java.methods.singleOrNull {
            it.name == "showComment" && it.parameterTypes.contentEquals(arrayOf(Context::class.java, String::class.java))
        }
        assertNotNull("MoonLetterNotifier must expose showComment(Context, String)", method)
        return requireNotNull(method).invoke(MoonLetterNotifier, app, entryId)
    }
}
