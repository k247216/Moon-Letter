package com.twomemory.app

import android.content.Context
import androidx.room.withTransaction
import com.twomemory.database.*
import com.twomemory.network.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Independent album queue. Local rows/files are durable; all HTTP acknowledgements are explicit. */
object AlbumCloudSync {
    private val mutex = Mutex()
    internal suspend fun <T> withPaused(block: suspend () -> T): T = mutex.withLock { block() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scheduled: Job? = null

    @Synchronized fun schedule(context: Context) {
        if (scheduled?.isActive == true) return
        val session = SyncSession.load(context) ?: return
        val app = context.applicationContext
        scheduled = scope.launch {
            try { sync(app, AppDatabase.build(app, session.databaseName), session) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Persisted rows retry on the next foreground/worker cycle. */ }
            finally { if (hasPending(AppDatabase.build(app, session.databaseName), session)) enqueue(app) }
        }
    }

    fun enqueue(context: Context) {
        val session = SyncSession.load(context) ?: return
        val work = OneTimeWorkRequestBuilder<AlbumSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf("space" to session.coupleId.toString(), "server" to session.baseUrl.trimEnd('/')))
            .build()
        val originKey = digest(session.baseUrl.trimEnd('/').toByteArray()).take(24)
        WorkManager.getInstance(context).enqueueUniqueWork("moon-letter-album-$originKey-${session.coupleId}", ExistingWorkPolicy.KEEP, work)
    }

    suspend fun hasPending(database: AppDatabase, session: SyncSession.Session): Boolean = database.albumDao().allAlbums()
        .filter { it.album.coupleId == session.coupleId.toString() && it.album.serverUrl == session.storageOrigin }
        .any { row ->
            val album = row.album
            if (album.deleted) album.cloudKnown && album.createdBy == session.userId.toString()
            else !album.cloudKnown || album.coverDirty || row.photos.any { photo ->
                if (photo.deleted) photo.cloudKnown && (photo.addedBy == null || photo.addedBy == session.userId.toString())
                else !photo.cloudKnown
            }
        }

