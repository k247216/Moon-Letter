package com.twomemory.app

import com.twomemory.app.notifications.CloudKeepsakeReminderRules
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CloudReminderRulesTest {
    @Test fun privateSurpriseHasAnAuthorReminderButNoPartnerReminder() {
        val card = OccasionCard("card", "space", "author", "SURPRISE", "准备礼物", "私密内容", "2026-10-12", false, "partner", null, "", "PRIVATE")
        assertEquals(LocalDate.of(2026, 10, 12), CloudKeepsakeReminderRules.nextDate(card, "author", LocalDate.of(2026, 10, 10)))
        assertNull(CloudKeepsakeReminderRules.nextDate(card, "partner", LocalDate.of(2026, 10, 10)))
    }
    @Test fun annualBirthdayCanBeEditedAndMovesItsReminderDate() {
        val card = OccasionCard("card", "space", "author", "BIRTHDAY", "生日", "", "2000-05-09", true, null, null, "")
        assertEquals(LocalDate.of(2027, 5, 9), CloudKeepsakeReminderRules.nextDate(card, "partner", LocalDate.of(2026, 10, 10)))
        assertEquals(LocalDate.of(2027, 4, 23), CloudKeepsakeReminderRules.nextDate(card.copy(date = "2000-04-23"), "partner", LocalDate.of(2026, 10, 10)))
    }
}
