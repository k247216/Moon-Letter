package com.twomemory.app.notifications

import android.content.Context
import java.time.DayOfWeek

/**
 * The two switches, when the weekly re-encounter is due, and the one watermark the
 * new-entry notice needs. Nothing else is kept: no counts, no unread state, no
 * history of what was shown. Switching a feature off deletes what only that
 * feature remembered.
 */
class NotificationPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var weeklyReviewEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_WEEKLY_ENABLED, value).apply()
        }

    var newEntryNoticeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NEW_ENTRY_ENABLED, true)
        set(value) {
            val editor = prefs.edit().putBoolean(KEY_NEW_ENTRY_ENABLED, value)
            if (!value) editor.remove(KEY_LAST_NOTIFIED_ENTRY)
            editor.apply()
        }

    /** The last record this device already announced; a single id, never a tally. */
    var lastNotifiedEntryId: String?
        get() = prefs.getString(KEY_LAST_NOTIFIED_ENTRY, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_LAST_NOTIFIED_ENTRY) else editor.putString(KEY_LAST_NOTIFIED_ENTRY, value)
            editor.apply()
        }

    var reviewDayOfWeek: DayOfWeek
        get() = runCatching { DayOfWeek.valueOf(prefs.getString(KEY_REVIEW_DAY, DayOfWeek.SUNDAY.name)!!) }
            .getOrDefault(DayOfWeek.SUNDAY)
        set(value) {
            prefs.edit().putString(KEY_REVIEW_DAY, value.name).apply()
        }

    var reviewHour: Int
        get() = prefs.getInt(KEY_REVIEW_HOUR, DEFAULT_REVIEW_HOUR).coerceIn(0, 23)
        set(value) {
            prefs.edit().putInt(KEY_REVIEW_HOUR, value.coerceIn(0, 23)).apply()
        }

    /** Asked once, never again: the system prompt must not become a nag. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()
        }

    companion object {
        const val DEFAULT_REVIEW_HOUR = 20

        private const val PREFS = "moon_letter_notifications"
        private const val KEY_WEEKLY_ENABLED = "weeklyReviewEnabled"
        private const val KEY_NEW_ENTRY_ENABLED = "newEntryNoticeEnabled"
        private const val KEY_LAST_NOTIFIED_ENTRY = "lastNotifiedEntryId"
        private const val KEY_REVIEW_DAY = "reviewDayOfWeek"
        private const val KEY_REVIEW_HOUR = "reviewHour"
        private const val KEY_PERMISSION_ASKED = "notificationPermissionAsked"
    }
}
