package com.twomemory.app.notifications

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LocalReminderRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 3, 8, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun yearlyAnniversaryUsesActualNextDayAndConfiguredTime() {
        val plan = LocalReminderRules.anniversary("认识", "2020-10-03", true, "09:15", now, zone)!!
        assertEquals(LocalDateTime.of(2026, 10, 3, 9, 15).atZone(zone).toInstant().toEpochMilli(), plan.atMillis)
    }

    @Test fun elapsedYearlyTimeMovesToNextYear() {
        val plan = LocalReminderRules.anniversary("认识", "2020-10-03", true, "07:00", now, zone)!!
        assertEquals(LocalDateTime.of(2027, 10, 3, 7, 0).atZone(zone).toInstant().toEpochMilli(), plan.atMillis)
    }

    @Test fun leapDayUsesFebruary28InNonLeapYear() {
        val plan = LocalReminderRules.anniversary("认识", "2024-02-29", true, "09:00", now, zone)!!
        assertEquals(LocalDateTime.of(2027, 2, 28, 9, 0).atZone(zone).toInstant().toEpochMilli(), plan.atMillis)
    }

    @Test fun malformedDateAndTimeCannotSchedule() {
        assertNull(LocalReminderRules.anniversary("认识", "2026-02-30", false, "09:00", now, zone))
        assertNull(LocalReminderRules.anniversary("认识", "2026-10-04", false, "25:00", now, zone))
        assertNull(LocalReminderRules.capsule("信", "2026-02-30T09:00", true, now, zone))
    }

    @Test fun pastOneTimeEventDoesNotBecomeAnImmediateReminder() {
        assertNull(LocalReminderRules.anniversary("旅行", "2026-10-01", false, "09:00", now, zone))
    }

    @Test fun nearTermCapsuleUsesMinuteWithoutIncludingLetterBody() {
        val plan = LocalReminderRules.capsule("信", "2026-10-03T08:02", true, now, zone)!!
        assertEquals(now + 120_000, plan.atMillis)
        assertNull(LocalReminderRules.capsule("信", "2026-10-03T08:02", false, now, zone))
    }

    @Test fun legacyDateCapsuleOpensAtLocalMidnight() {
        val plan = LocalReminderRules.capsule("信", "2026-10-04", true, now, zone)!!
        assertEquals(LocalDateTime.of(2026, 10, 4, 0, 0).atZone(zone).toInstant().toEpochMilli(), plan.atMillis)
    }

    @Test fun restartKeepsSameOccurrenceIdentityWhileChangedTimeInvalidatesIt() {
        val first = LocalReminderRules.capsule("信", "2026-10-04T08:00", true, now, zone)!!
        val restart = LocalReminderRules.capsule("信", "2026-10-04T08:00", true, now + 60_000, zone)!!
        val changed = LocalReminderRules.capsule("信", "2026-10-04T08:01", true, now, zone)!!
        assertEquals(first.key, restart.key)
        assertNotEquals(first.key, changed.key)
    }
}
