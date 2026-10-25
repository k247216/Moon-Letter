package com.twomemory.app.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real WorkManager queue coverage migrated from JVM: Robolectric lacked merged WorkManager resources. */
@RunWith(AndroidJUnit4::class)
class LocalReminderSchedulerIntegrationTest {
    @Test fun repeatedSchedulingKeepsWorkAndEditingTimeCancelsOldOccurrence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkManager.getInstance(context)
        val tools = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        val state = context.getSharedPreferences("moon_letter_local_reminders", Context.MODE_PRIVATE)
        val originalTools = tools.all.toMap()
        val originalState = state.all.toMap()
        val testWorkNames = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        try {
            // Unique title keeps prior runs' cancelled history out of single-occurrence assertions.
            assertTrue(tools.edit().putString("capsuleTitle", "队列测试-${UUID.randomUUID()}")
                .putString("capsuleBody", "原件").putString("capsuleUnlockDate", "2099-10-04T08:00")
                .putBoolean("capsuleLocked", true).putBoolean("localRemindersEnabled", true)
                .putString("anniversaryName", "").commit())
            val oldName = "local-${LocalRelationshipReminderScheduler.plan(context, "capsule", now)!!.key}"
            testWorkNames += oldName
            LocalRelationshipReminderScheduler.schedule(context)
            val originalWork = manager.getWorkInfosForUniqueWork(oldName).get(10, TimeUnit.SECONDS).single()
            assertEquals(WorkInfo.State.ENQUEUED, originalWork.state)

            LocalRelationshipReminderScheduler.schedule(context)
            assertEquals(originalWork.id, manager.getWorkInfosForUniqueWork(oldName).get(10, TimeUnit.SECONDS).single().id)

            assertTrue(tools.edit().putString("capsuleUnlockDate", "2099-10-04T08:01").commit())
            val newName = "local-${LocalRelationshipReminderScheduler.plan(context, "capsule", now)!!.key}"
            testWorkNames += newName
            LocalRelationshipReminderScheduler.schedule(context)
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork(oldName).get(10, TimeUnit.SECONDS).single().state)
            assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfosForUniqueWork(newName).get(10, TimeUnit.SECONDS).single().state)
            assertEquals("原件", tools.getString("capsuleBody", null))
        } finally {
            try {
                testWorkNames.forEach { manager.cancelUniqueWork(it).result.get(10, TimeUnit.SECONDS) }
            } finally {
                restore(tools, originalTools)
                restore(state, originalState)
                LocalRelationshipReminderScheduler.schedule(context)
            }
        }
    }

    private fun restore(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
            }
        }
        check(editor.commit())
    }
}
