package com.twomemory.app.notifications

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalReminderNotifierTest {
    @Test fun disabledLocalChannelDoesNotReportSuccessfulTestDelivery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(NotificationChannel(
            LocalReminderNotifier.CHANNEL, "本机提醒", NotificationManager.IMPORTANCE_NONE,
        ))
        assertEquals("通知未发送，请检查系统通知权限和本机提醒通道", LocalReminderNotifier.sendTest(context))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun capsuleNoticeDoesNotExposeStoredLetterBody() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).edit()
            .putString("capsuleBody", "不能出现在锁屏上的私密正文").commit()
        assertTrue(LocalReminderNotifier.showReminder(context, "capsule"))
        val notice = requireNotNull(context.getSystemService(NotificationManager::class.java)).activeNotifications.single().notification
        assertEquals("本机时间胶囊到期了", notice.extras.getString("android.title"))
        assertEquals("打开月笺查看这台手机保存的信。", notice.extras.getString("android.text"))
        assertEquals("不能出现在锁屏上的私密正文", context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).getString("capsuleBody", null))
    }
}
