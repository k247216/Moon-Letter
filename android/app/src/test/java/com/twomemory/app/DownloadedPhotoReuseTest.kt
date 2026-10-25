package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedPhotoReuseTest {
    @Test fun downloadedOriginalSuppliesBothPreviewProfilesWithoutAnotherNetworkRequest(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val asset = UUID.randomUUID().toString()
        val scope = "synthetic-download-reuse-${UUID.randomUUID()}"
        val pixels = bytes()
        var previews = 0
        var readings = 0
        var originals = 0
        EntryPhotos.installSource(scope, file = { requested, target ->
            assertEquals(asset, requested); originals++; target.writeBytes(pixels)
        }, preview = { previews++; pixels }, reading = { readings++; pixels })
        try {
            val original = requireNotNull(EntryPhotos.localOriginal(context, null, asset))
            assertArrayEquals(pixels, original.readBytes())
            assertNotNull(EntryPhotos.load(context, null, asset))
            assertNotNull(EntryPhotos.load(context, null, asset, reading = true))
            assertEquals("A downloaded original must not require another small-preview request", 0, previews)
            assertEquals("A downloaded original must not require another reading-preview request", 0, readings)
            assertEquals(1, originals)
            assertArrayEquals(pixels, original.readBytes())
            val broken = java.io.File(context.filesDir, "photos/${UUID.randomUUID()}.png")
            broken.parentFile!!.mkdirs(); broken.writeBytes(byteArrayOf(1, 2))
            try {
                assertNotNull("A broken local alias must not mask the downloaded original",
                    EntryPhotos.load(context, broken.absolutePath, asset, reading = true))
                broken.writeBytes(byteArrayOf())
                assertNotNull("An empty local alias must not mask the downloaded original",
                    EntryPhotos.load(context, broken.absolutePath, asset))
                assertArrayEquals(pixels, original.readBytes())
            } finally { broken.delete() }
        } finally { EntryPhotos.installSource("download-reuse-cleanup-${UUID.randomUUID()}") }
    }

    private fun bytes(): ByteArray {
        val image = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        } } finally { image.recycle() }
    }
}
