package com.twomemory.app

import android.content.Context
import java.security.MessageDigest

internal fun recordingScopeKey(scope: String): String = MessageDigest.getInstance("SHA-256")
    .digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Private reading position, never a receipt sent to the other member. */
internal class ReadingProgressStore(context: Context, scope: String) {
    private val prefs = context.getSharedPreferences("moon_letter_reading_${recordingScopeKey(scope)}", Context.MODE_PRIVATE)
    fun readIds(): Set<String> = prefs.getStringSet("read", emptySet()).orEmpty().toSet()
    fun markRead(id: String) = synchronized(lock) {
        if (id.isNotBlank()) prefs.edit().putStringSet("read", readIds() + id).apply()
    }
    companion object { private val lock = Any() }
}
