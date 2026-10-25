package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.system.Os
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.twomemory.designsystem.PhotoPixelCache
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DecorBitmapState(val bitmap: ImageBitmap?, val ready: Boolean)

object DecorBitmapLoader {
    private fun target(maxEdge: Int) = maxEdge.coerceAtMost(1280)
    private fun bucket(edge: Int) = if (edge <= 192) PhotoPixelCache.Bucket.PREVIEW else PhotoPixelCache.Bucket.READING
    private fun key(uri: String, edge: Int, scope: String) = PhotoPixelCache.Key("decor:$scope", uri, edge.toString())

    /** Memory only: file validity is checked separately on IO before publishing ready. */
    internal fun peek(uri: String?, maxEdge: Int, scope: String): ImageBitmap? {
        if (uri == null || maxEdge <= 0) return null
        val edge = target(maxEdge)
        return PhotoPixelCache.get(bucket(edge), key(uri, edge, scope))?.bitmap
    }

    suspend fun load(context: Context, uri: String?, maxEdge: Int, scope: String): ImageBitmap? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (uri.isNullOrBlank() || maxEdge <= 0) return@withContext null
        val edge = target(maxEdge)
        val pool = bucket(edge)
        val source = key(uri, edge, scope)
        var owned: Bitmap? = null
        try {
            val parsed = Uri.parse(uri)
            val version = version(context, parsed)
            currentCoroutineContext().ensureActive()
            PhotoPixelCache.get(pool, source)?.takeIf { version != null && it.version == version }?.let {
                return@withContext it.bitmap
            }
            PhotoPixelCache.remove(pool, source)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds); Unit }
                ?: return@withContext null
            currentCoroutineContext().ensureActive()
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight).toLong()
            while (longest > edge.toLong() * sample && sample <= Int.MAX_VALUE / 2) sample *= 2
            val orientation = context.contentResolver.openInputStream(parsed)?.use {
                try { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
            } ?: ExifInterface.ORIENTATION_NORMAL
            currentCoroutineContext().ensureActive()
            val decoded = context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            } ?: return@withContext null
            owned = decoded
            currentCoroutineContext().ensureActive()
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            var upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0,
                decoded.width, decoded.height, matrix, true).also {
                owned = it
                if (it !== decoded) decoded.recycle()
            }
            val actualEdge = maxOf(upright.width, upright.height)
            if (actualEdge > edge) {
                val resized = Bitmap.createScaledBitmap(upright,
                    (upright.width.toLong() * edge / actualEdge).toInt().coerceAtLeast(1),
                    (upright.height.toLong() * edge / actualEdge).toInt().coerceAtLeast(1), true)
                owned = resized
                if (resized !== upright) upright.recycle()
                upright = resized
            }
            currentCoroutineContext().ensureActive()
            if (version != null && version(context, parsed) != version) {
                PhotoPixelCache.remove(pool, source)
                return@withContext null
            }
            currentCoroutineContext().ensureActive()
            val pixels = upright.asImageBitmap()
            if (version != null) PhotoPixelCache.put(pool, source, version, pixels)
            owned = null // Shared/visible pixels must never be recycled on eviction.
            pixels
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PhotoPixelCache.remove(pool, source)
            null
        } finally {
            owned?.recycle()
        }
    }

    /** Metadata and descriptor validation happen only inside the IO load. */
    private fun version(context: Context, uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf { it.isFile && it.length() > 0 }?.let {
            "file:${it.lastModified()}:${it.length()}"
        }
        "content", "android.resource" -> try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                val stat = Os.fstat(it.fileDescriptor)
                if (!android.system.OsConstants.S_ISREG(stat.st_mode)) null
                else "descriptor:${stat.st_dev}:${stat.st_ino}:${stat.st_size}:${stat.st_mtime}"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } // Unknown provider version: decode, never cache.
        else -> null
    }
}

@Composable
fun rememberDecorBitmap(uri: String?, maxEdge: Int, scope: String): DecorBitmapState {
    val context = LocalContext.current
    val state = remember(context, uri, maxEdge, scope) {
        mutableStateOf(DecorBitmapState(DecorBitmapLoader.peek(uri, maxEdge, scope), ready = uri == null))
    }
    LaunchedEffect(context, uri, maxEdge, scope) {
        val pixels = DecorBitmapLoader.load(context, uri, maxEdge, scope)
        currentCoroutineContext().ensureActive()
        state.value = DecorBitmapState(pixels, ready = true)
    }
    return state.value
}
