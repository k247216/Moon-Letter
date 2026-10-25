package com.twomemory.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.util.UUID

/** Keep a contribution separate from the new-record draft, reusing the same serializer. */
fun perspectiveDraftContext(context: Context, entryId: UUID): Context {
    // Capture identity when the context is created; an old async save cannot
    // follow a later account switch into the new member's draft slot.
    val session = SyncSession.load(context)
    val identity = session?.let { "${it.storageOrigin}|${it.coupleId}|${it.userId}" } ?: "unbound"
    val scope = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(if (name == "moon_letter_draft") "$name-perspective-v2-$scope-$entryId" else name, mode)
    }
    // Legacy unowned preferences are deliberately preserved, never assigned
    // silently to whichever member happens to log in first after upgrading.
}
