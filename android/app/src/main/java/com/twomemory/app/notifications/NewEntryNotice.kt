package com.twomemory.app.notifications

import android.content.Context
import com.twomemory.app.SyncSession
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryDao

/** What a finished sync cycle should do about the "TA 写了一条新的" notice. */
enum class NewEntryAction { NONE, REMEMBER, ANNOUNCE }

/**
 * One record announced once. The whole state is the id of the record this device
 * last saw arrive from the other member, so nothing accumulates: several records
 * written while this phone was away still produce one notice, and there is no
 * unread number anywhere.
 */
object NewEntryNotice {

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
        entries: EntryDao = AppDatabase.build(context).entryDao(),
    ) {
        val preferences = NotificationPreferences(context)
        if (!preferences.newEntryNoticeEnabled) return
        val session = SyncSession.load(context) ?: return
        val latestEntryId = runCatching {
            entries.latestArrivedPublishedByOther(session.userId.toString())?.id
        }.getOrNull() ?: return
        when (decide(latestEntryId, preferences.lastNotifiedEntryId)) {
            NewEntryAction.NONE -> Unit
            NewEntryAction.REMEMBER -> preferences.lastNotifiedEntryId = latestEntryId
            NewEntryAction.ANNOUNCE -> {
                MoonLetterNotifier.showNewEntry(context, latestEntryId)
                preferences.lastNotifiedEntryId = latestEntryId
            }
        }
    }
}
