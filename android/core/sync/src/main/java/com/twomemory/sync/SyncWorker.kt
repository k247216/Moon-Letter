package com.twomemory.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.UUID
import java.util.concurrent.TimeUnit

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
        engine.pushPending()
        val pull = engine.pullAll()
        // A 401 means re-pair is needed; retrying blindly would not help.
        if (pull.needsRePair) return Result.failure()
        return if (pull.failed != null) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_COUPLE_ID = "couple_id"

        /**
         * Best-effort background sync: needs network, exponential backoff.
         * Real-time delivery comes from app start / foreground return /
         * manual refresh, never promised from here.
         */
        fun enqueue(context: Context, coupleId: UUID) {
            val work = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(coupleId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "moon-letter-sync-$coupleId", ExistingWorkPolicy.REPLACE, work,
            )
        }

        private fun workDataOf(coupleId: UUID) =
            androidx.work.workDataOf(KEY_COUPLE_ID to coupleId.toString())
    }
}
