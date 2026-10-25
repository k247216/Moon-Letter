package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoExifReadingTest {
    @Test fun previewAndHighQualityReadingKeepPortraitOrientationWithoutRewritingTheOriginal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(3200, 1600, BufferedImage.TYPE_INT_RGB), "jpeg", output)
        val jpeg = output.toByteArray()
        val exif = byteArrayOf(-1, -31, 0, 34, 69, 120, 105, 102, 0, 0, 73, 73, 42, 0, 8, 0, 0, 0,
            1, 0, 18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0)
        output.reset(); output.write(jpeg, 0, 2); output.write(exif); output.write(jpeg, 2, jpeg.size - 2)
        val bytes = output.toByteArray()
        val file = File(context.filesDir, "photos/${UUID.randomUUID()}.jpg")
        file.parentFile!!.mkdirs(); file.writeBytes(bytes)
        try {
            val preview = requireNotNull(EntryPhotos.load(context, file.absolutePath, null))
            val detail = requireNotNull(EntryPhotos.load(context, file.absolutePath, null, true))
            assertTrue("The phone's upright portrait must not turn sideways when the original arrives", detail.height > detail.width)
            assertTrue(preview.height > preview.width)
            assertArrayEquals(bytes, file.readBytes())
        } finally { file.delete() }
    }
}
