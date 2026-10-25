package com.twomemory.app.notifications

import android.content.Context
import androidx.work.*
import com.twomemory.app.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object CloudKeepsakeReminderRules {
    fun nextDate(card: OccasionCard, user: String, today: LocalDate): LocalDate? {
        if (!card.opensFor(user)) return null
        val start = runCatching { LocalDate.parse(card.date) }.getOrNull() ?: return null
        var next = if (card.repeatsYearly && today >= start) start.withYear(today.year) else start
        if (next < today) { if (!card.repeatsYearly) return null; next = start.withYear(today.year + 1) }
        return next
    }
}

/** Schedules generic reminders from confirmed per-account snapshots; never includes a secret body. */
object CloudKeepsakeReminders {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun scope(session: SyncSession.Session) = recordingScopeKey("${session.storageOrigin}|${session.coupleId}|${session.userId}")
    fun schedule(context: Context) {
        val session = SyncSession.load(context) ?: return
        if (!context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).getBoolean("localRemindersEnabled", true)) return
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val user = session.userId.toString()
        val receipts = LocalReminderState(context)
        fun key(kind: String, id: String, at: Long) = "cloud-${scope(session)}-$kind-$id-$at"
        val plans = OccasionCloud.store(context, session).cards().mapNotNull { card ->
            var date = CloudKeepsakeReminderRules.nextDate(card, user, today) ?: return@mapNotNull null
            val at = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            if (!receipts.shouldDeliver(key("occasion", card.id, at))) {
                if (!card.repeatsYearly) return@mapNotNull null
                // A same-day reschedule after delivery must move to the next occurrence.
                date = CloudKeepsakeReminderRules.nextDate(card, user, date.plusDays(1)) ?: return@mapNotNull null
            }
            Triple("occasion", card.id, date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        } + CapsuleCloud.store(context, session).cards().mapNotNull { card ->
            if (card.authorId != user && card.visibility != "SHARED") null else runCatching {
                Triple("capsule", card.id, LocalDate.parse(card.unlockDate).atStartOfDay(zone).toInstant().toEpochMilli())
            }.getOrNull()
        }
        // Saving and synchronizing must not fail when background scheduling is not ready yet.
        // Startup/foreground and each confirmed sync retry scheduling independently.
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        plans.forEach { (kind, id, at) ->
            val key = key(kind, id, at)
            if (receipts.shouldDeliver(key)) manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CloudKeepsakeReminderWorker>()
                    .setInputData(workDataOf("scope" to scope(session), "kind" to kind, "id" to id, "at" to at, "key" to key))
                    .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build())
        }
    }
    internal fun currentScope(session: SyncSession.Session) = scope(session)
    internal fun zone() = zone
}

class CloudKeepsakeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val session = SyncSession.load(context) ?: return Result.success()
        val scope = inputData.getString("scope") ?: return Result.success()
        if (CloudKeepsakeReminders.currentScope(session) != scope) return Result.success()
        if (!context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).getBoolean("localRemindersEnabled", true)) return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val id = inputData.getString("id") ?: return Result.success()
        val kind = inputData.getString("kind") ?: return Result.success()
        val at = inputData.getLong("at", -1)
        if (at < 0) return Result.success()
        if (System.currentTimeMillis() < at) return Result.retry()
        val receipts = LocalReminderState(context)
        if (!receipts.shouldDeliver(key)) return Result.success()
        if (kind == "capsule") {
            // A fast phone clock cannot reveal the letter or produce an early opened-letter notice.
            if (!CapsuleCloud.refresh(context)) return Result.retry()
            if (SyncSession.load(context) != session) return Result.success()
            val card = CapsuleCloud.store(context, session).cards().firstOrNull { it.id == id } ?: return Result.success()
            val expected = LocalDate.parse(card.unlockDate).atStartOfDay(CloudKeepsakeReminders.zone()).toInstant().toEpochMilli()
            if (expected != at) return Result.success()
            if (!card.unlocked) return Result.retry()
        } else if (kind == "occasion") {
            // Retained offline caches cannot authorize delivery; refresh must confirm a current snapshot.
            if (!OccasionCloud.refresh(context)) return Result.retry()
            if (SyncSession.load(context) != session) return Result.success()
            val card = OccasionCloud.store(context, session).cards().firstOrNull { it.id == id } ?: return Result.success()
            val date = Instant.ofEpochMilli(at).atZone(CloudKeepsakeReminders.zone()).toLocalDate()
            if (!card.opensFor(session.userId.toString()) || !card.isDue(date)) return Result.success()
            val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(CloudKeepsakeReminders.zone()).toLocalDate()
            if (today < date) return Result.retry()
            if (today > date) {
                // Delayed work cannot call a past occurrence "today". Replan from the refreshed snapshot.
                CloudKeepsakeReminders.schedule(context)
                return Result.success()
            }
        } else return Result.success()
        if (SyncSession.load(context) != session) return Result.success()
        if (!LocalReminderNotifier.showCloudReminder(context, kind, id)) return Result.retry()
        receipts.markDelivered(key)
        CloudKeepsakeReminders.schedule(context)
        return Result.success()
    }
}
