package com.twomemory.app.notifications

import com.twomemory.app.IndependentAnniversaryDraft
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class IndependentAnniversaryReminderTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun selectedTimeIsUsedInTheSpecifiedDeviceZone() {
        val draft = IndependentAnniversaryDraft(year = 2020, reminderTime = "18:45")
        assertEquals(Instant.parse("2026-09-24T10:45:00Z").toEpochMilli(),
            IndependentAnniversaryReminder.nextAt(draft, Instant.parse("2026-09-23T00:00:00Z").toEpochMilli(), zone))
    }

    @Test fun passedReminderMovesToNextGregorianYearWithoutFabricatingLunarDates() {
        assertEquals(Instant.parse("2027-09-24T01:00:00Z").toEpochMilli(),
            IndependentAnniversaryReminder.nextAt(IndependentAnniversaryDraft(year = 2020),
                Instant.parse("2026-09-24T02:00:00Z").toEpochMilli(), zone))
    }

    @Test fun unknownYearInvalidTimeAndInvalidDateDoNotCreatePlans() {
        val now = Instant.parse("2026-09-23T00:00:00Z").toEpochMilli()
        assertNull(IndependentAnniversaryReminder.nextAt(IndependentAnniversaryDraft(), now, zone))
        assertNull(IndependentAnniversaryReminder.nextAt(IndependentAnniversaryDraft(year = 2020, reminderTime = "25:00"), now, zone))
        assertNull(IndependentAnniversaryReminder.nextAt(IndependentAnniversaryDraft(year = 2020, month = 2, day = 30), now, zone))
    }
}
