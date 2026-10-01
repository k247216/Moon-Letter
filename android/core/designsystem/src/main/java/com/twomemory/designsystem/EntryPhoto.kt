package com.twomemory.designsystem

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Where a picture this device holds lives, plus what the server needs to store it. */
data class StoredPhoto(val localPath: String, val mimeType: String)

/**
 * Pictures of a record. The author's phone keeps its own copy under filesDir
 * because the photo picker only grants access until the process dies; every
 * other phone reads the copy the server holds.
 */
object EntryPhotos {

    /** Installed by the app module: fetches the bytes of one server asset. */
    var remoteBytes: (suspend (assetId: String) -> ByteArray)? = null

    private val cache = android.util.LruCache<String, ImageBitmap>(8)

    suspend fun store(context: Context, uri: Uri, id: String): StoredPhoto? =
        withContext(Dispatchers.IO) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext null
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val directory = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(directory, "$id.${extensionOf(mimeType)}")
            runCatching { file.writeBytes(bytes) }.getOrNull() ?: return@withContext null
            StoredPhoto(file.absolutePath, mimeType)
        }

    /** Local copy first: the writer's phone must not re-download a photo it took. */
    suspend fun load(context: Context, localPath: String?, assetId: String?): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val local = localPath?.let(::File)?.takeIf { it.isFile }
            val key = local?.absolutePath ?: assetId?.let { "asset:$it" } ?: return@withContext null
            cache.get(key)?.let { return@withContext it }
            val bytes = when {
                local != null -> runCatching { local.readBytes() }.getOrNull()
                assetId != null -> runCatching { remoteBytes?.invoke(assetId) }.getOrNull()
                else -> null
            } ?: return@withContext null
            decode(bytes)?.also { cache.put(key, it) }
        }

    private fun extensionOf(mimeType: String) = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        else -> "jpg"
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > 1440) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = max(1, sampleSize) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}

/**
 * One picture of a record: the local file when this device has it, the server
 * asset otherwise. A photo still on its way shows a paper-coloured slot instead
 * of a hole in the page.
 */
@Composable
fun EntryPhoto(
    localPath: String?,
    assetId: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "记录里的照片",
) {
    val context = LocalContext.current
    var bitmap by remember(localPath, assetId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(localPath, assetId) {
        bitmap = EntryPhotos.load(context, localPath, assetId)
    }
    val ratio = bitmap?.let { it.width.toFloat() / it.height.toFloat() }?.coerceIn(0.5f, 2.5f)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ratio == null) Modifier.height(180.dp) else Modifier.aspectRatio(ratio))
            .then(modifier)
            .clip(shape)
            .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("照片", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
    }
}
