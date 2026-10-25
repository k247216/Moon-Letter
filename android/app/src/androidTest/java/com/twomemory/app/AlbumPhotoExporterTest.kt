package com.twomemory.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.twomemory.database.AlbumPhotoEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumPhotoExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<File>()
    private val exported = mutableListOf<Uri>()

    @After fun cleanUp() {
        exported.forEach { context.contentResolver.delete(it, null, null) }
        files.forEach { it.delete() }
    }

    // Catches re-encoding, wrong MIME/extension, early publication and overwriting a prior copy.
    @SdkSuppress(minSdkVersion = 29)
    @Test fun mediaStorePublishesIndependentByteExactCopies() = runBlocking {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val photo = photo(bytes, "image/png")
        val exporter = AlbumPhotoExporter(context)
        repeat(2) {
            val uri = exporter.saveToMediaStore(photo)
            exported += uri
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.IS_PENDING,
                MediaStore.Images.Media.RELATIVE_PATH), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).endsWith(".png"))
                assertEquals("image/png", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals("Pictures/MoonLetter/", cursor.getString(3))
            }
        }
        assertNotEquals(exported[0], exported[1])
        assertArrayEquals(bytes, File(photo.localPath).readBytes())
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun realMediaStoreWriteFailureRemovesPendingRowAndKeepsEarlierExport() = runBlocking {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        bitmap.recycle()
        val photo = photo(bytes, "image/jpeg")
        val earlier = AlbumPhotoExporter(context).saveToMediaStore(photo)
        exported += earlier
        assertArrayEquals(bytes, context.contentResolver.openInputStream(earlier)!!.use { it.readBytes() })
        val real = AndroidAlbumExportStorage(context.contentResolver)
        var pending: Uri? = null
        val storage = object : AlbumExportStorage by real {
            override fun insertImage(values: ContentValues): Uri? = real.insertImage(values).also {
                pending = it
                if (it != null) exported += it
            }
            override fun openOutput(uri: Uri): OutputStream {
                context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1, it.getInt(0))
                }
                throw IOException("Simulated full storage")
            }
        }
        expectFailure { AlbumPhotoExporter(context, storage).saveToMediaStore(photo) }
        context.contentResolver.query(pending!!, arrayOf(MediaStore.Images.Media._ID), null, null, null)!!.use {
            assertEquals(0, it.count)
        }
        exported.remove(requireNotNull(pending))
        assertArrayEquals(bytes, context.contentResolver.openInputStream(earlier)!!.use { it.readBytes() })
        assertArrayEquals(bytes, File(photo.localPath).readBytes())
    }

    // Catches losing the original format when the private source has the generic .image suffix.
    @Test fun documentPickerUsesOriginalMimeAndExtension() {
        listOf("image/jpeg" to ".jpg", "image/png" to ".png", "image/heic" to ".heic",
            "image/heif" to ".heif", "image/webp" to ".webp").forEach { (mime, suffix) ->
            val intent = AlbumPhotoExporter(context).createDocumentIntent(photo(byteArrayOf(1), mime))
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
            assertEquals(mime, intent.type)
            assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(suffix))
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun failedWriteDeletesOnlyNewPendingRowAndKeepsSource() = runBlocking {
        val storage = FaultStorage(failWrite = true)
        val photo = photo(ByteArray(70_000) { 42 }, "image/heic")
        expectFailure { AlbumPhotoExporter(context, storage).saveToMediaStore(photo) }
        assertEquals(listOf(storage.newUri), storage.deleted)
        assertFalse(storage.published)
        assertEquals(1, storage.values!!.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertEquals("image/heic", storage.values!!.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(storage.values!!.getAsString(MediaStore.Images.Media.DISPLAY_NAME).endsWith(".heic"))
        assertEquals(70_000, File(photo.localPath).length().toInt())
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun failedPublishDeletesNewRowInsteadOfReportingSuccess() = runBlocking {
        val storage = FaultStorage(publishCount = 0)
        expectFailure { AlbumPhotoExporter(context, storage).saveToMediaStore(photo(byteArrayOf(1, 2), "image/jpeg")) }
        assertEquals(listOf(storage.newUri), storage.deleted)
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun nullOutputDeletesNewPendingRow() = runBlocking {
        val storage = FaultStorage(nullOutput = true)
        expectFailure { AlbumPhotoExporter(context, storage).saveToMediaStore(photo(byteArrayOf(1), "image/png")) }
        assertEquals(listOf(storage.newUri), storage.deleted)
        assertFalse(storage.published)
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun closeFailureCannotPublishAnIncompleteCopy() = runBlocking {
        val storage = FaultStorage(failClose = true)
        expectFailure { AlbumPhotoExporter(context, storage).saveToMediaStore(photo(byteArrayOf(1), "image/png")) }
        assertEquals(listOf(storage.newUri), storage.deleted)
        assertFalse(storage.published)
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun cancellationDuringCopyDeletesPendingRowAndPropagates() {
        val storage = FaultStorage()
        val job = Job()
        try {
            runBlocking(job) {
                storage.onWrite = { storage.currentJob.cancel() }
                storage.currentJob = currentCoroutineContext()[Job]!!
                AlbumPhotoExporter(context, storage).saveToMediaStore(photo(ByteArray(70_000), "image/png"))
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf(storage.newUri), storage.deleted)
            assertFalse(storage.published)
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun cancellationAtPublishBoundaryRemovesOnlyCurrentCopy() {
        val storage = FaultStorage()
        try {
            runBlocking {
                storage.currentJob = currentCoroutineContext()[Job]!!
                storage.onPublish = { storage.currentJob.cancel() }
                AlbumPhotoExporter(context, storage).saveToMediaStore(photo(byteArrayOf(1, 2), "image/png"))
            }
            fail("Cancellation must propagate instead of returning success")
        } catch (_: CancellationException) {
            assertEquals(listOf(storage.newUri), storage.deleted)
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun missingOrEmptySourceCreatesNoDestination() = runBlocking {
        val storage = FaultStorage()
        val exporter = AlbumPhotoExporter(context, storage)
        val empty = photo(byteArrayOf(), "image/png")
        expectFailure { exporter.saveToMediaStore(empty) }
        File(empty.localPath).delete()
        expectFailure { exporter.saveToMediaStore(empty) }
        assertNull(storage.values)
        assertTrue(storage.deleted.isEmpty())
    }

    @Test fun documentCopyKeepsHeicBytesAndSourceIntact() = runBlocking {
        val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 104, 101, 105, 99, 7, 8)
        val photo = photo(bytes, "image/heic")
        val storage = FaultStorage()
        val uri = Uri.parse("content://documents/new-photo")
        assertEquals(uri, AlbumPhotoExporter(context, storage).saveToDocument(photo, uri))
        assertArrayEquals(bytes, storage.bytes.toByteArray())
        assertArrayEquals(bytes, File(photo.localPath).readBytes())
        assertNull(storage.values)
        assertFalse(storage.published)
    }

    @Test fun documentFailureDeletesOnlySelectedNewDocument() = runBlocking {
        val storage = FaultStorage(failWrite = true)
        val uri = Uri.parse("content://documents/new-photo")
        expectFailure { AlbumPhotoExporter(context, storage).saveToDocument(photo(byteArrayOf(1), "image/png"), uri) }
        assertEquals(listOf(uri), storage.deletedDocuments)
        assertTrue(storage.deleted.isEmpty())
    }

    @Test fun documentCancellationCleansNewDocumentAndPropagates() {
        val storage = FaultStorage()
        val uri = Uri.parse("content://documents/new-photo")
        try {
            runBlocking {
                storage.currentJob = currentCoroutineContext()[Job]!!
                storage.onWrite = { storage.currentJob.cancel() }
                AlbumPhotoExporter(context, storage).saveToDocument(photo(ByteArray(70_000), "image/png"), uri)
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf(uri), storage.deletedDocuments)
            assertTrue(storage.deleted.isEmpty())
        }
    }

    @SdkSuppress(minSdkVersion = 26, maxSdkVersion = 28)
    @Test fun legacyAndroidCannotInsertIntoMediaStore() = runBlocking {
        val storage = FaultStorage()
        try {
            AlbumPhotoExporter(context, storage).saveToMediaStore(photo(byteArrayOf(1), "image/png"))
            fail("Legacy Android must use CREATE_DOCUMENT")
        } catch (_: IllegalStateException) { }
        assertNull(storage.values)
    }

    @Test fun refusesFileDestinationBeforeOpeningOrDeletingSource() = runBlocking {
        val storage = FaultStorage()
        val photo = photo(byteArrayOf(1, 2, 3), "image/png")
        try {
            AlbumPhotoExporter(context, storage).saveToDocument(photo, Uri.fromFile(File(photo.localPath)))
            fail("A file URI must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(byteArrayOf(1, 2, 3), File(photo.localPath).readBytes())
        assertTrue(storage.deletedDocuments.isEmpty())
        assertEquals(0, storage.bytes.size())
    }

    private fun photo(bytes: ByteArray, mime: String): AlbumPhotoEntity {
        val file = File.createTempFile("album-export-test-", ".image", context.cacheDir)
        files += file
        file.writeBytes(bytes)
        return AlbumPhotoEntity("photo", "album", file.absolutePath, mime, 3, 2, 1, 1)
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected export failure") } catch (_: IOException) { }
    }

    // Only the destination I/O boundary is replaced to induce otherwise nondeterministic faults.
    private class FaultStorage(
        private val failWrite: Boolean = false,
        private val publishCount: Int = 1,
        private val nullOutput: Boolean = false,
        private val failClose: Boolean = false,
    ) : AlbumExportStorage {
        val newUri = Uri.parse("content://media/external/images/media/123")
        val bytes = ByteArrayOutputStream()
        val deleted = mutableListOf<Uri>()
        val deletedDocuments = mutableListOf<Uri>()
        var values: ContentValues? = null
        var published = false
        lateinit var currentJob: Job
        var onWrite: (() -> Unit)? = null
        var onPublish: (() -> Unit)? = null
        override fun insertImage(values: ContentValues): Uri { this.values = ContentValues(values); return newUri }
        override fun openOutput(uri: Uri): OutputStream? = if (nullOutput) null else object : OutputStream() {
            override fun write(b: Int) { if (failWrite) throw IOException("Disk full"); bytes.write(b); onWrite?.invoke() }
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (failWrite) throw IOException("Disk full")
                bytes.write(b, off, len)
                onWrite?.invoke()
            }
            override fun close() {
                assertFalse("Must close before publishing", published)
                if (failClose) throw IOException("Cannot finish write")
            }
        }
        override fun publishImage(uri: Uri): Int { published = true; onPublish?.invoke(); return publishCount }
        override fun deleteImage(uri: Uri) { deleted += uri }
        override fun deleteDocument(uri: Uri) { deletedDocuments += uri }
    }
}
