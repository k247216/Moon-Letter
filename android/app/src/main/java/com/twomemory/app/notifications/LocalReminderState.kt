package com.twomemory.app.notifications

import android.content.Context

/** Delivery receipts survive process/device restart, separate from the original letter. */
class LocalReminderState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "moon_letter_local_reminders", Context.MODE_PRIVATE,
    )

    fun shouldDeliver(key: String): Boolean = !prefs.getBoolean("delivered:$key", false)

    fun markDelivered(key: String) {
        com.twomemory.app.commitBinding(prefs, prefs.edit().putBoolean("delivered:$key", true))
    }
}
