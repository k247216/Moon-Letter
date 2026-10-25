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
class SharedPhotoMemoryBudgetTest {
    @Test fun albumAndRecordDetailsShareABoundedPoolWithoutEvictingSmallPreviews() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = List(3) {
            File(context.filesDir, "photos/${UUID.randomUUID()}.png").also { file ->
                file.parentFile!!.mkdirs()
                val image = Bitmap.createBitmap(3200, 2000, Bitmap.Config.ARGB_8888)
                try { file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { image.recycle() }
            }
        }
        try {
            val preview = requireNotNull(EntryPhotos.load(context, files[0].absolutePath, null))
            val detail = requireNotNull(EntryPhotos.load(context, files[0].absolutePath, null, true))
            files.drop(1).forEach { requireNotNull(AlbumPhotoPixels.load(it.absolutePath, true)) }
            assertNotSame("Three 6.4MB detail images must not sit in separate uncoordinated caches", detail,
                EntryPhotos.load(context, files[0].absolutePath, null, true))
            assertSame(preview, EntryPhotos.load(context, files[0].absolutePath, null))
        } finally { files.forEach { it.delete() } }
    }
}
