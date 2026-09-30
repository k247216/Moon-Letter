package com.twomemory.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID

fun interface SyncEngineFactory {
    fun create(coupleId: UUID): SyncEngine
}

object SyncEngineRegistry {
    lateinit var factory: SyncEngineFactory
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val coupleId = inputData.getString(KEY_COUPLE_ID)?.let(UUID::fromString) ?: return Result.failure()
        val engine = SyncEngineRegistry.factory.create(coupleId)
        val push = engine.pushPending()
        val pull = engine.pullAfter(inputData.getLong(KEY_CURSOR, 0L))
        return if (pull.failed != null) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_COUPLE_ID = "couple_id"
        const val KEY_CURSOR = "cursor"
        fun enqueue(context: Context, coupleId: UUID) {
            val work = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_COUPLE_ID to coupleId.toString()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "moon-letter-sync-$coupleId", ExistingWorkPolicy.KEEP, work,
            )
        }
    }
}
