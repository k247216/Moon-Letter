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
class TimelinePhotoAdoptionTest {
    @Test fun assigningTheUploadedAssetIdKeepsTheAuthorsAlreadyDecodedLocalPhoto(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        EntryPhotos.installSource("author-adoption-${UUID.randomUUID()}")
        val local = File(context.filesDir, "photos/${UUID.randomUUID()}.png")
        local.parentFile!!.mkdirs(); local.writeBytes(bytes())
        try {
            val displayed = requireNotNull(EntryPhotos.load(context, local.absolutePath, null))
            val id = UUID.randomUUID().toString()
            assertSame("An upload acknowledgement must not hide the author's already-loaded photo",
                displayed, EntryPhotos.cached(local.absolutePath, id))
            assertSame(displayed, EntryPhotos.load(context, local.absolutePath, id))
            local.writeBytes(byteArrayOf(1, 2))
            assertNull(EntryPhotos.load(context, local.absolutePath, id))
            assertNull(EntryPhotos.cached(local.absolutePath, id))
        } finally { local.delete(); EntryPhotos.installSource("author-adoption-cleanup-${UUID.randomUUID()}") }
    }
    @Test fun adoptingTheDownloadedPathKeepsTheAlreadyVisiblePreviewUntilValidation(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val raw = bytes()
        EntryPhotos.installSource("adoption-${UUID.randomUUID()}", preview = { raw })
        val local = File(context.filesDir, "photos/${UUID.randomUUID()}.png")
        local.parentFile!!.mkdirs(); local.writeBytes(raw)
        try {
            val displayed = requireNotNull(EntryPhotos.load(context, null, id))
            assertSame("Room's downloaded localPath must not turn a loaded cloud photo back into a placeholder",
                displayed, EntryPhotos.cached(local.absolutePath, id))
            assertNotNull(EntryPhotos.load(context, local.absolutePath, id))
            local.writeBytes(byteArrayOf(1, 2))
            assertNull("A stable asset key must still revalidate an invalid local source",
                EntryPhotos.load(context, local.absolutePath, id))
            assertNull(EntryPhotos.cached(local.absolutePath, id))
        } finally { local.delete(); EntryPhotos.installSource("adoption-cleanup-${UUID.randomUUID()}") }
    }

    private fun bytes(): ByteArray {
        val image = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } }
        finally { image.recycle() }
    }
}
