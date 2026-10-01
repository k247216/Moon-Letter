package com.twomemory.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.twomemory.app.MainActivity
import com.twomemory.app.R

/**
 * Two notices, both with a fixed id: a new one replaces the last instead of
 * leaving a stack the couple would have to clear. Tapping either opens that one
 * record directly.
 */
object MoonLetterNotifier {

    const val CHANNEL_WEEKLY_REVIEW = "weekly_review"
    const val CHANNEL_NEW_ENTRY = "new_entry"

    private const val ID_WEEKLY_REVIEW = 2001
    private const val ID_NEW_ENTRY = 2002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WEEKLY_REVIEW, "每周回看", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NEW_ENTRY, "对方写了新的", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun showWeeklyReview(context: Context, entryId: String) {
        post(
            context = context,
            id = ID_WEEKLY_REVIEW,
            channel = CHANNEL_WEEKLY_REVIEW,
            requestCode = ID_WEEKLY_REVIEW,
            title = "很久以前的一条",
            body = "点开看看那天的你们",
            entryId = entryId,
        )
    }

    /** Says only that TA wrote one. No title, no body text, no count, no unread total. */
    fun showNewEntry(context: Context, entryId: String) {
        post(
            context = context,
            id = ID_NEW_ENTRY,
            channel = CHANNEL_NEW_ENTRY,
            requestCode = ID_NEW_ENTRY,
            title = "TA 写了一条新的",
            body = "点开看看",
            entryId = entryId,
        )
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        requestCode: Int,
        title: String,
        body: String,
        entryId: String,
    ) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openEntryIntent(context, entryId, requestCode))
            .setAutoCancel(true)
            .build()
        // A denied permission makes this a silent no-op, which is the right answer:
        // neither feature may fall back to an in-app badge or counter.
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun openEntryIntent(context: Context, entryId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ENTRY, entryId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
