package com.twomemory.app

import android.content.Context
import androidx.work.*
import com.twomemory.database.AppDatabase
import com.twomemory.sync.SyncWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Media owns its own durable lane; no record-cycle or foreground lock is held during an upload. */
internal object RecordMediaSync {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scheduled: Job? = null
    internal suspend fun <T> withPaused(block: suspend () -> T): T = mutex.withLock { block() }

    @Synchronized fun schedule(context: Context) {
        enqueue(context)
        if (scheduled?.isActive == true) return
        val app = context.applicationContext
        scheduled = scope.launch {
            try { sync(app) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Durable worker retries after reconnect. */ }
        }
    }

    fun enqueue(context: Context) {
        val session = SyncSession.load(context) ?: return
        val work = OneTimeWorkRequestBuilder<RecordMediaWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf("database" to session.databaseName, "space" to session.coupleId.toString(),
                "user" to session.userId.toString()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("moon-letter-media-${session.databaseName}", ExistingWorkPolicy.KEEP, work)
    }

    internal suspend fun sync(context: Context,
        upload: suspend (SyncSession.Session, AppDatabase) -> Unit = { session, db ->
            MediaUploadManager.uploadPendingImages(session, db)
        }): Boolean = mutex.withLock {
        val session = SyncSession.load(context) ?: return@withLock false
        val db = AppDatabase.build(context, session.databaseName)
        val before = db.entryDao().mediaBlocksWithoutAsset().map { it.id }.toSet()
        upload(session, db)
        currentCoroutineContext().ensureActive()
        // Never enqueue an old-space text pipeline after a binding has changed.
        if (SyncSession.load(context) != session) return@withLock false
        val remaining = db.entryDao().mediaBlocksWithoutAsset()
        if (remaining.map { it.id }.toSet() != before) SyncWorker.enqueue(context, session.coupleId)
        try { ProfileMediaSync.uploadOwn(context, session) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock true }
        remaining.isNotEmpty()
    }
}

internal class RecordMediaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = SyncSession.load(applicationContext) ?: return Result.success()
        if (inputData.getString("database") != session.databaseName ||
            inputData.getString("space") != session.coupleId.toString() ||
            inputData.getString("user") != session.userId.toString()) return Result.success()
        return try { if (RecordMediaSync.sync(applicationContext)) Result.retry() else Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
