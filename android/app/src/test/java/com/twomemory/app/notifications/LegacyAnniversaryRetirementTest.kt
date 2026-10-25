package com.twomemory.app.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyAnniversaryRetirementTest {
    @Test fun installationLevelDateCannotQueueAnAnniversaryWithoutAnOwnedSharedConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        prefs.edit().putString("anniversaryName", "没有归属的旧日期").putString("anniversaryDate", "2020-09-24")
            .putBoolean("anniversaryRepeats", true).putString("anniversaryReminderTime", "09:00").commit()
        assertNull(LocalRelationshipReminderScheduler.plan(context, "anniversary", 0L))
        assertEquals("2020-09-24", prefs.getString("anniversaryDate", null))
    }
}
