package com.twomemory.app.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalReminderStateTest {
    private lateinit var context: Context
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 3, 8, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("moon_letter_local_reminders", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun deliveredOccurrenceIsNotDeliveredAgainAfterRestart() {
        val plan = LocalReminderRules.capsule("信", "2026-10-03T08:02", true, now, zone)!!
        val store = LocalReminderState(context)
        assertTrue(store.shouldDeliver(plan.key))
        store.markDelivered(plan.key)
        assertFalse(LocalReminderState(context).shouldDeliver(plan.key))
    }

    @Test fun changedOccurrenceCanDeliverWithoutClearingExistingLetter() {
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        prefs.edit().putString("capsuleBody", "永远保留原来的信").commit()
        val store = LocalReminderState(context)
        store.markDelivered("capsule:old")
        assertTrue(LocalReminderState(context).shouldDeliver("capsule:new"))
        assertEquals("永远保留原来的信", prefs.getString("capsuleBody", null))
    }

    @Test fun schedulerReadsSavedCapsuleAndInvalidatesChangedOrUnlockedLetter() {
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        prefs.edit().putString("capsuleTitle", "信").putString("capsuleBody", "保留原件")
            .putString("capsuleUnlockDate", "2026-10-04T08:00").putBoolean("capsuleLocked", true).commit()
        val first = LocalRelationshipReminderScheduler.plan(context, "capsule", now)!!
        assertEquals(first.key, LocalRelationshipReminderScheduler.plan(context, "capsule", now + 60_000)!!.key)
        prefs.edit().putString("capsuleUnlockDate", "2026-10-04T08:01").commit()
        assertNotEquals(first.key, LocalRelationshipReminderScheduler.plan(context, "capsule", now)!!.key)
        prefs.edit().putBoolean("capsuleLocked", false).commit()
        assertNull(LocalRelationshipReminderScheduler.plan(context, "capsule", now))
        assertEquals("保留原件", prefs.getString("capsuleBody", null))
    }

    @Test fun schedulerPreservesButNeverUsesUnownedLegacyAnniversarySettings() {
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        prefs.edit().putString("anniversaryName", "旅行").putString("anniversaryDate", "2020-10-03")
            .putBoolean("anniversaryRepeats", false).putString("anniversaryReminderTime", "09:15").commit()
        assertNull(LocalRelationshipReminderScheduler.plan(context, "anniversary", now))
        prefs.edit().putBoolean("anniversaryRepeats", true).commit()
        assertNull(LocalRelationshipReminderScheduler.plan(context, "anniversary", now))
        assertEquals("2020-10-03", prefs.getString("anniversaryDate", null))
        prefs.edit().putString("anniversaryDate", "2026-02-30").commit()
        assertNull(LocalRelationshipReminderScheduler.plan(context, "anniversary", now))
    }

}
