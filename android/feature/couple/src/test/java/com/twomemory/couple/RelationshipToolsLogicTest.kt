package com.twomemory.couple

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDate

/**
 * Date rules the couple can actually rely on, verified where they run: both
 * helpers are pure, so they execute on the JVM instead of waiting for a device.
 */
class RelationshipToolsLogicTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun aCapsuleOpensOnItsOwnDayAndStaysShutBeforeIt() {
        assertTrue(capsuleIsDue("2026-10-01", today))
        assertTrue(capsuleIsDue("2026-09-30", today))
        assertFalse(capsuleIsDue("2026-10-02", today))
    }

    @Test
    fun anUnreadableUnlockDateKeepsTheCapsuleShut() {
        assertFalse(capsuleIsDue("", today))
        assertFalse(capsuleIsDue("农历八月十五", today))
        assertFalse(capsuleIsDue("2026-13-45", today))
    }

    @Test
    fun onlyAnExplicitSolarAnniversaryGetsACountdown() {
        assertEquals(
            "距离 2026-10-06 还有 5 天",
            anniversaryCountdown("2026-10-06", repeatsYearly = false, today = today),
        )
        assertEquals(null, anniversaryCountdown("农历八月十五", repeatsYearly = true, today = today))
    }
}
