package com.twomemory.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.sun.net.httpserver.HttpServer
import com.twomemory.database.AlbumCategoryEntity
import com.twomemory.database.AlbumEntity
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumCachedSourceScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun corruptLocalOriginalWithAssetDoesNotRemainVisibleFromWarmDetailPixels() =
        verifyInvalidOriginalWithAsset(byteArrayOf(1, 2, 3))

    @Test fun emptyLocalOriginalWithAssetDoesNotRemainVisibleFromWarmDetailPixels() =
        verifyInvalidOriginalWithAsset(byteArrayOf())

    private fun verifyInvalidOriginalWithAsset(invalidBytes: ByteArray) {
        val file = File(compose.activity.filesDir, "photos/${UUID.randomUUID()}.png")
        writePicture(file)
        try {
            runBlocking { requireNotNull(AlbumPhotoPixels.load(file.absolutePath, true)) }
            file.writeBytes(invalidBytes)
            val photo = AlbumPhotoEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(), file.absolutePath,
                "image/png", 80, 60, 1, 1, assetId = UUID.randomUUID().toString(), cloudKnown = true)
            showPhoto(photo)
            // Wait for real source validation, rather than asserting before the
            // asynchronous loader has had a chance to reject the corrupted file.
            compose.waitUntil(10_000) { AlbumPhotoPixels.cached(file.absolutePath, true) == null }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("相册照片").assertDoesNotExist()
        } finally {
            clearPhoto()
            file.delete()
        }
    }

    @Test fun verifiedRemotePreviewRemainsVisibleWhenOriginalTemporarilyFails() {
        val context = compose.activity
        val prefs = SyncSession.sessionPrefs(context)
        val priorPrefs = prefs.all.toMap()
        val assetId = UUID.randomUUID()
        val originals = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/media/$assetId/data") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val body = "Temporary synthetic outage".toByteArray()
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            originals.incrementAndGet()
        }
        server.start()
        var database: AppDatabase? = null
        var original: File? = null
        var preview: File? = null
        try {
            SyncSession.save(context, "synthetic-album-preview", UUID.randomUUID(), UUID.randomUUID(),
                "http://127.0.0.1:${server.address.port}")
            val session = requireNotNull(SyncSession.load(context))
            val db = AppDatabase.build(context, session.databaseName)
            database = db
            val albumId = UUID.randomUUID().toString()
            val photoId = UUID.randomUUID().toString()
            val source = File(context.filesDir, "cloud_album_cache/synthetic/$photoId.image")
            original = source
            assertFalse(source.exists())
            val photo = AlbumPhotoEntity(photoId, albumId, source.absolutePath,
                "image/png", 80, 60, 1, 1, assetId.toString(), session.userId.toString(), true)
            runBlocking {
                val categoryId = UUID.randomUUID().toString()
                db.albumDao().insertCategory(AlbumCategoryEntity(categoryId, "Synthetic preview", 1))
                db.albumDao().upsertAlbum(AlbumEntity(albumId, "Synthetic album", categoryId, 1,
                    serverUrl = session.storageOrigin, coupleId = session.coupleId.toString(),
                    createdBy = session.userId.toString(), cloudKnown = true))
                db.albumDao().upsertPhoto(photo)
            }
            val previewFile = requireNotNull(AlbumImageLoader.previewFile(context, photo, session))
            preview = previewFile
            writePicture(previewFile)
            runBlocking { requireNotNull(AlbumPhotoPixels.load(previewFile.absolutePath, false)) }
            showPhoto(photo)
            compose.waitUntil(10_000) { originals.get() > 0 }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("相册照片").assertIsDisplayed()
            assertFalse("A preview must not become the original", source.exists())
        } finally {
            clearPhoto()
            server.stop(0)
            original?.delete()
            preview?.delete()
            database?.close()
            val editor = prefs.edit().clear()
            for ((key, value) in priorPrefs) when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            editor.commit()
        }
    }

    private fun writePicture(file: File) {
        file.parentFile!!.mkdirs()
        val image = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }

    private fun showPhoto(photo: AlbumPhotoEntity) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            AlbumLocalPhoto(photo, Modifier.height(220.dp), fit = true)
        } } }
    }

    private fun clearPhoto() {
        compose.activity.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
    }

    @Test fun missingLocalAlbumOriginalDoesNotRemainVisibleFromWarmDetailPixels() {
        val file = File(compose.activity.filesDir, "photos/${UUID.randomUUID()}.png")
        file.parentFile!!.mkdirs()
        val image = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
        runBlocking { requireNotNull(AlbumPhotoPixels.load(file.absolutePath, true)) }
        assertTrue(file.delete())
        val photo = AlbumPhotoEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(), file.absolutePath,
            "image/png", 80, 60, 1, 1)
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            AlbumLocalPhoto(photo, Modifier.height(220.dp), fit = true)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("相册照片").fetchSemanticsNodes().isEmpty() }
    }
}