    /** Also used by the record worker: a successful partial cycle still owes a retry. */
    internal suspend fun syncAndContinue(context: Context, database: AppDatabase, session: SyncSession.Session,
        media: MediaApi = RetrofitMediaApi.create(), bindingValid: () -> Boolean = { SyncSession.load(context) == session },
        onPending: () -> Unit = { enqueue(context) }) {
        try {
            sync(context, database, session, media, bindingValid)
            if (hasPending(database, session)) onPending()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { onPending() }
    }

    suspend fun sync(context: Context, database: AppDatabase, session: SyncSession.Session,
        media: MediaApi = RetrofitMediaApi.create(), bindingValid: () -> Boolean = { SyncSession.load(context) == session }) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!bindingValid()) return@withLock
            fun ensureBound() { if (!bindingValid()) throw CancellationException("Space binding changed during album transfer") }
            val origin = session.storageOrigin
            val transport = session.baseUrl.trimEnd('/')
            val space = session.coupleId.toString()
            val owner = session.userId.toString()
            val dao = database.albumDao()
            val api = SharedAlbumApi(transport, session.token, session.coupleId)
            // Read before pushing: a stale offline phone must learn remote deletion first.
            val snapshot = api.read()
            ensureBound()
            dao.claimUnbound(origin, space, owner)
            merge(context, database, session, snapshot)
            fun scoped(row: AlbumWithPhotos) = row.album.serverUrl == origin && row.album.coupleId == space
            var uploadBudget = 8
            // A failed response can arrive after the server applied a mutation.
            // Refresh after attempts, not just successful acknowledgements.
            var remoteMutationAttempted = false
            for (row in dao.allAlbums().filter(::scoped)) {
                currentCoroutineContext().ensureActive()
                ensureBound()
                try {
                    val album = row.album
                    val albumId = UUID.fromString(album.id)
                    if (album.deleted) {
                        if (album.cloudKnown && album.createdBy == owner) {
                            remoteMutationAttempted = true
                            api.deleteAlbum(albumId)
                        }
                        continue
                    }
                    if (!album.cloudKnown) {
                        if (album.createdBy != owner) continue
                        val category = dao.observeCategories().first().first { it.id == album.categoryId }.name
                        ensureBound()
                        remoteMutationAttempted = true
                        api.saveAlbum(albumId, album.name, category)
                        ensureBound()
                        dao.acknowledgeAlbum(album.id)
                    }
                    for (photo in row.photos) {
                        ensureBound()
                        if (photo.addedBy != null && photo.addedBy != owner) continue
                        try {
                            if (photo.deleted) {
                                if (photo.cloudKnown) {
                                    remoteMutationAttempted = true
                                    api.deletePhoto(albumId, UUID.fromString(photo.id))
                                }
                                continue
                            }
                            if (photo.cloudKnown) continue
                            if (uploadBudget <= 0) continue
                            var assetId = photo.assetId
                            if (assetId == null) {
                                val file = File(photo.localPath)
                                if (!file.isFile) continue
                                val hash = fileSha256(file)
                                remoteMutationAttempted = true
                                val ticket = media.createUpload(transport, session.token, MediaCreateCommand(
                                    session.userId, session.coupleId, UUID.fromString(photo.id), "IMAGE",
                                    photo.mimeType, file.length(), photo.width, photo.height, hash, purpose = "ALBUM"))
                                if (ticket.url.startsWith("local://"))
                                    media.uploadFileData(transport, session.token, ticket.assetId, file, photo.mimeType)
                                else {
                                    media.uploadFileToUrl(ticket.url, file, photo.mimeType)
                                    media.completeUpload(transport, session.token, ticket.assetId, hash)
                                }
                                assetId = ticket.assetId.toString()
                                ensureBound()
                                dao.attachPhotoAsset(photo.id, assetId, owner)
                            }
                            // Recheck a photo deleted while its bytes were uploading.
                            if (dao.findPhoto(photo.id)?.deleted == true) continue
                            ensureBound()
                            remoteMutationAttempted = true
                            api.addPhoto(albumId, UUID.fromString(photo.id), UUID.fromString(assetId), photo.takenAtEpochMillis)
                            ensureBound()
                            dao.acknowledgePhoto(photo.id)
                            uploadBudget-- // Failed rows must not starve healthy rows behind them.
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* A single bad upload cannot hold incoming photos hostage. */ }
                    }
                    val current = dao.findAlbum(album.id) ?: continue
                    val cover = current.coverPhotoId?.let { dao.findPhoto(it) }
                    if (!current.deleted && current.coverDirty && cover?.cloudKnown == true && !cover.deleted && cover.assetId != null) {
                        remoteMutationAttempted = true
                        api.setCover(albumId, UUID.fromString(cover.assetId))
                        ensureBound()
                        dao.acknowledgeCover(album.id, cover.id)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Another album can still progress. */ }
            }
            ensureBound()
            if (remoteMutationAttempted) {
                val after = api.read()
                ensureBound()
                merge(context, database, session, after)
            }
            // Images are requested by visible cells/viewer, not eagerly behind metadata synchronization.
        }
    }

    private suspend fun merge(context: Context, db: AppDatabase, session: SyncSession.Session, snapshot: List<CloudAlbum>) {
        val dao = db.albumDao()
        val origin = session.storageOrigin
        val cacheScope = digest("$origin|${session.coupleId}".toByteArray()).take(24)
        db.withTransaction {
            for (remote in snapshot) {
                val id = remote.id.toString()
                val local = dao.findAlbum(id)
                // Server UUID collisions must not overwrite albums bound to a different backend.
                if (local != null && (local.serverUrl != origin || local.coupleId != session.coupleId.toString())) continue
                val category = dao.findCategory(remote.category) ?: run {
                    dao.insertCategory(AlbumCategoryEntity(UUID.randomUUID().toString(), remote.category, remote.createdAtEpochMillis))
                    requireNotNull(dao.findCategory(remote.category))
                }
                val cover = remote.photos.firstOrNull { !it.deleted && it.assetId == remote.coverAssetId }?.id?.toString()
                val keepCoverIntent = local?.coverDirty == true && !remote.deleted &&
                    remote.photos.none { it.id.toString() == local.coverPhotoId && it.deleted }
                val mergedAlbum = AlbumEntity(id, remote.name, category.id, remote.createdAtEpochMillis,
                    if (keepCoverIntent) local?.coverPhotoId else cover, origin, session.coupleId.toString(),
                    remote.createdBy.toString(), !remote.deleted, remote.deleted || local?.deleted == true,
                    keepCoverIntent)
                if (mergedAlbum != local) dao.upsertAlbum(mergedAlbum)
                for (photo in remote.photos) {
                    val old = dao.findPhoto(photo.id.toString())
                    if (old != null && old.albumId != id) continue
                    val file = File(context.filesDir, "cloud_album_cache/$cacheScope/$id/${photo.id}.image")
                    val path = old?.localPath?.takeIf { File(it).isFile } ?: file.absolutePath
                    val mergedPhoto = AlbumPhotoEntity(photo.id.toString(), id, path,
                        photo.mimeType, photo.width, photo.height, photo.takenAtEpochMillis,
                        old?.importedAtEpochMillis ?: remote.createdAtEpochMillis, photo.assetId.toString(),
                        photo.addedBy.toString(), !photo.deleted, photo.deleted || old?.deleted == true)
                    if (mergedPhoto != old) dao.upsertPhoto(mergedPhoto)
                }
            }
        }
    }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

class AlbumSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = SyncSession.load(applicationContext) ?: return Result.success()
        if (inputData.getString("space") != session.coupleId.toString()) return Result.success()
        // An old-origin worker still owes the same space its pending photos.
        // Startup hooks also reconcile work if death occurred before re-enqueue.
        if (inputData.getString("server") != session.baseUrl.trimEnd('/')) {
            AlbumCloudSync.enqueue(applicationContext)
            return Result.success()
        }
        return try {
            val db = AppDatabase.build(applicationContext, session.databaseName)
            AlbumCloudSync.sync(applicationContext, db, session)
            if (AlbumCloudSync.hasPending(db, session)) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
