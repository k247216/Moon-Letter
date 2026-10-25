package com.twomemory.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.twomemory.app.IndependentAnniversaryDraft
import com.twomemory.app.IndependentAnniversaryStore
import com.twomemory.app.SyncSession
import com.twomemory.app.recordingScopeKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** No title/body/token enters WorkManager. This scheduler owns only its scoped jobs. */
internal object IndependentAnniversaryReminder {
    private data class Rule(val scope: String, val date: LocalDate, val repeats: Boolean,
        val time: LocalTime, val zone: ZoneId, val configuration: String)

    fun nextAt(draft: IndependentAnniversaryDraft, nowMillis: Long, zone: ZoneId): Long? = runCatching {
        val year = draft.year ?: return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        if (year < 1 || year > today.year) return null
        val rule = Rule("", LocalDate.of(year, draft.month, draft.day), true, LocalTime.parse(draft.reminderTime), zone, "")
        next(rule, nowMillis)
    }.getOrNull()

    private fun next(rule: Rule, now: Long): Long? {
        val today = Instant.ofEpochMilli(now).atZone(rule.zone).toLocalDate()
        var date = if (rule.repeats && today >= rule.date) rule.date.withYear(today.year) else rule.date
        var target = date.atTime(rule.time).atZone(rule.zone).toInstant().toEpochMilli()
        if (target < now) {
            if (!rule.repeats) return null
            date = rule.date.withYear(today.year + 1)
            target = date.atTime(rule.time).atZone(rule.zone).toInstant().toEpochMilli()
        }
        return target
    }

    private fun rule(context: Context, binding: SyncSession.BindingSnapshot): Rule? = runCatching {
        val prefs = IndependentAnniversaryStore.preferences(context, binding)
        val confirmed = IndependentAnniversaryStore.readConfirmed(prefs) ?: return null
        if (confirmed.coupleId != binding.session.coupleId || confirmed.calendarType != "GREGORIAN" ||
            confirmed.lunarLeapMonth) return null
        val year = confirmed.startYear ?: return null
        if (year !in 1..32767) return null
        val date = LocalDate.of(year, confirmed.month, confirmed.day)
        val time = LocalTime.parse(prefs.getString("reminderTime", "09:00"))
        val zone = ZoneId.systemDefault()
        val scope = IndependentAnniversaryStore.scopeOf(binding.session)
        val configuration = recordingScopeKey("${confirmed.id}|${confirmed.title}|$date|${confirmed.repeatsYearly}|$time|${zone.id}")
        Rule(scope, date, confirmed.repeatsYearly, time, zone, configuration)
    }.getOrNull()

