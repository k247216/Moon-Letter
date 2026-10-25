package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LittleNoteTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun binding(): SyncSession.BindingSnapshot {
        SyncSession.save(context, "synthetic-note", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        return requireNotNull(SyncSession.captureBinding(context))
    }
    @Test fun pendingTextRestoresOnlyInItsOwnSpace() {
        val a = binding(); val store = LittleNoteStore(context, a.session)
        store.keep("今天只想抱一下")
        assertEquals("今天只想抱一下", LittleNoteStore(context, a.session).load().text)
        val b = binding()
        assertEquals("", LittleNoteStore(context, b.session).load().text)
        assertEquals("今天只想抱一下", store.load().text)
    }
    @Test fun savingAndRetryingOneNoteCreatesOnlyOneRecordAndOneOperationPair() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        store.keep("今天也想你。")
        val pending = store.load()
        val first = publishLittleNote(context, bound, pending)
        assertEquals(first, publishLittleNote(context, bound, pending))
        val db = AppDatabase.build(context, bound.session.databaseName)
        val entry = requireNotNull(db.entryDao().findEntry(first.toString()))
        assertEquals("小便签", entry.title)
        assertEquals("DRAFT", entry.state) // Existing local-first state until the server confirms publication.
        assertEquals("今天也想你。", org.json.JSONObject(db.entryDao().blocks(first.toString()).single().payload).getString("text"))
        assertEquals(2, db.outboxDao().pending(System.currentTimeMillis(), 50).count { it.entityId == first.toString() })
    }
    @Test fun blankAndOversizeNotesCannotEnterTheArchive() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        for (text in listOf(" \n ", "字".repeat(501))) {
            store.keep(text)
            assertTrue(runCatching { publishLittleNote(context, bound, store.load()) }.isFailure)
        }
    }
    @Test fun aNewerDraftCannotBeClearedByAnOlderSave() {
        val store = LittleNoteStore(context, binding().session)
        store.keep("旧的一句"); val submitted = store.load()
        store.keep("后来又想到一句")
        store.clearIfSame(submitted)
        assertEquals("后来又想到一句", store.load().text)
    }
    @Test fun switchingSpacesCannotPublishTheOldNoteIntoEitherArchive() = runBlocking {
        val a = binding(); val store = LittleNoteStore(context, a.session)
        store.keep("不能误投")
        val pending = store.load(); val b = binding()
        assertTrue(runCatching { publishLittleNote(context, a, pending) }.isFailure)
        assertNull(AppDatabase.build(context, a.session.databaseName).entryDao().findEntry(pending.id.toString()))
        assertNull(AppDatabase.build(context, b.session.databaseName).entryDao().findEntry(pending.id.toString()))
    }
    @Test fun notePresentationDoesNotReclassifyCollaborativeOrPhotoRecords() {
        val base = TimelineItem(UUID.randomUUID(), UUID.randomUUID(), EntryMode.PERSONAL, EntryState.PUBLISHED,
            Instant.now(), "Asia/Shanghai", "小便签", preview = "一小句")
        assertTrue(base.isLittleNote)
        assertFalse(base.copy(mode = EntryMode.COLLABORATIVE).isLittleNote)
        assertFalse(base.copy(title = "普通记录").isLittleNote)
        assertFalse(base.copy(photo = com.twomemory.model.TimelinePhoto("local", null)).isLittleNote)
    }
    @Test fun hiddenNoteNeverFallsBackToPublicWhenPrivacyCheckFails() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        store.keep("还不想给你看的小惊喜", com.twomemory.model.EntryVisibility.PRIVATE)
        val pending = store.load()
        assertTrue(runCatching { publishLittleNote(context, bound, pending) }.isFailure)
        assertNull(AppDatabase.build(context, bound.session.databaseName).entryDao().findEntry(pending.id.toString()))
        assertEquals(com.twomemory.model.EntryVisibility.PRIVATE, store.load().visibility)
    }
    @Test fun unicodeEmojiUsesCharactersRatherThanUtf16Halves() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        store.keep("🌙".repeat(500))
        val id = publishLittleNote(context, bound, store.load())
        assertNotNull(AppDatabase.build(context, bound.session.databaseName).entryDao().findEntry(id.toString()))
    }
    @Test fun recoveryAfterCommitRetiresTheOldIdAndKeepsNewerWritingPublishable() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        store.keep("已经贴好的话"); val old = store.load()
        publishLittleNote(context, bound, old) // Process dies here, before scratch paper acknowledgement.
        store.keep("后来新想到的话")
        val recovered = restoreLittleNote(context, bound)
        assertEquals("后来新想到的话", recovered.text)
        assertNotEquals(old.id, recovered.id)
        assertEquals(recovered.id, publishLittleNote(context, bound, recovered))
    }
    @Test fun aDeletedCommittedIdCannotTrapANewerPendingNote() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        store.keep("原来的话"); val old = store.load()
        publishLittleNote(context, bound, old)
        AppDatabase.build(context, bound.session.databaseName).entryDao().withdraw(old.id.toString())
        store.keep("新的一句话")
        val recovered = restoreLittleNote(context, bound)
        assertNotEquals(old.id, recovered.id)
        assertEquals("新的一句话", recovered.text)
        publishLittleNote(context, bound, recovered)
        assertTrue(AppDatabase.build(context, bound.session.databaseName).entryDao().findEntry(old.id.toString())!!.deleted)
    }
    @Test fun anAlreadyCommittedIdenticalDraftDoesNotReappearAsUnsent() = runBlocking {
        val bound = binding(); val store = LittleNoteStore(context, bound.session)
        store.keep("已经贴好了"); val old = store.load()
        publishLittleNote(context, bound, old)
        val recovered = restoreLittleNote(context, bound)
        assertNotEquals(old.id, recovered.id)
        assertEquals("", recovered.text)
    }
}
