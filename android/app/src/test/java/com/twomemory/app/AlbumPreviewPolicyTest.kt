package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.*
import com.twomemory.network.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPreviewPolicyTest {
    @Test fun cachedLightPreviewWinsOverAnExistingLargeOriginal(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = SyncSession.Session("test", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:1")
        val original = File(context.filesDir, "cloud_album_cache/test/${UUID.randomUUID()}.image")
        original.parentFile!!.mkdirs(); original.writeBytes(byteArrayOf(1, 2, 3, 4))
        val photo = AlbumPhotoEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(), original.absolutePath,
            "image/jpeg", 4000, 3000, 1, 1, UUID.randomUUID().toString(), session.userId.toString(), true)
        val preview = requireNotNull(AlbumImageLoader.previewFile(context, photo, session))
        preview.parentFile!!.mkdirs(); preview.writeBytes(byteArrayOf(5, 6))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val category = UUID.randomUUID().toString()
        try {
            db.albumDao().insertCategory(AlbumCategoryEntity(category, "Synthetic", 1))
            db.albumDao().upsertAlbum(AlbumEntity(photo.albumId, "Synthetic", category, 1,
                serverUrl = session.storageOrigin, coupleId = session.coupleId.toString(), cloudKnown = true))
            db.albumDao().upsertPhoto(photo)
            assertEquals("A thumbnail must not reopen the large original once its preview exists", preview,
                AlbumImageLoader.load(context, photo, false, session, db))
            assertEquals(original, AlbumImageLoader.load(context, photo, true, session, db))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), original.readBytes())
        } finally { db.close(); preview.delete(); original.delete() }
    }
    @Test fun cachedPreviewStillRejectsDeletedOrForeignReferences(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = SyncSession.Session("synthetic", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:1")
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val category = UUID.randomUUID().toString(); val albumId = UUID.randomUUID().toString()
        val original = File(context.filesDir, "cloud_album_cache/${UUID.randomUUID()}.image")
        val photo = AlbumPhotoEntity(UUID.randomUUID().toString(), albumId, original.absolutePath,
            "image/jpeg", 800, 600, 1, 1, UUID.randomUUID().toString(), session.userId.toString(), true)
        val album = AlbumEntity(albumId, "Synthetic", category, 1, serverUrl = session.storageOrigin,
            coupleId = session.coupleId.toString(), cloudKnown = true)
        val preview = requireNotNull(AlbumImageLoader.previewFile(context, photo, session))
        preview.parentFile!!.mkdirs(); preview.writeBytes(byteArrayOf(5, 6))
        try {
            db.albumDao().insertCategory(AlbumCategoryEntity(category, "Synthetic", 1))
            db.albumDao().upsertAlbum(album); db.albumDao().upsertPhoto(photo.copy(deleted = true))
            assertTrue("A removed photo must not bypass validation through its disk cache",
                runCatching { AlbumImageLoader.load(context, photo, false, session, db) }.isFailure)
            db.albumDao().upsertPhoto(photo); db.albumDao().upsertAlbum(album.copy(deleted = true))
            assertTrue(runCatching { AlbumImageLoader.load(context, photo, false, session, db) }.isFailure)
            db.albumDao().upsertAlbum(album.copy(coupleId = UUID.randomUUID().toString()))
            assertTrue(runCatching { AlbumImageLoader.load(context, photo, false, session, db) }.isFailure)
            assertArrayEquals(byteArrayOf(5, 6), preview.readBytes())
            assertFalse(original.exists())
        } finally { db.close(); preview.delete() }
    }
    @Test fun unsupportedPreviewNeverDownloadsOrWritesAnOriginal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val session = SyncSession.Session("test", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:1")
        val category = UUID.randomUUID().toString(); val album = UUID.randomUUID().toString()
        val photoId = UUID.randomUUID().toString(); val assetId = UUID.randomUUID().toString()
        val file = File(context.filesDir, "cloud_album_cache/test/$photoId.image")
        val photo = AlbumPhotoEntity(photoId, album, file.absolutePath, "image/heic", 100, 200, 1, 1, assetId, session.userId.toString(), true)
        var originals = 0
        val transport = object : MediaApi by RetrofitMediaApi.create() {
            override suspend fun downloadPreview(baseUrl: String, bearer: String, assetId: UUID): ByteArray = throw MediaDownloadException(415)
            override suspend fun download(baseUrl: String, bearer: String, assetId: UUID): ByteArray { originals++; return byteArrayOf(1, 2, 3) }
        }
        try {
            db.albumDao().insertCategory(AlbumCategoryEntity(category, "test", 1))
            db.albumDao().upsertAlbum(AlbumEntity(album, "test", category, 1, serverUrl = session.baseUrl, coupleId = session.coupleId.toString(), createdBy = session.userId.toString(), cloudKnown = true))
            db.albumDao().upsertPhoto(photo)
            try { AlbumImageLoader.load(context, photo, false, session, db, transport); fail("Preview must report unsupported format") }
            catch (expected: MediaDownloadException) { assertEquals(415, expected.status) }
            assertEquals(0, originals); assertFalse(file.exists())
        } finally { db.close() }
    }
}
