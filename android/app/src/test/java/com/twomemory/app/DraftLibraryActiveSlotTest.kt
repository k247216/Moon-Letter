package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.editor.EditorPhoto
import com.twomemory.editor.EditorViewModel
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryVisibility
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftLibraryActiveSlotTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun id() = UUID.randomUUID().toString()

    @Test fun automaticSavePersistsAnActiveSlotAcrossLibraryRecreation() {
        val scope=id(); val slot=id()
        val draft=DraftStore.Draft("中断的页","还没手动收好")
        DraftLibrary(context,scope).save(slot,EntryMode.PERSONAL,draft)
        val reopened=DraftLibrary(context,scope)
        assertEquals(slot,reopened.activeDraft(EntryMode.PERSONAL)?.id)
        assertEquals(draft,reopened.activeDraft(EntryMode.PERSONAL)?.draft)
    }

    @Test fun manualFinishClosesOnlyThatModesMatchingActiveSlot() {
        val scope=id(); val personal=id(); val shared=id()
        val library=DraftLibrary(context,scope)
        library.save(personal,EntryMode.PERSONAL,DraftStore.Draft("独立页","个人正文"))
        library.save(shared,EntryMode.COLLABORATIVE,DraftStore.Draft("共同页","共同正文"))
        library.markManualSaved(personal)
        val reopened=DraftLibrary(context,scope)
        assertNull(reopened.activeDraft(EntryMode.PERSONAL))
        assertEquals(shared,reopened.activeDraft(EntryMode.COLLABORATIVE)?.id)
        assertEquals(2,reopened.list().size)
    }

    @Test fun finishingAnOlderDraftCannotClearTheNewerActiveSlot() {
        val library=DraftLibrary(context,id()); val older=id(); val newer=id()
        library.save(older,EntryMode.PERSONAL,DraftStore.Draft("旧页","旧正文"))
        library.save(newer,EntryMode.PERSONAL,DraftStore.Draft("新页","新正文"))
        library.markManualSaved(older)
        assertEquals(newer,library.activeDraft(EntryMode.PERSONAL)?.id)
        assertEquals(2,library.list().size)
    }

    @Test fun anotherAccountAndWrongModeCannotClaimAnActiveDraft() {
        val scope=id(); val slot=id(); val library=DraftLibrary(context,scope)
        library.save(slot,EntryMode.PERSONAL,DraftStore.Draft("仅此账号","原正文"))
        assertNull(DraftLibrary(context,id()).activeDraft(EntryMode.PERSONAL))
        library.activate(slot,EntryMode.COLLABORATIVE)
        assertNull(library.activeDraft(EntryMode.COLLABORATIVE))
        assertEquals(slot,library.activeDraft(EntryMode.PERSONAL)?.id)
    }

    @Test fun legacyLibraryItemsWithoutAnActiveMarkerStaySavedButDoNotAutoResume() {
        val scope=id(); val slot=id(); val library=DraftLibrary(context,scope)
        val draft=DraftStore.Draft("旧版本保存的页","不知道是否手动收好的正文")
        library.save(slot,EntryMode.PERSONAL,draft)
        // Simulate a pre-sidecar installation: existing library/slot data survives untouched.
        assertTrue(context.getSharedPreferences("moon_letter_draft_active_${recordingScopeKey(scope)}",Context.MODE_PRIVATE)
            .edit().clear().commit())
        val reopened=DraftLibrary(context,scope)
        val editor=EditorViewModel()
        assertNotEquals(slot,openDraftEditor(reopened,EntryMode.PERSONAL,editor))
        assertEquals("",editor.state.value.body)
        assertEquals(draft,reopened.load(slot)?.draft)
    }

    @Test fun activatingAndManuallyFinishingDoNotRewriteExistingSlotOrLibraryData() {
        val scope=id(); val slot=id(); val library=DraftLibrary(context,scope)
        val photo=EditorPhoto(UUID.randomUUID(),"/original-must-stay.jpg","image/jpeg")
        val draft=DraftStore.Draft("完整保存","正文",listOf(photo),
            occurrence=Instant.parse("2026-09-01T12:00:00Z"),visibility=EntryVisibility.PRIVATE)
        library.save(slot,EntryMode.PERSONAL,draft)
        val key=recordingScopeKey(scope)
        val index=context.getSharedPreferences("moon_letter_draft_library_$key",Context.MODE_PRIVATE)
        val payload=context.getSharedPreferences("moon_letter_draft-library-$key-$slot",Context.MODE_PRIVATE)
        val indexBefore=index.all.toMap(); val payloadBefore=payload.all.toMap()
        library.activate(slot,EntryMode.PERSONAL)
        library.markManualSaved(slot)
        assertEquals(indexBefore,index.all)
        assertEquals(payloadBefore,payload.all)
        assertEquals(draft,library.load(slot)?.draft)
    }
}
