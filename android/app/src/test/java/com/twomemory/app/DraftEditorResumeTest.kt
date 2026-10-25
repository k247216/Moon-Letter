package com.twomemory.app

import androidx.test.core.app.ApplicationProvider
import com.twomemory.editor.EditorViewModel
import com.twomemory.editor.EditorPhoto
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
@Config(sdk=[34])
class DraftEditorResumeTest {
    private fun library()=DraftLibrary(ApplicationProvider.getApplicationContext(),UUID.randomUUID().toString())
    @Test fun newRecordResumesAnExplicitAutosaveIncludingPhotosDateAndPrivacy() {
        val library=library(); val id=UUID.randomUUID().toString()
        val date=Instant.parse("2026-09-01T12:00:00Z")
        val photo=EditorPhoto(UUID.randomUUID(),"/kept-original.jpg","image/jpeg")
        library.save(id,EntryMode.PERSONAL,DraftStore.Draft("没写完","刚写下的最后一句",listOf(photo),occurrence=date,visibility=EntryVisibility.PRIVATE))
        library.activate(id,EntryMode.PERSONAL)
        val editor=EditorViewModel()
        assertEquals(id,openDraftEditor(library,EntryMode.PERSONAL,editor))
        assertEquals("刚写下的最后一句",editor.state.value.body)
        assertEquals(listOf(photo),editor.state.value.photos)
        assertEquals(date,editor.state.value.occurrenceTime)
        assertEquals(EntryVisibility.PRIVATE,editor.state.value.visibility)
        assertEquals(1,library.list().size)
    }
    @Test fun explicitlyStartingAnotherPageDoesNotOverwriteTheUnfinishedOne() {
        val library=library(); val id=UUID.randomUUID().toString()
        library.save(id,EntryMode.PERSONAL,DraftStore.Draft("旧草稿","保留我"))
        val editor=EditorViewModel(); editor.updateBody("上次编辑器的文字")
        assertNotEquals(id,openDraftEditor(library,EntryMode.PERSONAL,editor,fresh=true))
        assertEquals("",editor.state.value.body)
        assertEquals("保留我",library.load(id)?.draft?.body)
    }
    @Test fun aDifferentAccountOrModeNeverResumesSomeoneElsesDraft() {
        val a=library(); val b=library(); val id=UUID.randomUUID().toString()
        a.save(id,EntryMode.COLLABORATIVE,DraftStore.Draft("共同记录","只属于此账户的草稿"))
        val editor=EditorViewModel()
        assertNotEquals(id,openDraftEditor(b,EntryMode.COLLABORATIVE,editor))
        assertEquals("",editor.state.value.body)
        assertNotEquals(id,openDraftEditor(a,EntryMode.PERSONAL,editor))
        assertEquals("",editor.state.value.body)
    }

    @Test fun manuallySavedDraftStaysInDraftBoxButNextRecordStartsEmpty() {
        val library=library(); val id=UUID.randomUUID().toString()
        val draft=DraftStore.Draft("手动收好","下一页不要自动带回这段文字")
        library.save(id,EntryMode.PERSONAL,draft)
        library.activate(id,EntryMode.PERSONAL)
        library.markManualSaved(id)
        val editor=EditorViewModel(); editor.updateBody("编辑器遗留正文")
        assertNotEquals(id,openDraftEditor(library,EntryMode.PERSONAL,editor))
        assertEquals("",editor.state.value.body)
        assertEquals("",editor.state.value.title)
        assertEquals(draft,library.load(id)?.draft)
        assertEquals(1,library.list().size)
    }

    @Test fun manualResumeActivatesTheSelectedDraftInsteadOfTheNewestLibraryItem() {
        val library=library()
        library.save(UUID.randomUUID().toString(),EntryMode.PERSONAL,DraftStore.Draft("第一份","正文一"))
        library.save(UUID.randomUUID().toString(),EntryMode.PERSONAL,DraftStore.Draft("第二份","正文二"))
        // Select the non-first item regardless of equal-millisecond save timestamps.
        val selected=library.list().last()
        assertNotEquals(selected.id,library.list().first().id)
        library.activate(selected.id,selected.mode)
        val editor=EditorViewModel()
        assertEquals(selected.id,openDraftEditor(library,selected.mode,editor))
        assertEquals(selected.draft.body,editor.state.value.body)
        assertEquals(2,library.list().size)
    }

    @Test fun freshShareKeepsOldDraftAndItsOriginalMediaAfterTheNewPageAutosaves() {
        val library=library(); val oldId=UUID.randomUUID().toString()
        val photo=EditorPhoto(UUID.randomUUID(),"/unchanged-original.jpg","image/jpeg")
        val old=DraftStore.Draft("旧页","旧页正文",listOf(photo))
        library.save(oldId,EntryMode.PERSONAL,old)
        library.activate(oldId,EntryMode.PERSONAL)
        val editor=EditorViewModel()
        val newId=openDraftEditor(library,EntryMode.PERSONAL,editor,fresh=true)
        assertNotEquals(oldId,newId)
        assertEquals("",editor.state.value.body)
        library.save(newId,EntryMode.PERSONAL,DraftStore.Draft("新分享","这次分享的正文"))
        assertEquals(old,library.load(oldId)?.draft)
        assertEquals(newId,library.activeDraft(EntryMode.PERSONAL)?.id)
        assertEquals(2,library.list().size)
    }
}