    fun schedule(context: Context, binding: SyncSession.BindingSnapshot) {
        // Unavailable WorkManager does not turn an acknowledged cloud save into a failed save.
        val manager = WorkManager.getInstance(context)
        synchronized(SyncSession.bindingLock) {
            if (!SyncSession.isCurrentBinding(context, binding)) return
            val scope = IndependentAnniversaryStore.scopeOf(binding.session)
            val prefs = IndependentAnniversaryStore.preferences(context, binding)
            val oldKey = prefs.getString("queuedKey", null)
            val oldAt = prefs.getLong("queuedAt", -1)
            val oldConfiguration = prefs.getString("queuedConfiguration", null)
            val now = System.currentTimeMillis()
            val value = if (deviceRemindersEnabled(context)) rule(context, binding) else null
            val receipts = LocalReminderState(context)
            val retained = value?.takeIf { oldConfiguration == it.configuration &&
                oldAt >= 0 && oldKey == occurrenceKey(it, oldAt) && receipts.shouldDeliver(requireNotNull(oldKey)) &&
                next(it, oldAt) == oldAt && Instant.ofEpochMilli(oldAt).atZone(it.zone).toLocalDate() >=
                    Instant.ofEpochMilli(now).atZone(it.zone).toLocalDate() }
            val at = if (retained != null) oldAt else value?.let { next(it, now) }
            val key = if (value != null && at != null) occurrenceKey(value, at).takeIf { receipts.shouldDeliver(it) } else null
            if (oldKey != null && oldKey != key && receipts.shouldDeliver(oldKey))
                manager.cancelUniqueWork(workName(scope, oldKey))
            if (value == null || at == null || key == null) {
                if (prefs.contains("queuedKey") || prefs.contains("queuedAt") || prefs.contains("queuedConfiguration"))
                    com.twomemory.app.commitBinding(prefs, prefs.edit().remove("queuedKey").remove("queuedAt").remove("queuedConfiguration"))
                return
            }
            if (oldKey != key || oldAt != at || oldConfiguration != value.configuration)
                com.twomemory.app.commitBinding(prefs, prefs.edit().putString("queuedKey", key).putLong("queuedAt", at)
                    .putString("queuedConfiguration", value.configuration))
            val request = OneTimeWorkRequestBuilder<IndependentAnniversaryReminderWorker>()
                .addTag("independent-anniversary:$scope")
                .setInputData(workDataOf("scope" to scope, "configuration" to value.configuration, "key" to key, "at" to at))
                .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            manager.enqueueUniqueWork(workName(scope, key), ExistingWorkPolicy.KEEP, request)
        }
    }

    /** Worker calls this under bindingLock immediately before displaying a generic notification. */
    internal fun canDeliver(context: Context, binding: SyncSession.BindingSnapshot, scope: String,
        configuration: String, key: String, at: Long): Boolean {
        if (!deviceRemindersEnabled(context)) return false
        if (!SyncSession.isCurrentBinding(context, binding) || IndependentAnniversaryStore.scopeOf(binding.session) != scope) return false
        val prefs = IndependentAnniversaryStore.preferences(context, binding)
        val value = rule(context, binding) ?: return false
        return at >= 0 && value.configuration == configuration && occurrenceKey(value, at) == key &&
            prefs.getString("queuedKey", null) == key && prefs.getLong("queuedAt", -1) == at &&
            next(value, at) == at
    }

    private fun occurrenceKey(rule: Rule, at: Long) = "independent-anniversary:${rule.scope}:${rule.configuration}:$at"
    private fun workName(scope: String, key: String) = "independent-anniversary:$scope:$key"
    // Device notification switch only, never a legacy date/cache input.
    private fun deviceRemindersEnabled(context: Context): Boolean =
        context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).getBoolean("localRemindersEnabled", true)
}

class IndependentAnniversaryReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        performWork()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }

    private suspend fun performWork(): Result {
        val context = applicationContext
        val scope = inputData.getString("scope") ?: return Result.success()
        val configuration = inputData.getString("configuration") ?: return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        val at = inputData.getLong("at", -1)
        val job = currentCoroutineContext()
        val binding = SyncSession.captureBinding(context) ?: return Result.success()
        synchronized(SyncSession.bindingLock) {
            job.ensureActive()
            if (!IndependentAnniversaryReminder.canDeliver(context, binding, scope, configuration, key, at)) {
                IndependentAnniversaryReminder.schedule(context, binding)
                return Result.success()
            }
            if (System.currentTimeMillis() < at) return Result.retry()
            val zone = ZoneId.systemDefault()
            val queuedDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            val actualDate = LocalDate.now(zone)
            // Same-day background delay is acceptable; a days-late job cannot say "today".
            // Leave the old receipt undelivered and let schedule select the next occurrence.
            if (queuedDate == actualDate) {
                val receipts = LocalReminderState(context)
                if (receipts.shouldDeliver(key)) {
                    if (!LocalReminderNotifier.showReminder(context, "anniversary")) return Result.retry()
                    receipts.markDelivered(key)
                }
            }
        }
        job.ensureActive()
        IndependentAnniversaryReminder.schedule(context, binding)
        return Result.success()
    }
}
