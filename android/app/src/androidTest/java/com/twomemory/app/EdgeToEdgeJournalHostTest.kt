package com.twomemory.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.twomemory.sync.SyncEngineRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class EdgeToEdgeJournalHostTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun actualActivityCoverHasNoStatusBarStripAndEditorChangesIconContrast() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = SyncSession.sessionPrefs(context)
        val backup = prefs.all
        val oldCompletedHook = SyncEngineRegistry.onCycleCompleted
        val oldFactory = runCatching { SyncEngineRegistry.factory }.getOrNull()
        val couple = UUID.randomUUID()
        try {
            SyncSession.save(context, "local-host-test", couple, UUID.randomUUID(), "http://127.0.0.1:1/")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.onNodeWithContentDescription("首页半屏背景图").assertIsDisplayed()
                val cover = compose.onNodeWithContentDescription("首页半屏背景图").fetchSemanticsNode().boundsInRoot
                assertTrue("The cover begins behind the status bar, not below a separate strip", cover.top <= 1f)
                scenario.onActivity {
                    assertEquals(0, it.window.statusBarColor)
                    assertFalse(androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars)
                }
                File(context.getExternalFilesDir("visual-qa"), "actual-home-window-v9.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                compose.onNodeWithContentDescription("新建记录").performClick()
                compose.waitForIdle()
                scenario.onActivity {
                    assertTrue(androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars)
                }
            }
        } finally {
            WorkManager.getInstance(context).cancelUniqueWork("moon-letter-sync-$couple").result.get()
            prefs.edit().clear().apply {
                backup.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                } }
            }.commit()
            if (oldFactory != null) SyncEngineRegistry.factory = oldFactory
            SyncEngineRegistry.onCycleCompleted = oldCompletedHook
        }
    }
}
