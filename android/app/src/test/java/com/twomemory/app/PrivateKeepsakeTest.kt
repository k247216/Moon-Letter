package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryVisibility
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivateKeepsakeTest {
    @Test fun privateDraftRemainsPrivateAfterReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = DraftLibrary(context, UUID.randomUUID().toString())
        val id = UUID.randomUUID().toString()
        library.save(id, EntryMode.PERSONAL, DraftStore.Draft("给未来", "先收在心里", visibility = EntryVisibility.PRIVATE))
        assertEquals(EntryVisibility.PRIVATE, library.load(id)!!.draft.visibility)
    }

    @Test fun privateSurpriseStillRemindsItsAuthorButNeverItsRecipient() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OccasionStore(context, UUID.randomUUID().toString())
        val json = JSONObject().put("id", "card").put("coupleId", "space").put("authorId", "author")
            .put("kind", "SURPRISE").put("title", "准备惊喜").put("body", "秘密计划")
            .put("date", "2026-10-10").put("repeatsYearly", false).put("recipientId", "partner")
            .put("updatedAt", "2026-10-10T00:00:00Z").put("visibility", "PRIVATE")
        store.preferences.edit().putString("cards", JSONArray().put(json).toString()).commit()
        assertTrue(store.due("partner", LocalDate.of(2026, 10, 10)).isEmpty())
        assertEquals(1, store.due("author", LocalDate.of(2026, 10, 10)).size)
    }
}
