package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CapsuleStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val couple = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private fun session(user: UUID = author, origin: String = "https://one.example") =
        SyncSession.Session("token", couple, user, origin)
    private fun card() = CapsuleCard(UUID.randomUUID().toString(), couple.toString(), author.toString(),
        "For later", "secret", "2000-01-01", "SHARED", false, "2026-10-10T00:00:00Z")

    @Test fun realPreferencesIsolateAccountsSpacesAndOriginsAndIgnoreLegacy() {
        context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).edit().putString("capsuleBody", "legacy draft").commit()
        val store = CapsuleCloud.store(context, session())
        store.replace(listOf(card()))
        assertEquals("secret", CapsuleCloud.store(context, session()).cards().single().body)
        assertTrue(CapsuleCloud.store(context, session(partner)).cards().isEmpty())
        assertTrue(CapsuleCloud.store(context, session(origin = "https://two.example")).cards().isEmpty())
        assertTrue(CapsuleCloud.store(context, session().copy(coupleId = UUID.randomUUID())).cards().isEmpty())
        assertEquals("legacy draft", context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).getString("capsuleBody", null))
    }

    @Test fun partnerMetadataNeverStoresBodyAndClockCannotUnlockLetter() {
        val store = CapsuleCloud.store(context, session(partner))
        store.replace(listOf(card().copy(body = null)))
        assertNull(store.cards().single().readableBody(partner.toString()))
        assertFalse(store.preferences.getString("cards", "")!!.contains("secret"))
        assertTrue(store.due(partner.toString()).isEmpty())
        store.replace(listOf(card().copy(unlocked = true)))
        val revealed = store.due(partner.toString()).single()
        assertEquals("secret", revealed.readableBody(partner.toString()))
        store.dismiss(revealed)
        assertTrue(CapsuleCloud.store(context, session(partner)).due(partner.toString()).isEmpty())
        store.replace(emptyList())
        assertTrue(store.cards().isEmpty())
    }

    @Test fun storeRejectsCrossSpacePrivatePartnerAndPrematureBodyInsteadOfCaching() {
        val store = CapsuleCloud.store(context, session(partner))
        for (bad in listOf(card(), card().copy(visibility = "PRIVATE", body = null), card().copy(coupleId = UUID.randomUUID().toString()))) {
            try { store.replace(listOf(bad)); fail("invalid snapshot was cached") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(store.cards().isEmpty())
    }

    @Test fun authoritativeResealingReplacesPreviouslyReadablePartnerBody() {
        val store = CapsuleCloud.store(context, session(partner))
        val letter = card().copy(unlocked = true)
        store.replace(listOf(letter))
        assertEquals("secret", store.cards().single().readableBody(partner.toString()))
        store.replace(listOf(letter.copy(unlocked = false, body = null, unlockDate = "2099-01-01")))
        val restored = CapsuleCloud.store(context, session(partner))
        assertNull(restored.cards().single().readableBody(partner.toString()))
        assertFalse(restored.preferences.getString("cards", "")!!.contains("secret"))
        assertTrue(restored.due(partner.toString()).isEmpty())
    }
}
