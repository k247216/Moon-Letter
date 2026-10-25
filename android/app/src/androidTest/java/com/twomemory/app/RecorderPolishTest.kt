package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.AudioRecordingPanel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class RecorderPolishTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recorderHasAVisualVoiceAreaAndCancelNeverConfirms() {
        var confirmed = false; var dismissed = false
        compose.setContent { TwoMemoryTheme { AudioRecordingPanel({ confirmed = true }, { dismissed = true }) } }
        compose.onNodeWithContentDescription("录音波形").assertExists()
        compose.onNodeWithText("开始录音").assertIsDisplayed()
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa")!!.apply { mkdirs() }
        java.io.File(folder, "recorder-v25.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(confirmed) }
    }
}
