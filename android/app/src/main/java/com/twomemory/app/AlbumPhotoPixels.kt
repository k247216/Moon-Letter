package com.twomemory.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.twomemory.database.AlbumImageDecoder
import com.twomemory.designsystem.PhotoPixelCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object AlbumPhotoPixels {
    private fun bucket(detail: Boolean) = if (detail) PhotoPixelCache.Bucket.DETAIL else PhotoPixelCache.Bucket.PREVIEW
    fun cached(path: String, detail: Boolean): ImageBitmap? =
        PhotoPixelCache.get(bucket(detail), PhotoPixelCache.Key("album", path))?.bitmap
    suspend fun load(path: String, detail: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        val cache = bucket(detail)
        val key = PhotoPixelCache.Key("album", path)
        val file = File(path)
        if (!file.isFile || file.length() == 0L) {
            PhotoPixelCache.remove(cache, key)
            return@withContext null
        }
        val version = "${file.length()}:${file.lastModified()}"
        PhotoPixelCache.get(cache, key)?.takeIf { it.version == version }?.let { return@withContext it.bitmap }
        val image = AlbumImageDecoder.decode(path, if (detail) 2048 else 600)?.asImageBitmap()
        if (image != null) PhotoPixelCache.put(cache, key, version, image) else PhotoPixelCache.remove(cache, key)
        image
    }
}
