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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Where a picture this device holds lives, plus what the server needs to store it. */
data class StoredPhoto(val localPath: String, val mimeType: String)

/**
 * Pictures of a record. The author's phone keeps its own copy under filesDir
 * because the photo picker only grants access until the process dies; every
 * other phone reads the copy the server holds.
 */
object EntryPhotos {
    private val downloadMutex = kotlinx.coroutines.sync.Mutex()
    private data class Transport(val scope: String = "unbound", val bytes: (suspend (String) -> ByteArray)? = null,
        val file: (suspend (String, File) -> Unit)? = null, val preview: (suspend (String) -> ByteArray)? = null,
        val reading: (suspend (String) -> ByteArray)? = null)
    private val sourceLock = Any()
    @Volatile private var transport = Transport()
    private val mutableSourceScope = kotlinx.coroutines.flow.MutableStateFlow("unbound")
    internal val sourceScope: kotlinx.coroutines.flow.StateFlow<String> = mutableSourceScope

    /** Persist media originals so replay and navigation never depend on RAM caches. */
    suspend fun localOriginal(context: Context, localPath: String?, assetId: String?): File? =
        originalForSource(context, localPath, assetId, transport)

    private suspend fun originalForSource(context: Context, localPath: String?, assetId: String?, source: Transport): File? =
        withContext(Dispatchers.IO) {
            localPath?.let(::File)?.takeIf { it.isFile && it.length() > 0 }?.let { return@withContext it }
            val id = assetId?.let { runCatching { java.util.UUID.fromString(it).toString() }.getOrNull() }
                ?: return@withContext null
            val directory = File(context.filesDir, if (source.scope == "unbound") "downloaded-media"
                else "downloaded-media/${source.scope}").apply { mkdirs() }
            val target = File(directory, id)
            downloadMutex.lock()
            try {
                if (target.isFile && target.length() > 0) return@withContext target
                val temp = File.createTempFile("download-", ".tmp", directory)
                try {
                    val fileSource = source.file
                    if (fileSource != null) fileSource(id, temp)
                    else {
                        val bytes = source.bytes?.invoke(id)?.takeIf { it.isNotEmpty() } ?: return@withContext null
                        temp.writeBytes(bytes)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    check(temp.length() > 0) { "Media download was empty" }
                    check(temp.renameTo(target)) { "Media cache could not be saved" }
                } finally { temp.delete() }
                target
            } finally { downloadMutex.unlock() }
        }

    /** Installed by the app module: fetches the bytes of one server asset. */
    var remoteBytes: (suspend (assetId: String) -> ByteArray)?
        get() = transport.bytes
        set(value) = synchronized(sourceLock) { transport = transport.copy(bytes = value) }
    var remoteFile: (suspend (assetId: String, destination: File) -> Unit)?
        get() = transport.file
        set(value) = synchronized(sourceLock) { transport = transport.copy(file = value) }
    var remotePreview: (suspend (assetId: String) -> ByteArray)?
        get() = transport.preview
        set(value) = synchronized(sourceLock) { transport = transport.copy(preview = value) }
    var remoteReadingPreview: (suspend (assetId: String) -> ByteArray)?
        get() = transport.reading
        set(value) = synchronized(sourceLock) { transport = transport.copy(reading = value) }

    fun installSource(scope: String, bytes: (suspend (String) -> ByteArray)? = null,
        file: (suspend (String, File) -> Unit)? = null, preview: (suspend (String) -> ByteArray)? = null,
        reading: (suspend (String) -> ByteArray)? = null) {
        val namespace = java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        synchronized(sourceLock) {
            transport = Transport(namespace, bytes, file, preview, reading)
            mutableSourceScope.value = namespace
        }
    }

    private fun bucket(original: Boolean, reading: Boolean) = if (original) PhotoPixelCache.Bucket.DETAIL
        else if (reading) PhotoPixelCache.Bucket.READING else PhotoPixelCache.Bucket.PREVIEW
    private fun pixelKey(scope: String, localPath: String?, assetId: String?) =
        PhotoPixelCache.Key("entry:$scope", if (assetId == null) localPath else null, assetId)

    /** Memory-only lookup; never opens a file or starts a request on the UI thread. */
    fun cached(localPath: String?, assetId: String?, original: Boolean = false, reading: Boolean = false): ImageBitmap? {
        val scope = transport.scope
        val pool = bucket(original, reading)
        val key = pixelKey(scope, localPath, assetId)
        PhotoPixelCache.get(pool, key)?.let { return it.bitmap }
        // Upload acknowledgement adds an asset ID to the same immutable local file.
        // Rebind pixels in RAM only; the normal IO load still validates its version.
        if (assetId != null && localPath != null) {
            val previous = pixelKey(scope, localPath, null)
            PhotoPixelCache.get(pool, previous)?.let {
                PhotoPixelCache.put(pool, key, it.version, it.bitmap)
                PhotoPixelCache.remove(pool, previous)
                return it.bitmap
            }
        }
        return null
    }

    suspend fun store(context: Context, uri: Uri, id: String, maxBytes: Long = 64L*1024*1024): StoredPhoto? =
        withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val directory = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(directory, "$id.${extensionOf(mimeType)}")
            val temporary = File.createTempFile("import-", ".part", directory)
            try {
                val count=context.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output -> copyMediaBounded(input,output,maxBytes) }
                } ?: return@withContext null
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if(count==0L) return@withContext null
                check(temporary.renameTo(file)) { "Media import could not be saved" }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext null }
            finally { temporary.delete() }
            StoredPhoto(file.absolutePath, mimeType)
        }

    /** Local copy first: the writer's phone must not re-download a photo it took. */
    suspend fun load(context: Context, localPath: String?, assetId: String?, original: Boolean = false, reading: Boolean = false): ImageBitmap? {
        val provider = transport
        return withContext(Dispatchers.IO) {
            val downloaded = assetId?.let { value -> runCatching {
                val id = java.util.UUID.fromString(value).toString()
                File(context.filesDir, if (provider.scope == "unbound") "downloaded-media/$id"
                    else "downloaded-media/${provider.scope}/$id")
            }.getOrNull() }
            val local = localPath?.let(::File)?.takeIf { it.isFile }
                ?: downloaded?.takeIf { it.isFile && it.length() > 0 }
            val source = pixelKey(provider.scope, localPath, assetId)
            val cache = bucket(original, reading)
            val target = if (original) 2048 else if (reading) 1200 else 640
            // File validation remains off the UI thread. Local originals normally
            // have immutable UUID paths, but still revalidate replacements/removal.
            val version = (local?.let { "local:${it.absolutePath}:${it.length()}:${it.lastModified()}" }
                ?: assetId?.let { "asset:$it" })?.let { primary ->
                if (downloaded != null && downloaded != local && downloaded.isFile)
                    "$primary|downloaded:${downloaded.length()}:${downloaded.lastModified()}" else primary
            }
            if (version == null) {
                PhotoPixelCache.remove(cache, source)
                return@withContext null
            }
            PhotoPixelCache.get(cache, source)?.takeIf { it.version == version }?.let { return@withContext it.bitmap }
            var bitmap = when {
                local != null -> if (original) decodeFile(local, target)
                    else localPreview(context, local, provider.scope, target)
                assetId != null && original -> originalForSource(context, null, assetId, provider)?.let { decodeFile(it, target) }
                assetId != null -> {
                    val dir = File(context.cacheDir, "entry-previews/${provider.scope}").apply { mkdirs() }
                    val id = java.util.UUID.fromString(assetId).toString()
                    val file = File(dir, if (reading) "$id-reading-v39.jpg" else "$id.jpg")
                    if (!file.isFile || file.length() == 0L) {
                        val source = if (reading) provider.reading ?: provider.preview else provider.preview
                        val bytes = source?.invoke(id)?.takeIf { it.isNotEmpty() } ?: return@withContext null
                        val part = File.createTempFile("preview-", ".part", dir)
                        try { part.writeBytes(bytes); check(part.renameTo(file)) } finally { part.delete() }
                    }
                    decodeFile(file, target)
                }
                else -> null
            }
            if (bitmap == null && local != null && downloaded != null && downloaded != local &&
                downloaded.isFile && downloaded.length() > 0) {
                bitmap = if (original) decodeFile(downloaded, target)
                    else localPreview(context, downloaded, provider.scope, target)
            }
            if (bitmap == null) PhotoPixelCache.remove(cache, source)
            else PhotoPixelCache.put(cache, source, version, bitmap)
            bitmap
        }
    }

    /** Reusable derived pixels only; the source file and its bytes are never modified. */
    private suspend fun localPreview(context: Context, original: File, scope: String, target: Int): ImageBitmap? {
        val fingerprint = "${original.absolutePath}|${original.length()}|${original.lastModified()}|$target"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        val directory = File(context.cacheDir, "entry-previews/$scope").apply { mkdirs() }
        val cached = File(directory, "local-$digest.png")
        if (cached.isFile && cached.length() > 0) decodeFile(cached, target)?.let { return it }
        val pixels = decodeFile(original, target) ?: return null
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // A cache write failure must never hide a readable original.
        var part: File? = null
        try {
            part = File.createTempFile("local-preview-", ".part", directory)
            part.outputStream().use { check(pixels.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            check(part.renameTo(cached))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { /* Disposable disk cache; retain decoded pixels. */ }
        finally { part?.delete() }
        return pixels
    }

    private fun extensionOf(mimeType: String) = when (mimeType.substringBefore(';').trim().lowercase(java.util.Locale.ROOT)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "video/mp4" -> "mp4"
        "video/webm", "audio/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/aac", "audio/aacp" -> "aac"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/opus" -> "opus"
        "audio/3gpp" -> "3gp"
        "audio/amr" -> "amr"
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

    private fun decodeFile(file: File, target: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > target) sample *= 2
        val original = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = runCatching { android.media.ExifInterface(file.absolutePath)
            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val matrix = android.graphics.Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(270f)
            else -> return original.asImageBitmap()
        }
        return android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            .also { if (it !== original) original.recycle() }.asImageBitmap()
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
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 18.dp,
    original: Boolean = false,
    useNaturalAspectRatio: Boolean = true,
    reading: Boolean = false,
) {
    val context = LocalContext.current
    val sourceScope by EntryPhotos.sourceScope.collectAsState()
    var bitmap by remember(context, sourceScope, localPath, assetId, original, reading) {
        mutableStateOf(EntryPhotos.cached(localPath, assetId, original, reading)
            ?: if (original) EntryPhotos.cached(localPath, assetId, reading = true)
                ?: EntryPhotos.cached(localPath, assetId) else null)
    }
    LaunchedEffect(sourceScope, localPath, assetId, original, reading) {
        repeat(3) {
            try {
                val loaded = if (original) loadPhotoStages(
                    // A warm first frame still needs off-thread validation. A
                    // missing local source is not a temporarily slow original.
                    preview = { EntryPhotos.load(context, localPath, assetId).also {
                        if (it == null) bitmap = null
                    } },
                    original = { EntryPhotos.load(context, localPath, assetId, true) },
                    showPreview = { preview ->
                        val current = bitmap
                        if (current == null || maxOf(preview.width, preview.height) >= maxOf(current.width, current.height)) bitmap = preview
                    },
                ) else EntryPhotos.load(context, localPath, assetId, reading = reading)
                if (loaded != null) { bitmap = loaded; return@LaunchedEffect }
                if (!original) bitmap = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* Retain preview while original is still unavailable. */ }
            kotlinx.coroutines.delay(2_000)
        }
    }
    val ratio = bitmap?.let { it.width.toFloat() / it.height.toFloat() }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!useNaturalAspectRatio) Modifier else if (ratio == null) Modifier.height(180.dp) else
                Modifier.layout { measurable, constraints ->
                    // Respect the caller's cap instead of painting a natural-sized
                    // bitmap outside a fixed page slot. Panoramas retain their ratio.
                    val size = if (constraints.hasBoundedWidth) {
                        val height = (constraints.maxWidth / ratio).roundToInt().coerceAtLeast(1)
                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                        constraints.copy(minHeight = height, maxHeight = height)
                    } else constraints
                    val placeable = measurable.measure(size)
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                })
            .clip(shape)
            .background(if (bitmap == null) TwoMemoryColors.WarmBeigeLine.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("照片", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
    }
}
