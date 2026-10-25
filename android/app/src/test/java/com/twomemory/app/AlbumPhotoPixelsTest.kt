package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPhotoPixelsTest {
    @Test fun repeatedAlbumPreviewReturnsDecodedPixelsWithoutStartingEmptyAgain() = runBlocking {
        val file = photo(100, 80)
        try {
            val before = requireNotNull(AlbumPhotoPixels.load(file.absolutePath, false))
            assertSame(before, AlbumPhotoPixels.cached(file.absolutePath, false))
            assertSame(before, AlbumPhotoPixels.load(file.absolutePath, false))
        } finally { file.delete() }
    }
    @Test fun detailReadingDoesNotReplaceTheListPixelsOrChangeTheSavedFile() = runBlocking {
        val file = photo(3000, 2000)
        try {
            val bytes = file.readBytes()
            val small = requireNotNull(AlbumPhotoPixels.load(file.absolutePath, false))
            val large = requireNotNull(AlbumPhotoPixels.load(file.absolutePath, true))
            assertTrue(small.width <= 600); assertTrue(large.width > 1000)
            assertSame(small, AlbumPhotoPixels.load(file.absolutePath, false))
            assertArrayEquals(bytes, file.readBytes())
        } finally { file.delete() }
    }
    @Test fun invalidatedFileDoesNotRetainOldPixelsAfterValidation() = runBlocking {
        val file = photo(100, 80)
        requireNotNull(AlbumPhotoPixels.load(file.absolutePath, false))
        assertTrue(file.delete())
        assertNull(AlbumPhotoPixels.load(file.absolutePath, false))
        assertNull(AlbumPhotoPixels.cached(file.absolutePath, false))
    }
    private fun photo(width: Int, height: Int): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "photos/${UUID.randomUUID()}.png")
        file.parentFile!!.mkdirs()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
        return file
    }
}
