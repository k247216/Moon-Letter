package com.twomemory.app

import android.content.Context
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AppDatabase
import com.twomemory.network.MediaApi
import com.twomemory.network.MediaDownloadException
import com.twomemory.network.RetrofitMediaApi
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Viewport-driven previews and explicit original fetches. Never stores a preview at localPath. */
internal object AlbumImageLoader {
    private val previewSlots = Semaphore(2)
    private val originalSlots = Semaphore(1)
    private val locks = Array(64) { Mutex() }

    /** Path calculation only; a caller may peek at RAM, but disk/network access stays in load. */
    fun previewFile(context: Context, photo: AlbumPhotoEntity, session: SyncSession.Session?): File? {
        val active = session ?: return null
        val id = photo.assetId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val scope = MessageDigest.getInstance("SHA-256").digest("${active.storageOrigin}|${active.coupleId}".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        return File(context.cacheDir, "album_previews/$scope/$id.jpg")
    }

    suspend fun load(context: Context, photo: AlbumPhotoEntity, original: Boolean,
        session: SyncSession.Session? = SyncSession.load(context),
        database: AppDatabase? = null, media: MediaApi = RetrofitMediaApi.create()): File = withContext(Dispatchers.IO) {
        val source = File(photo.localPath)
        val preview = if (!original) previewFile(context, photo, session)?.takeIf { it.isFile && it.length() > 0 } else null
        if (preview == null && source.isFile && source.length() > 0) return@withContext source
        val active = requireNotNull(session) { "请先连接云端空间" }
        val asset = UUID.fromString(requireNotNull(photo.assetId) { "照片文件不存在" })
        val db = database ?: AppDatabase.build(context, active.databaseName)
        val album = requireNotNull(db.albumDao().findAlbum(photo.albumId))
        require(!album.deleted && album.coupleId == active.coupleId.toString() && album.serverUrl == active.storageOrigin) {
            "照片不属于当前云端空间"
        }
        val currentPhoto = db.albumDao().findPhoto(photo.id)
        require(currentPhoto != null && !currentPhoto.deleted && currentPhoto.albumId == photo.albumId &&
            currentPhoto.assetId == photo.assetId) { "照片已被移除或引用已更新" }
        if (preview != null) return@withContext preview
        val destination = if (original) source else requireNotNull(previewFile(context, photo, active))
        val root = if (original) File(context.filesDir, "cloud_album_cache") else File(context.cacheDir, "album_previews")
        require(destination.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
        val lock = locks[(destination.absolutePath.hashCode() and Int.MAX_VALUE) % locks.size]
        lock.withLock {
            if (destination.isFile && destination.length() > 0) return@withLock destination
            (if (original) originalSlots else previewSlots).withPermit {
                currentCoroutineContext().ensureActive()
                destination.parentFile?.mkdirs()
                val part = File(destination.parentFile, "${destination.name}.part")
                try {
                    if (original) media.downloadToFile(active.baseUrl, active.token, asset, part)
                    else {
                        val bytes = media.downloadPreview(active.baseUrl, active.token, asset)
                        require(bytes.isNotEmpty()) { "照片下载为空" }
                        FileOutputStream(part).use { it.write(bytes); it.fd.sync() }
                    }
                    currentCoroutineContext().ensureActive()
                    val current = db.albumDao().findPhoto(photo.id)
                    require(current != null && !current.deleted && current.assetId == photo.assetId) { "照片已被移除" }
                    check(part.renameTo(destination)) { "照片缓存写入失败" }
                } finally { part.delete() }
                destination
            }
        }
    }
}
