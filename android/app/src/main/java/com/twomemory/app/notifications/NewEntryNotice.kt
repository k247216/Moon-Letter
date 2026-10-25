package com.twomemory.app.notifications

import android.content.Context
import com.twomemory.app.SyncSession
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryDao
import com.twomemory.database.CommentDao
import com.twomemory.app.commitBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a finished sync cycle should do about the "TA 写了一条新的" notice. */
enum class NewEntryAction { NONE, REMEMBER, ANNOUNCE }

/**
 * Account-scoped identity receipts prevent edits/deletions from looking like arrivals.
 * Only ids are retained, never message contents. Several arrivals replace one notice;
 * failed system posting remains eligible for the next sync, with no unread counter.
 */
object NewEntryNotice {
    private val mutex = Mutex()

    /**
     * A device seeing its partner's records for the first time only learns where
     * the line sits — [NewEntryAction.REMEMBER]. Announcing what was already there
     * as "just written" would be a lie, and the notice must never fire for it.
     */
    fun decide(latestArrivedEntryId: String, lastNotifiedEntryId: String?): NewEntryAction = when {
        latestArrivedEntryId == lastNotifiedEntryId -> NewEntryAction.NONE
        lastNotifiedEntryId == null -> NewEntryAction.REMEMBER
        else -> NewEntryAction.ANNOUNCE
    }

    /**
     * Called from the sync worker after changes arrived. Best effort by design: a
     * missing session, a locked database or a denied permission all end quietly,
     * because a notice that failed is never worth a crash or a retry storm.
     */
    suspend fun announceIfNeeded(
        context: Context,
        entries: EntryDao? = null,
        comments: CommentDao? = null,
    ) = mutex.withLock {
        val preferences = NotificationPreferences(context)
        if (!preferences.newEntryNoticeEnabled) return@withLock
        val binding = SyncSession.captureBinding(context) ?: return@withLock
        val session = binding.session
        val database by lazy { AppDatabase.build(context, session.databaseName) }
        val job = currentCoroutineContext()
        val receipt = context.getSharedPreferences("moon_letter_arrivals_${NoticeTargetScope.of(session)}", Context.MODE_PRIVATE)
        val generation = preferences.noticeGeneration
        suspend fun arrivals(kind: String, targets: List<Pair<String, String>>, post: (String) -> Boolean) {
            job.ensureActive()
            synchronized(SyncSession.bindingLock) {
                job.ensureActive()
                if (!SyncSession.isCurrentBinding(context, binding) || !preferences.newEntryNoticeEnabled ||
                    preferences.noticeGeneration != generation) return
                val initialized = receipt.getBoolean("$kind.ready", false) &&
                    receipt.getLong("$kind.generation", -1) == generation
                val known = if (initialized) receipt.getStringSet("$kind.ids", emptySet()).orEmpty().toSet() else emptySet()
                val newest = targets.lastOrNull { it.first !in known }
                // The first local snapshot is historical, even when empty. A later first arrival is genuinely new.
                if (initialized && newest != null && !post(newest.second)) return
                val observed = known + targets.map { it.first }
                if (!initialized || observed != known) {
                    commitBinding(receipt, receipt.edit().putBoolean("$kind.ready", true)
                        .putLong("$kind.generation", generation).putStringSet("$kind.ids", observed))
                }
                if (kind == "entries" && targets.isNotEmpty()) preferences.lastNotifiedEntryId = targets.last().first
            }
        }
        try {
            val candidates = (entries ?: database.entryDao()).noticeEntryIds(session.coupleId.toString(), session.userId.toString())
            arrivals("entries", candidates.map { it to it }) { MoonLetterNotifier.showNewEntry(context, it) }
            // A supplied DAO is an explicit archive boundary; never mix it with another default archive.
            if (comments != null || entries == null) {
                val replies = (comments ?: database.commentDao()).noticeTargets(session.coupleId.toString(), session.userId.toString())
                arrivals("comments", replies.map { it.id to it.entryId }) { MoonLetterNotifier.showComment(context, it) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep receipts unchanged on a failed read/write and retry after a later sync. */ }
    }
}
