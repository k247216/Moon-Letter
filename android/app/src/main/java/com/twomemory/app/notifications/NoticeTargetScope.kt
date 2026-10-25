package com.twomemory.app.notifications

import android.content.Context
import android.content.Intent
import com.twomemory.app.SyncSession
import com.twomemory.app.recordingScopeKey

/** No tokens/content in intents. A notification belongs to one server, space and member. */
internal object NoticeTargetScope {
    const val EXTRA = "com.twomemory.app.NOTICE_SCOPE"
    fun of(session: SyncSession.Session): String = recordingScopeKey(
        "${session.storageOrigin}|${session.coupleId}|${session.userId}")
    fun current(context: Context): String? = SyncSession.load(context)?.let(::of)
    fun accepts(context: Context, intent: Intent?): Boolean {
        // Legacy notices may launch the app, but cannot safely choose an account's destination.
        if (intent == null || !intent.hasExtra(EXTRA)) return false
        val scope = intent.getStringExtra(EXTRA) ?: return false
        return scope == current(context)
    }
}
