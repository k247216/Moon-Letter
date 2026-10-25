package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
import java.io.ByteArrayOutputStream
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
class ReadingPreviewVariantTest {
    @Test fun readingPreviewIsSharperWithoutDownloadingOrPoisoningTheOriginalOrSmallVariant() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val priorSmall = EntryPhotos.remotePreview
        val priorReading = EntryPhotos.remoteReadingPreview
        val priorOriginal = EntryPhotos.remoteFile
        var smallCalls = 0; var readingCalls = 0; var originals = 0
        try {
            EntryPhotos.remotePreview = { smallCalls++; bytes(600, 300) }
            EntryPhotos.remoteReadingPreview = { readingCalls++; bytes(1200, 600) }
            EntryPhotos.remoteFile = { _, _ -> originals++; error("A reading preview is not an original fetch") }
            val small = requireNotNull(EntryPhotos.load(context, null, id))
            val reading = requireNotNull(EntryPhotos.load(context, null, id, reading = true))
            assertEquals(600, small.width)
            assertEquals(1200, reading.width)
            assertSame(small, EntryPhotos.load(context, null, id))
            assertSame(reading, EntryPhotos.load(context, null, id, reading = true))
            assertEquals(1, smallCalls); assertEquals(1, readingCalls); assertEquals(0, originals)
            assertFalse(File(context.filesDir, "downloaded-media/$id").exists())
        } finally {
            EntryPhotos.remotePreview = priorSmall
            EntryPhotos.remoteReadingPreview = priorReading
            EntryPhotos.remoteFile = priorOriginal
        }
    }
    private fun bytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        } } finally { bitmap.recycle() }
    }
}
