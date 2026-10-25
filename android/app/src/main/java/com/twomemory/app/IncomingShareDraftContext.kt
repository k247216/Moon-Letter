package com.twomemory.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

internal fun incomingShareDraftContext(context: Context): Context {
    val session = SyncSession.load(context)
    val scope = session?.let { "${it.storageOrigin}|${it.coupleId}|${it.userId}" } ?: "unbound"
    return object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(if (name == "moon_letter_draft") "$name-incoming-${recordingScopeKey(scope)}" else name, mode)
    }
}
