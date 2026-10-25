package com.twomemory.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object LocalRelationshipReminderScheduler {
    /** Call after committing moon_letter_tools edits and on process startup. */
    @Synchronized
    fun schedule(context: Context) {
        val now = System.currentTimeMillis()
        val manager = WorkManager.getInstance(context)
        val state = context.getSharedPreferences("moon_letter_local_reminders", Context.MODE_PRIVATE)
        val tools = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        for (kind in listOf("anniversary", "capsule")) {
            val previous = state.getString("queued:$kind", null)
            val previousAt = state.getLong("at:$kind", now)
            // Validate the old occurrence against current settings before retaining overdue work.
            val retained = plan(context, kind, previousAt)?.takeIf {
                it.key == previous && LocalReminderState(context).shouldDeliver(it.key)
            }
            val next = if (tools.getBoolean("localRemindersEnabled", true))
                retained ?: plan(context, kind, now)?.takeIf { LocalReminderState(context).shouldDeliver(it.key) }
            else null
            if (previous != null && previous != next?.key && LocalReminderState(context).shouldDeliver(previous)) {
                manager.cancelUniqueWork("local-$previous")
            }
            if (next == null) {
                state.edit().remove("queued:$kind").remove("at:$kind").apply()
                continue
            }
            state.edit().putString("queued:$kind", next.key).putLong("at:$kind", next.atMillis).apply()
            val request = OneTimeWorkRequestBuilder<LocalRelationshipReminderWorker>()
                .setInputData(workDataOf("kind" to kind, "key" to next.key, "at" to next.atMillis))
                .setInitialDelay((next.atMillis - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            manager.enqueueUniqueWork("local-${next.key}", ExistingWorkPolicy.KEEP, request)
        }
    }

    internal fun plan(context: Context, kind: String, now: Long): LocalReminderPlan? {
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        val zone = ZoneId.systemDefault()
        return when (kind) {
            // Old installation-wide dates have no proven owner. Preserve them,
            // but only the new scoped, confirmed scheduler may deliver anniversaries.
            "anniversary" -> null
            "capsule" -> if (prefs.getString("capsuleBody", "").isNullOrBlank()) null else LocalReminderRules.capsule(
                prefs.getString("capsuleTitle", "").orEmpty(),
                prefs.getString("capsuleUnlockDate", "").orEmpty(),
                prefs.getBoolean("capsuleLocked", false), now, zone,
            )
            else -> null
        }
    }
}

class LocalRelationshipReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val at = inputData.getLong("at", -1)
        val context = applicationContext
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("localRemindersEnabled", true) ||
            LocalRelationshipReminderScheduler.plan(context, kind, at)?.key != key) {
            LocalRelationshipReminderScheduler.schedule(context)
            return Result.success()
        }
        if (System.currentTimeMillis() < at) {
            // Recheck wall clock after a clock change; never reveal a capsule early.
            return Result.retry()
        }
        val state = LocalReminderState(context)
        if (state.shouldDeliver(key)) {
            // Capsule body remains in tools prefs only, never in WorkManager or notifications.
            if (!LocalReminderNotifier.showReminder(context, kind)) return Result.retry()
            state.markDelivered(key)
        }
        LocalRelationshipReminderScheduler.schedule(context)
        return Result.success()
    }
}
