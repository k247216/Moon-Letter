package com.twomemory.app

import android.content.Context

/** Device-personal sidecar IDs only: not shared cloud data, records, or media. */
internal class MemoryBookmarkStore(context: Context, origin: String, coupleId: String, userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "moon_letter_bookmarks_${scopeKey(origin, coupleId, userId)}", Context.MODE_PRIVATE)

    fun ids(): Set<String> = synchronized(lock) { readIds() }

    fun toggle(entryId: String): Set<String> = update(entryId) { ids ->
        if (entryId in ids) ids - entryId else ids + entryId
    }

    fun remove(entryId: String): Set<String> = update(entryId) { it - entryId }

    private fun readIds(): Set<String> = prefs.getStringSet("ids", emptySet()).orEmpty().toSet()

    private fun update(entryId: String, change: (Set<String>) -> Set<String>): Set<String> = synchronized(lock) {
        require(entryId.isNotBlank()) { "Bookmark record ID must not be blank" }
        // Re-read on every mutation so separate store instances cannot overwrite each other's IDs.
        val before = readIds()
        val after = change(before)
        if (after != before) {
            try {
                commitBinding(prefs, prefs.edit().putStringSet("ids", after))
            } catch (failure: IllegalStateException) {
                throw IllegalStateException("书签没有保存成功，请稍后再试。", failure)
            }
        }
        after
    }

    private companion object {
        val lock = Any()

        fun scopeKey(origin: String, coupleId: String, userId: String): String {
            val parts = listOf(origin.trimEnd('/'), coupleId, userId)
            require(parts.all { it.isNotBlank() }) { "Bookmarks require a bound personal scope" }
            val scope = parts.joinToString("|") { "${it.length}:$it" }
            return java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        }
    }
}
