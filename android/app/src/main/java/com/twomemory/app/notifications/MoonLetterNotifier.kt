package com.twomemory.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.twomemory.app.MainActivity
import com.twomemory.app.R

/**
 * Each notice kind has a fixed id: a new one replaces the last instead of
 * leaving a stack the couple would have to clear. Tapping a notice opens that one
 * record directly.
 */
object MoonLetterNotifier {

    const val CHANNEL_WEEKLY_REVIEW = "weekly_review"
    const val CHANNEL_NEW_ENTRY = "new_entry"
    const val CHANNEL_PARTNER_REPLIES = "partner_replies"

    private const val ID_WEEKLY_REVIEW = 2001
    private const val ID_NEW_ENTRY = 2002
    private const val ID_COMMENT = 2003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WEEKLY_REVIEW, "每周旧笺提醒", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NEW_ENTRY, "对方写了新的", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PARTNER_REPLIES, "对方回复了", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun showWeeklyReview(context: Context, entryId: String): Boolean =
        post(
            context = context,
            id = ID_WEEKLY_REVIEW,
            channel = CHANNEL_WEEKLY_REVIEW,
            requestCode = ID_WEEKLY_REVIEW,
            title = "很久以前的一条",
            body = "点开看看那天的你们",
            entryId = entryId,
        )

    /** Says only that TA wrote one. No title, no body text, no count, no unread total. */
    fun showNewEntry(context: Context, entryId: String): Boolean =
        post(
            context = context,
            id = ID_NEW_ENTRY,
            channel = CHANNEL_NEW_ENTRY,
            requestCode = ID_NEW_ENTRY,
            title = "TA 写了一条新的",
            body = "点开看看",
            entryId = entryId,
        )

    fun showComment(context: Context, entryId: String): Boolean =
        post(
            context = context,
            id = ID_COMMENT,
            channel = CHANNEL_PARTNER_REPLIES,
            requestCode = ID_COMMENT,
            title = "TA 留下了一条回复",
            body = "点开看看",
            entryId = entryId,
        )

    /** True means the system notify call returned normally, not that anyone received or read it. */
    private fun post(
        context: Context,
        id: Int,
        channel: String,
        requestCode: Int,
        title: String,
        body: String,
        entryId: String,
    ): Boolean = runCatching {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return@runCatching false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return@runCatching false
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return@runCatching false
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("月笺")
            .setContentText("打开月笺查看")
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openEntryIntent(context, entryId, requestCode))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(id, notification)
        true
    }.getOrDefault(false)

    private fun openEntryIntent(context: Context, entryId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ENTRY, entryId)
            .apply {
                NoticeTargetScope.current(context)?.let { scope ->
                    putExtra("com.twomemory.app.NOTICE_SCOPE", scope)
                }
            }
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
