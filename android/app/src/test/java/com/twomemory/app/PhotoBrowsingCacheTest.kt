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
class PhotoBrowsingCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun returningToAnAlreadyLoadedPhotoHasAnImmediatePreview() = runBlocking {
        val photo = fixture(80, 60)
        try {
            val preview = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null))
            assertSame("A revisited page should not start with an empty slot", preview,
                EntryPhotos.cached(photo.absolutePath, null))
            assertNull(EntryPhotos.cached(null, UUID.randomUUID().toString()))
        } finally { photo.delete() }
    }

    @Test fun manySmallPhotosDoNotEvictTheFirstAfterOnlyEightItems() = runBlocking {
        val photos = List(20) { fixture(80, 60) }
        try {
            val first = requireNotNull(EntryPhotos.load(context, photos.first().absolutePath, null))
            photos.drop(1).forEach { EntryPhotos.load(context, it.absolutePath, null) }
            assertSame("Small thumbnails should be kept while their byte budget has room", first,
                EntryPhotos.load(context, photos.first().absolutePath, null))
        } finally { photos.forEach { it.delete() } }
    }

    @Test fun openingLargeDetailsDoesNotDisplaceSmallListPreviews() = runBlocking {
        val previewFile = fixture(80, 60)
        val details = List(10) { fixture(1200, 1000) }
        try {
            val preview = requireNotNull(EntryPhotos.load(context, previewFile.absolutePath, null))
            details.forEach { EntryPhotos.load(context, it.absolutePath, null, original = true) }
            assertSame("Detail viewing must not make the photo wall reload", preview,
                EntryPhotos.load(context, previewFile.absolutePath, null))
        } finally { previewFile.delete(); details.forEach { it.delete() } }
    }

    @Test fun aChangedLocalFileIsRevalidatedAndNeverReusesTheOldBitmap() = runBlocking {
        val photo = fixture(80, 60)
        try {
            val before = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null))
            write(photo, 120, 90)
            val after = requireNotNull(EntryPhotos.load(context, photo.absolutePath, null))
            assertNotSame(before, after)
            assertEquals(120, after.width)
            assertEquals(90, after.height)
        } finally { photo.delete() }
    }

    @Test fun largePreviewBrowsingRemainsBoundedInsteadOfKeepingAllDecodedImages() = runBlocking {
        val photos = List(24) { fixture(600, 600) }
        try {
            val first = requireNotNull(EntryPhotos.load(context, photos.first().absolutePath, null))
            photos.drop(1).forEach { EntryPhotos.load(context, it.absolutePath, null) }
            assertNotSame("A byte-limited cache must eventually evict large previews", first,
                EntryPhotos.load(context, photos.first().absolutePath, null))
        } finally { photos.forEach { it.delete() } }
    }

    private fun fixture(width: Int, height: Int): File =
        File(context.filesDir, "photos/${UUID.randomUUID()}.png").also { write(it, width, height) }

    private fun write(file: File, width: Int, height: Int) {
        file.parentFile!!.mkdirs()
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { source.recycle() }
    }
}
