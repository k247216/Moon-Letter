package com.twomemory.couple

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCapsuleRulesTest {
    @Test fun datedCapsuleOpensOnItsActualDay() {
        assertTrue(capsuleIsDue("2026-10-03", LocalDate.of(2026, 10, 3)))
        assertFalse(capsuleIsDue("2026-10-04", LocalDate.of(2026, 10, 3)))
    }

    @Test fun malformedDatesNeverOpen() {
        listOf("2026-02-30", "2026-13-03", "", "tomorrow").forEach {
            assertFalse(capsuleIsDue(it, LocalDate.of(2026, 10, 3)))
        }
    }

    // Removing date+time support must prevent an already elapsed local test
    // capsule from opening. The past year makes this independent of test time.
    @Test fun elapsedDateAndTimeCapsuleOpens() {
        assertTrue(capsuleIsDue("2020-01-01T12:30"))
    }

    @Test fun futureDateAndTimeCapsuleStaysClosed() {
        assertFalse(capsuleIsDue("2099-01-01T12:30"))
    }
}
