package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
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
class EntryPhotoPreviewSizeTest {
    @Test fun listPreviewDoesNotDecodeDetailSizedImagesOrPoisonDetailCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photo = File(context.filesDir, "photos/${UUID.randomUUID()}.png")
        photo.parentFile!!.mkdirs()
        val source = Bitmap.createBitmap(1400, 1000, Bitmap.Config.ARGB_8888)
        try {
            photo.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val originalBytes = photo.readBytes()
            val preview = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null))
            assertTrue("Small timeline previews must be bounded", maxOf(preview.width, preview.height) <= 720)
            val detail = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null, original = true))
            assertEquals(1400, detail.width)
            assertEquals(1000, detail.height)
            assertArrayEquals(originalBytes, photo.readBytes())
            assertTrue(requireNotNull(EntryPhotos.load(context, photo.absolutePath, null)).width <= 720)
        } finally { source.recycle(); photo.delete() }
    }
}
