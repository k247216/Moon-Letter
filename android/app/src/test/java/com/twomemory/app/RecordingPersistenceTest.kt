package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.editor.EditorAttachment
import com.twomemory.editor.EditorPhoto
import com.twomemory.model.BlockType
import com.twomemory.model.EntryMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun removingPublishedSnapshotNeverRemovesMoreRecentDraftEdits() {
        val library = DraftLibrary(context, UUID.randomUUID().toString())
        val id = UUID.randomUUID().toString()
        val sent = DraftStore.Draft("", "提交时的内容")
        library.save(id, EntryMode.PERSONAL, sent)
        library.save(id, EntryMode.PERSONAL, sent.copy(body = "保存期间补写的内容"))
        library.removeIfUnchanged(id, sent)
        assertEquals("保存期间补写的内容", library.load(id)?.draft?.body)
        library.removeIfUnchanged(id, sent.copy(body = "保存期间补写的内容"))
        assertNull(library.load(id))
    }

    @Test fun rememberedBookNameCanResolveAfterRecreationWithoutAcceptingOutsideFiles() {
        val dir = File(context.cacheDir, "memory-books").apply { mkdirs() }
        val file = File(dir, "MoonLetter_${UUID.randomUUID()}.zip").apply { writeBytes(byteArrayOf(1)) }
        assertEquals(file, pendingBookFile(context, file.name))
        assertNull(pendingBookFile(context, "../private.txt"))
    }

    @Test fun readIdsSurviveRecreationButDoNotCrossAccounts() {
        val scope = UUID.randomUUID().toString()
        ReadingProgressStore(context, scope).markRead("old-date-new-record")
        assertEquals(setOf("old-date-new-record"), ReadingProgressStore(context, scope).readIds())
        assertTrue(ReadingProgressStore(context, "$scope-other").readIds().isEmpty())
    }

    @Test fun multipleDraftsRestoreOriginalMediaAndTimeWithoutOverwriting() {
        val scope = UUID.randomUUID().toString()
        val first = UUID.randomUUID().toString(); val second = UUID.randomUUID().toString()
        val photo = EditorPhoto(UUID.randomUUID(), "/private/photo.jpg", "image/jpeg")
        val voice = EditorAttachment(UUID.randomUUID(), BlockType.AUDIO, "/private/voice.m4a", "audio/mp4", "{\"duration\":12}")
        val moment = Instant.parse("2026-09-01T12:00:00Z")
        val library = DraftLibrary(context, scope)
        library.save(first, EntryMode.PERSONAL, DraftStore.Draft("旧草稿", "昨天的声音", listOf(photo), listOf(voice), moment))
        library.save(second, EntryMode.COLLABORATIVE, DraftStore.Draft("", "另一篇"))
        val reopened = DraftLibrary(context, scope)
        assertEquals(2, reopened.list().size)
        assertEquals("昨天的声音", reopened.load(first)?.draft?.body)
        assertEquals(listOf(photo), reopened.load(first)?.draft?.photos)
        assertEquals(listOf(voice), reopened.load(first)?.draft?.attachments)
        assertEquals(moment, reopened.load(first)?.draft?.occurrence)
        assertEquals(EntryMode.COLLABORATIVE, reopened.load(second)?.mode)
        assertTrue(DraftLibrary(context, "$scope-other").list().isEmpty())
    }

    @Test fun removingOneDraftDoesNotDeleteMediaOrOtherDrafts() {
        val library = DraftLibrary(context, UUID.randomUUID().toString())
        val voice = File(context.filesDir, "voice-${UUID.randomUUID()}.m4a").apply { writeBytes(byteArrayOf(1,2,3)) }
        val first = UUID.randomUUID().toString(); val second = UUID.randomUUID().toString()
        library.save(first, EntryMode.PERSONAL, DraftStore.Draft("", "声音", attachments = listOf(
            EditorAttachment(UUID.randomUUID(), BlockType.AUDIO, voice.absolutePath, "audio/mp4", "{}"))))
        library.save(second, EntryMode.PERSONAL, DraftStore.Draft("", "保留"))
        library.remove(first)
        assertNull(library.load(first))
        assertEquals("保留", library.load(second)?.draft?.body)
        assertTrue(voice.isFile)
    }
}
