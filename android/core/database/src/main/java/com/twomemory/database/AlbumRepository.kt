package com.twomemory.database

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

data class AlbumImportFailure(val source: Uri, val reason: String)
data class AlbumImportReport(val importedCount: Int, val failures: List<AlbumImportFailure>)

/** Owns only the dedicated album tables and filesDir/independent_albums. No sync is implied. */
class AlbumRepository(private val database: AppDatabase, context: Context, private val scope: AlbumScope? = null) {
    private val context = context.applicationContext
    private val dao = database.albumDao()
    fun observeAlbums() = dao.observeAlbums().map { albums ->
        albums.filter { visible(it.album) }.map { it.copy(photos = it.photos.filterNot { photo -> photo.deleted }) }
    }
    fun observeCategories() = dao.observeCategories()

    suspend fun createAlbum(name: String, categoryName: String): String {
        val cleanName = name.trim()
        val cleanCategory = categoryName.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) { "相册名称需要 1–80 个字" }
        require(cleanCategory.isNotEmpty() && cleanCategory.length <= 40) { "分类名称需要 1–40 个字" }
        return database.withTransaction {
            val now = System.currentTimeMillis()
            dao.insertCategory(AlbumCategoryEntity(UUID.randomUUID().toString(), cleanCategory, now))
            val category = requireNotNull(dao.findCategory(cleanCategory))
            val album = AlbumEntity(UUID.randomUUID().toString(), cleanName, category.id, now,
                serverUrl = scope?.serverUrl?.trimEnd('/'), coupleId = scope?.coupleId, createdBy = scope?.userId)
            dao.insertAlbum(album)
            album.id
        }
    }

    suspend fun setCover(albumId: String, photoId: String): Boolean {
        require(dao.findAlbum(albumId)?.let(::visible) == true) { "相册不属于当前空间" }
        return dao.setCover(albumId, photoId) == 1
    }

    private fun visible(album: AlbumEntity): Boolean = scope == null || album.coupleId == null ||
        (album.coupleId == scope.coupleId && album.serverUrl == scope.serverUrl.trimEnd('/'))

    suspend fun deleteAlbum(id: String) = database.withTransaction {
        val album = requireNotNull(dao.findAlbum(id))
        require(visible(album) && (album.createdBy == null || album.createdBy == scope?.userId)) { "只有创建者可以删除整本相册" }
        dao.deleteAlbum(id)
    }

    suspend fun deletePhoto(id: String) = database.withTransaction {
        val photo = requireNotNull(dao.findPhoto(id))
        val album = requireNotNull(dao.findAlbum(photo.albumId))
        require(visible(album) && (photo.addedBy == null || photo.addedBy == scope?.userId)) { "只能删除自己添加的照片" }
        dao.deletePhoto(id)
        if (album.coverPhotoId == id) {
            val next = dao.allAlbums().first { it.album.id == album.id }.orderedPhotos.firstOrNull()?.id
            dao.upsertAlbum(album.copy(coverPhotoId = next, coverDirty = next != null))
        }
    }

    /**
     * Each photo is copied, flushed, validated and committed separately. A failed source cannot
     * discard earlier successes. The short commit boundary completes even if the screen leaves,
     * preventing cancellation between a committed row and cleanup of its private file.
     */
    suspend fun importPhotos(albumId: String, sources: List<Uri>): AlbumImportReport {
        require(dao.findAlbum(albumId)?.let { visible(it) && !it.deleted } == true) { "相册不存在，请重新打开相册" }
        var imported = 0
        val failures = mutableListOf<AlbumImportFailure>()
        for (source in sources.distinct()) {
            currentCoroutineContext().ensureActive()
            try {
                withContext(Dispatchers.IO + NonCancellable) { importOne(albumId, source) }
                imported++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += AlbumImportFailure(source, when (error) {
                    is SecurityException -> "无法读取这张照片，请重新选择"
                    is IllegalArgumentException -> error.message ?: "照片无法读取"
                    else -> "照片保存失败，请检查可用空间后重试"
                })
            }
        }
        return AlbumImportReport(imported, failures)
    }

    private suspend fun importOne(albumId: String, source: Uri) {
        // Album ids originate in our database; never let a caller turn one into a filesystem path.
        require(UUID.fromString(albumId).toString() == albumId) { "无效的相册" }
        val directory = File(context.filesDir, "independent_albums/$albumId")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create album directory")
        val id = UUID.randomUUID().toString()
        val partial = File(directory, "$id.part")
        val destination = File(directory, "$id.image")
        var committed = false
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IllegalArgumentException("无法读取这张照片，请重新选择")
            input.use { stream ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val size = stream.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= 64L * 1024 * 1024) { "照片超过 64 MB，请选择较小的图片" }
                        output.write(buffer, 0, size)
                    }
                    output.fd.sync()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(partial.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true) {
                "这张照片无法解码，请选择其他图片"
            }
            // Check that this isn't just a valid header followed by a broken image.
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            val check = BitmapFactory.decodeFile(partial.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            requireNotNull(check) { "这张照片已损坏，请选择其他图片" }
            check.recycle()
            if (!partial.renameTo(destination)) throw IOException("Cannot finish photo copy")
            val now = System.currentTimeMillis()
            val (width, height) = AlbumImageDecoder.displayDimensions(destination.absolutePath, bounds.outWidth, bounds.outHeight)
            dao.addPhoto(AlbumPhotoEntity(
                id, albumId, destination.absolutePath, bounds.outMimeType!!,
                width, height, takenAt(source) ?: now, now, addedBy = scope?.userId,
            ))
            committed = true
        } finally {
            partial.delete()
            if (!committed) destination.delete()
        }
    }

    private fun takenAt(source: Uri): Long? = runCatching {
        context.contentResolver.query(source, arrayOf(MediaStore.Images.ImageColumns.DATE_TAKEN), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column).takeIf { it > 0 } else null
        }
    }.getOrNull()
}
