package com.twomemory.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OccasionStoreTest {
    @Test fun dismissalSurvivesReopeningButDoesNotHideNextYearsBirthday() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = UUID.randomUUID().toString()
        val store = OccasionStore(context, scope)
        val card = OccasionCard("one", "space", "author", "BIRTHDAY", "生日", "祝福", "2020-05-09", true, null, null, "")
        store.replace(listOf(card))
        assertEquals(1, store.due("partner", LocalDate.of(2026, 5, 9)).size)
        store.dismiss(card, LocalDate.of(2026, 5, 9))
        assertTrue(OccasionStore(context, scope).due("partner", LocalDate.of(2026, 5, 9)).isEmpty())
        assertEquals(1, store.due("partner", LocalDate.of(2027, 5, 9)).size)
        assertTrue(OccasionStore(context, scope + ":other-user").cards().isEmpty())
        assertEquals("祝福", store.cards().single().body)
    }
    @Test fun confirmedDeletionDropsPopupWithoutDeletingOtherImportantDates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OccasionStore(context, UUID.randomUUID().toString())
        val base = OccasionCard("one", "space", "author", "ANNIVERSARY", "相遇", "", "2020-05-09", true, null, null, "")
        store.replace(listOf(base, base.copy(id = "two", title = "生日")))
        store.replace(store.cards().filterNot { it.id == "one" })
        assertEquals("two", store.due("partner", LocalDate.of(2026, 5, 9)).single().id)
    }
}
