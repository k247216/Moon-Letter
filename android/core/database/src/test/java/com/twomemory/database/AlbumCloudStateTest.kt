package com.twomemory.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumCloudStateTest {
    @Test fun legacyAlbumsBindOnceAndCannotTravelToAnotherServerOrCouple() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = AlbumRepository(db, context).createAlbum("旧相册", "旅行")
            val photoId = UUID.randomUUID().toString()
            db.albumDao().addPhoto(AlbumPhotoEntity(photoId, id, "/private/legacy.png", "image/png", 10, 20, 1, 2))
            db.albumDao().claimUnbound("https://api.example.com", "space-a", "owner-a")
            db.albumDao().claimUnbound("https://other.example.com", "space-b", "owner-b")
            val album = db.albumDao().findAlbum(id)!!
            assertEquals("https://api.example.com", album.serverUrl)
            assertEquals("space-a", album.coupleId)
            assertEquals("owner-a", album.createdBy)
            assertEquals("owner-a", db.albumDao().findPhoto(photoId)!!.addedBy)
            db.albumDao().claimUnbound("https://api.example.com", "space-a", "owner-b")
            assertEquals("owner-a", db.albumDao().findPhoto(photoId)!!.addedBy)
            assertEquals(1, AlbumRepository(db, context, AlbumScope("https://api.example.com", "space-a", "owner-a")).observeAlbums().first().size)
            assertTrue(AlbumRepository(db, context, AlbumScope("https://other.example.com", "space-a", "owner-a")).observeAlbums().first().isEmpty())
        } finally { db.close() }
    }
    @Test fun deletedPhotosAreHiddenButKeepRetryTombstoneAndOriginalPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = AlbumRepository(db, context, AlbumScope("https://api.example.com", "space", "owner"))
            val id = repository.createAlbum("一起", "生活")
            val photo = AlbumPhotoEntity(UUID.randomUUID().toString(), id, "/private/photo.png", "image/png", 10, 20, 1, 2, addedBy = "owner")
            db.albumDao().addPhoto(photo)
            repository.deletePhoto(photo.id)
            assertTrue(repository.observeAlbums().first().single().photos.isEmpty())
            val tombstone = db.albumDao().allAlbums().single().photos.single()
            assertTrue(tombstone.deleted)
            assertEquals("/private/photo.png", tombstone.localPath)
        } finally { db.close() }
    }
    @Test fun onlyCreatorMayDeleteWholeAlbum() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val owner = AlbumRepository(db, context, AlbumScope("https://api.example.com", "space", "owner"))
            val id = owner.createAlbum("两个人", "生活")
            try {
                AlbumRepository(db, context, AlbumScope("https://api.example.com", "space", "partner")).deleteAlbum(id)
                fail("partner must not delete creator's entire album")
            } catch (_: IllegalArgumentException) { }
            owner.deleteAlbum(id)
            assertTrue(owner.observeAlbums().first().isEmpty())
            assertTrue(db.albumDao().findAlbum(id)!!.deleted)
        } finally { db.close() }
    }
}
