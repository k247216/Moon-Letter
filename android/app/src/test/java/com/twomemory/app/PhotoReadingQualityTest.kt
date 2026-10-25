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
class PhotoReadingQualityTest {
    @Test fun fullScreenReadingDoesNotReduceAFourThousandPixelPhotoToOneThousand() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photo = File(context.filesDir, "photos/${UUID.randomUUID()}.png")
        photo.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        try {
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val original = photo.readBytes()
            val result = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null, original = true))
            assertTrue("Reading needs a useful 2K display, not a 1000px enlarged preview", result.width >= 2000)
            assertTrue("Decode must stay bounded", result.width <= 2048)
            assertArrayEquals(original, photo.readBytes())
            val preview = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null))
            assertTrue(preview.width <= 640)
        } finally { bitmap.recycle(); photo.delete() }
    }
}
