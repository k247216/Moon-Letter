package com.twomemory.database

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: AlbumRepository
    private lateinit var source: File
    private val databaseName = "album-repository-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = openDatabase()
        repository = AlbumRepository(database, context)
        source = File(context.cacheDir, "picked-photo.png")
        source.outputStream().use {
            Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        source.delete()
        File(context.filesDir, "independent_albums").deleteRecursively()
    }

    @Test fun albumsAndPhotosSurviveReopeningWithoutTouchingEntriesOrOutbox() = runBlocking {
        val entry = EntryEntity("entry", "couple", "author", "PERSONAL", "DRAFT", 1, "Asia/Shanghai", "保留的记录")
        database.entryDao().insertEntry(entry)
        database.openHelper.writableDatabase.execSQL("INSERT INTO outbox_operations VALUES ('op', 'couple', 'entry', 'CREATE_ENTRY', '{}', 0, 0, 0, 'PENDING')")
        val albumId = repository.createAlbum("  湖边的周末  ", "  旅行  ")
        val report = repository.importPhotos(albumId, listOf(Uri.fromFile(source)))
        assertEquals(1, report.importedCount)
        assertTrue(report.failures.isEmpty())
        val photo = database.albumDao().observeAlbums().first().single().photos.single()
        assertFalse(photo.localPath.startsWith(context.cacheDir.absolutePath))
        assertArrayEquals(source.readBytes(), File(photo.localPath).readBytes())
        source.delete()
        database.close()
        database = openDatabase()
        val stored = database.albumDao().observeAlbums().first().single()
        assertEquals("湖边的周末", stored.album.name)
        assertEquals("旅行", database.albumDao().observeCategories().first().single().name)
        assertEquals(photo.id, stored.album.coverPhotoId)
        assertTrue(File(stored.photos.single().localPath).isFile)
        assertEquals(entry, database.entryDao().findEntry("entry"))
        assertEquals("op", database.outboxDao().pending(Long.MAX_VALUE, 10).single().operationId)
        assertEquals(0, count("entry_blocks"))
    }

    @Test fun invalidPhotoIsReportedAndLeavesNoRowOrPartialFile() = runBlocking {
        val albumId = repository.createAlbum("日常", "生活")
        source.writeText("not a photo")
        val report = repository.importPhotos(albumId, listOf(Uri.fromFile(source)))
        assertEquals(0, report.importedCount)
        assertEquals(1, report.failures.size)
        assertTrue(database.albumDao().observeAlbums().first().single().photos.isEmpty())
        assertTrue(File(context.filesDir, "independent_albums/$albumId").listFiles().orEmpty().isEmpty())
    }

    @Test fun partialImportKeepsSuccessfulPhotosAndReportsUnavailableSource() = runBlocking {
        val albumId = repository.createAlbum("小事", "生活")
        val report = repository.importPhotos(albumId, listOf(Uri.fromFile(source), Uri.fromFile(File(context.cacheDir, "missing.png"))))
        assertEquals(1, report.importedCount)
        assertEquals(1, report.failures.size)
        assertEquals(1, database.albumDao().observeAlbums().first().single().photos.size)
    }

    @Test fun categoryIsReusedAndCoverCannotReferToAnotherAlbum() = runBlocking {
        val firstId = repository.createAlbum("一个相册", " Travel ")
        val secondId = repository.createAlbum("另一个相册", "travel")
        repository.importPhotos(firstId, listOf(Uri.fromFile(source)))
        val first = database.albumDao().observeAlbums().first().single { it.album.id == firstId }
        assertEquals(1, database.albumDao().observeCategories().first().size)
        assertFalse(repository.setCover(secondId, first.photos.single().id))
        assertNull(database.albumDao().findAlbum(secondId)?.coverPhotoId)
        assertEquals(0, count("entries"))
        assertEquals(0, count("outbox_operations"))
    }

    @Test fun blankNamesDoNotCreateCategoriesOrAlbums() = runBlocking {
        for ((name, category) in listOf(" " to "旅行", "旅行" to " ")) {
            try {
                repository.createAlbum(name, category)
                fail("Blank album or category must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
        assertTrue(database.albumDao().observeAlbums().first().isEmpty())
        assertTrue(database.albumDao().observeCategories().first().isEmpty())
    }

    private fun openDatabase() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()

    private fun count(table: String): Int = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
        it.moveToFirst()
        it.getInt(0)
    }
}
