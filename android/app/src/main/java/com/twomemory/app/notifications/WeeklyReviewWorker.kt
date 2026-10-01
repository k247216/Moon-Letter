package com.twomemory.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryDao
import com.twomemory.sync.ReviewCandidate
import com.twomemory.sync.WeeklyReview
import java.util.concurrent.TimeUnit

/**
 * The weekly re-encounter. It reads the local archive, picks one clearly earlier
 * published record and opens it on tap; when nothing qualifies it produces
 * nothing at all — no placeholder, no "本周没有内容".
 */
class WeeklyReviewWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        pickReviewEntryId(context)?.let { MoonLetterNotifier.showWeeklyReview(context, it) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "moon-letter-weekly-review"
        private const val PERIOD_DAYS = 7L

        /**
         * (Re)queues the weekly run so its first firing lands on the chosen
         * weekday and hour, then the seven-day period carries it forward.
         * [force] replaces an already queued run — use it only when the settings
         * actually changed, because re-enqueueing restarts the waiting time.
         * WorkManager only promises a window, not the exact minute: 到点 must be
         * confirmed on a real phone.
         */
        fun schedule(context: Context, force: Boolean = false) {
            val preferences = NotificationPreferences(context)
            if (!preferences.weeklyReviewEnabled) {
                cancel(context)
                return
            }
            val initialDelay = WeeklyReview.millisUntilNext(
                System.currentTimeMillis(),
                preferences.reviewDayOfWeek,
                preferences.reviewHour,
            )
            val request = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(PERIOD_DAYS, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                if (force) {
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                },
                request,
            )
        }

        /** Off means off: the queued run goes with it. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}

/**
 * The one record this week's re-encounter should open, or null — and null is the
 * quiet skip: a young archive produces no notice and no placeholder.
 */
internal suspend fun pickReviewEntryId(
    context: Context,
    entries: EntryDao = AppDatabase.build(context).entryDao(),
): String? {
    // Re-read here, so a run queued before the switch went off still produces nothing.
    if (!NotificationPreferences(context).weeklyReviewEnabled) return null
    val candidates = runCatching {
        entries.publishedEntries().map { ReviewCandidate(it.id, it.occurredAtEpochMillis) }
    }.getOrDefault(emptyList())
    return WeeklyReview.pick(candidates, System.currentTimeMillis())?.entryId
}
