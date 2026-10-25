package com.twomemory.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class OccasionRulesTest {
    private fun card(kind: String = "BIRTHDAY", date: String = "2020-05-09", yearly: Boolean = true) =
        OccasionCard("card", "space", "author", kind, "生日", "", date, yearly, null, null, "2026-10-04T00:00:00Z")

    @Test fun birthdayUsesMonthAndDayAndNeverTriggersOnTheWrongDate() {
        assertTrue(card().isDue(LocalDate.of(2027, 5, 9)))
        assertFalse(card().isDue(LocalDate.of(2027, 5, 8)))
        assertFalse(card().isDue(LocalDate.of(2019, 5, 9)))
    }
    @Test fun oneOffSurpriseDoesNotRepeatOrTurnIntoAnOverduePopup() {
        val value = card("SURPRISE", "2026-10-04", false)
        assertTrue(value.isDue(LocalDate.of(2026, 10, 4)))
        assertFalse(value.isDue(LocalDate.of(2026, 10, 5)))
        assertFalse(value.isDue(LocalDate.of(2027, 10, 4)))
    }
    @Test fun leapBirthdayUsesFebruary28InNonLeapYears() {
        assertTrue(card(date = "2024-02-29").isDue(LocalDate.of(2027, 2, 28)))
        assertTrue(card(date = "2024-02-29").isDue(LocalDate.of(2028, 2, 29)))
        assertFalse(card(date = "bad date").isDue(LocalDate.of(2028, 2, 29)))
    }
    @Test fun surpriseOnlyOpensForItsRecipient() {
        val value = card("SURPRISE").copy(recipientId = "partner")
        assertTrue(value.opensFor("partner"))
        assertFalse(value.opensFor("author"))
        assertFalse(value.opensFor("stranger"))
    }
}
