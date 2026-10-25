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

object LocalReminderNotifier {
    const val CHANNEL = "local_relationship_reminders"

    fun sendTest(context: Context): String = if (post(context, 2100, "月笺本机测试通知", "这是本机通道检查，不代表对方通知或定时提醒已验收。"))
        "已发送本机测试通知，请检查通知栏" else "通知未发送，请检查系统通知权限和本机提醒通道"

    fun openSettings(context: Context) {
        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    internal fun showReminder(context: Context, kind: String): Boolean = if (kind == "capsule")
        post(context, 2102, "本机时间胶囊到期了", "打开月笺查看这台手机保存的信。", target = "capsule")
    else post(context, 2101, "今天是你保存的纪念日", "打开月笺，给这一天留下一句话。", target = "anniversary")

    internal fun showCloudReminder(context: Context, kind: String, id: String): Boolean =
        post(context, ("$kind:$id").hashCode(), if (kind == "capsule") "一封时光信可以打开了" else "今天，有个日子值得记住",
            "打开月笺，看看收好的那份心意。", target = if (kind == "capsule") "capsule" else "occasion", reminderId = id)

    private fun post(context: Context, id: Int, title: String, body: String,
        target: String? = null, reminderId: String? = null): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "本机纪念日与胶囊", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val intent = PendingIntent.getActivity(context, id,
            Intent(context, MainActivity::class.java).apply {
                if (target != null) {
                    putExtra("com.twomemory.app.OPEN_REMINDER", target)
                    reminderId?.let { putExtra("com.twomemory.app.REMINDER_ID", it) }
                    NoticeTargetScope.current(context)?.let { putExtra(NoticeTargetScope.EXTRA, it) }
                }
            }.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body).setContentIntent(intent).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return runCatching { NotificationManagerCompat.from(context).notify(id, notification); true }.getOrDefault(false)
    }
}
