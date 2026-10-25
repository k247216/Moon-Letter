package com.twomemory.database

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AlbumPhotoOrientationTest {
    @Test fun importedPhonePhotoKeepsCorrectPortraitDimensions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("orientation-", ".jpg", context.cacheDir)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            bitmap.recycle()
            ExifInterface(source.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val repository = AlbumRepository(database, context)
            val album = repository.createAlbum("方向测试", "测试")
            assertEquals(1, repository.importPhotos(album, listOf(Uri.fromFile(source))).importedCount)
            val photo = database.albumDao().observeAlbums().first().single().photos.single()
            assertEquals(20, photo.width)
            assertEquals(40, photo.height)
            val decoded = requireNotNull(AlbumImageDecoder.decode(photo.localPath, 600))
            assertEquals(20, decoded.width)
            assertEquals(40, decoded.height)
            decoded.recycle()
        } finally { database.close(); source.delete() }
    }
}
