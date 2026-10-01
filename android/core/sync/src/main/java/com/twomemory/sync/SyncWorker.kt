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

    /**
     * Runs inside the worker after a cycle that pulled changes, before WorkManager
     * is allowed to consider the job done. The app owns what happens next (a
     * notice); sync stays free of it and a no-op is the default.
     */
    var onCycleCompleted: suspend () -> Unit = { }
}

/** Outcome of one sync cycle; drives the WorkManager retry decision. */
enum class SyncOutcome { SUCCESS, RETRY, FAILURE }

/**
 * One push+pull cycle, independent of WorkManager so the retry policy is
 * unit-testable: a retryable push must not be reported as success.
 */
suspend fun runSyncCycle(engine: SyncEngine): SyncOutcome {
    val push = engine.pushPending()
    if (push.needsRePair) return SyncOutcome.FAILURE
    if (push.retried > 0) return SyncOutcome.RETRY
    val pull = engine.pullAll()
    if (pull.needsRePair) return SyncOutcome.FAILURE
    return if (pull.failed != null) SyncOutcome.RETRY else SyncOutcome.SUCCESS
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val coupleId = inputData.getString(KEY_COUPLE_ID)?.let(UUID::fromString) ?: return Result.failure()
        val engine = SyncEngineRegistry.factory.create(coupleId)
        val outcome = runSyncCycle(engine)
        if (outcome != SyncOutcome.FAILURE) {
            // Runs after every cycle that could have applied changes, not just the
            // one that pulled them: a worker cancelled or retried mid-pull would
            // otherwise drop that notice for good. The app-side hook dedups, so
            // running it again costs a query and never repeats a notice.
            SyncEngineRegistry.onCycleCompleted()
        }
        return when (outcome) {
            SyncOutcome.SUCCESS -> Result.success()
            SyncOutcome.RETRY -> Result.retry()
            // A 401 means re-pair is needed; retrying blindly would not help.
            SyncOutcome.FAILURE -> Result.failure()
        }
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
