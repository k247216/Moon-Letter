package com.twomemory.app

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.twomemory.database.AlbumPhotoEntity
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Copies the private original; never decodes, re-encodes, uploads or alters album data. */
internal class AlbumPhotoExporter(
    private val context: Context,
    private val storage: AlbumExportStorage = AndroidAlbumExportStorage(context.applicationContext.contentResolver),
) {
    fun createDocumentIntent(photo: AlbumPhotoEntity): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = normalizedMime(photo)
        putExtra(Intent.EXTRA_TITLE, displayName(photo))
    }

    suspend fun saveToMediaStore(photo: AlbumPhotoEntity): Uri {
        check(Build.VERSION.SDK_INT >= 29) { "Use the system document picker on Android 8–9" }
        var inserted: Uri? = null
        try {
            return withContext(Dispatchers.IO) {
                if (photo.assetId != null) AlbumImageLoader.load(context, photo, original = true)
                val source = readableSource(photo)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName(photo))
                    put(MediaStore.Images.Media.MIME_TYPE, normalizedMime(photo))
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MoonLetter/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                currentCoroutineContext().ensureActive()
                val uri = storage.insertImage(values) ?: throw IOException("Cannot create image")
                inserted = uri
                copyOriginal(source, uri)
                currentCoroutineContext().ensureActive()
                if (storage.publishImage(uri) != 1) throw IOException("Cannot publish image")
                uri
            }
        } catch (error: Exception) {
            // Includes cancellation at the IO dispatcher return boundary, even after publication.
            inserted?.let { uri -> cleanup(error) { storage.deleteImage(uri) } }
            throw error
        }
    }

    /** destination must be the NEW document returned by ACTION_CREATE_DOCUMENT, never an opened file. */
    suspend fun saveToDocument(photo: AlbumPhotoEntity, destination: Uri): Uri {
        require(destination.scheme == ContentResolver.SCHEME_CONTENT) { "Expected a newly created document" }
        try {
            return withContext(Dispatchers.IO) {
                if (photo.assetId != null) AlbumImageLoader.load(context, photo, original = true)
                copyOriginal(readableSource(photo), destination)
                destination
            }
        } catch (error: Exception) {
            cleanup(error) { storage.deleteDocument(destination) }
            throw error
        }
    }

    private fun readableSource(photo: AlbumPhotoEntity): File {
        val source = File(photo.localPath)
        if (!source.isFile || !source.canRead()) throw FileNotFoundException("Album original is unavailable")
        if (source.length() <= 0) throw IOException("Album original is empty")
        return source
    }

    private suspend fun copyOriginal(source: File, destination: Uri) {
        currentCoroutineContext().ensureActive()
        val expectedSize = source.length()
        source.inputStream().use { input ->
            val stream = storage.openOutput(destination) ?: throw IOException("Cannot open destination")
            stream.use { output ->
                val buffer = ByteArray(32 * 1024)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                }
                currentCoroutineContext().ensureActive()
                if (copied == 0L || copied != expectedSize) throw IOException("Incomplete original copy")
                output.flush()
            }
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun cleanup(error: Exception, delete: () -> Unit) {
        withContext(NonCancellable + Dispatchers.IO) {
            try { delete() } catch (cleanupError: Exception) { error.addSuppressed(cleanupError) }
        }
    }

    private fun normalizedMime(photo: AlbumPhotoEntity): String {
        val mime = photo.mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        require(mime.startsWith("image/") && mime != "image/*") { "Unsupported photo format" }
        return if (mime == "image/jpg") "image/jpeg" else mime
    }

    private fun displayName(photo: AlbumPhotoEntity): String {
        val mime = normalizedMime(photo)
        val extension = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: throw IllegalArgumentException("Unsupported photo format")
        }
        return "MoonLetter_${UUID.randomUUID()}.$extension"
    }
}

/** Destination boundary permits deterministic write/publish faults without replacing source IO. */
internal interface AlbumExportStorage {
    fun insertImage(values: ContentValues): Uri?
    fun openOutput(uri: Uri): OutputStream?
    fun publishImage(uri: Uri): Int
    fun deleteImage(uri: Uri)
    fun deleteDocument(uri: Uri)
}

internal class AndroidAlbumExportStorage(private val resolver: ContentResolver) : AlbumExportStorage {
    override fun insertImage(values: ContentValues): Uri? = resolver.insert(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
    )
    override fun openOutput(uri: Uri): OutputStream? = resolver.openOutputStream(uri, "wt")
    override fun publishImage(uri: Uri): Int = resolver.update(uri, ContentValues().apply {
        put(MediaStore.Images.Media.IS_PENDING, 0)
    }, null, null)
    override fun deleteImage(uri: Uri) {
        if (resolver.delete(uri, null, null) != 1) throw IOException("Cannot remove incomplete image")
    }
    override fun deleteDocument(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) throw IOException("Cannot remove incomplete document")
    }
}
