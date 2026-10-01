package com.twomemory.app.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Everything the new-entry notice remembers: one record id. These tests are the
 * written form of the rule that no count, no unread state and no history of what
 * was shown may exist — including after a switch is turned off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewEntryNoticeStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = NotificationPreferences(context)

    @Test
    fun aFirstPartnerRecordIsLearnedNotAnnounced() {
        assertEquals(NewEntryAction.REMEMBER, NewEntryNotice.decide("entry-1", null))
    }

    @Test
    fun aRecordAlreadySeenProducesNothing() {
        assertEquals(NewEntryAction.NONE, NewEntryNotice.decide("entry-1", "entry-1"))
    }

    @Test
    fun onlyADifferentRecordIsAnnounced() {
        assertEquals(NewEntryAction.ANNOUNCE, NewEntryNotice.decide("entry-2", "entry-1"))
    }

    @Test
    fun bothHooksStartOnAtSundayEight() {
        assertTrue(preferences.weeklyReviewEnabled)
        assertTrue(preferences.newEntryNoticeEnabled)
        assertEquals(DayOfWeek.SUNDAY, preferences.reviewDayOfWeek)
        assertEquals(20, preferences.reviewHour)
    }

    @Test
    fun switchingTheNoticeOffLeavesNoRecordIdBehind() {
        preferences.lastNotifiedEntryId = "entry-1"
        preferences.newEntryNoticeEnabled = false
        assertNull(preferences.lastNotifiedEntryId)
        preferences.newEntryNoticeEnabled = true
        assertNull(preferences.lastNotifiedEntryId)
    }

    @Test
    fun switchingTheReviewOffKeepsTheChosenTimeButNotTheRun() {
        preferences.reviewDayOfWeek = DayOfWeek.WEDNESDAY
        preferences.reviewHour = 9
        preferences.weeklyReviewEnabled = false
        assertFalse(preferences.weeklyReviewEnabled)
        assertEquals(DayOfWeek.WEDNESDAY, preferences.reviewDayOfWeek)
        assertEquals(9, preferences.reviewHour)
    }
}
