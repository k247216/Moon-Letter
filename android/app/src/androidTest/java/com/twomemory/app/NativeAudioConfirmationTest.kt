package com.twomemory.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.AudioRecordingPanel
import com.twomemory.editor.RecordedAudio
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Requires the coordinator to grant the app mic permission; never silently skips capture. */
class NativeAudioConfirmationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun microphoneToPreviewToConfirmProducesARealPlayableOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        var result: RecordedAudio? = null
        var dismissed = false
        try {
            compose.setContent { TwoMemoryTheme {
                AudioRecordingPanel(onConfirmed = { result = it }, onDismiss = { dismissed = true })
            } }
            compose.onNodeWithText("开始录音").performClick()
            compose.onNodeWithText("停止录音").assertIsDisplayed()
            SystemClock.sleep(1800) // Actual microphone capture, not fabricated waveform or file bytes.
            compose.onNodeWithText("停止录音").performClick()
            compose.onNodeWithText("试听").assertIsDisplayed().performClick()
            compose.onNodeWithText("停止试听").assertIsDisplayed()
            compose.onNodeWithText("确认加入").performClick()
            compose.runOnIdle { assertTrue(dismissed); assertNotNull(result) }
            val audio = result!!
            assertTrue(audio.file.isFile)
            assertTrue(audio.durationMs >= 1000)
            assertEquals(audio.sizeBytes, audio.file.length())
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(audio.file.absolutePath)
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            } finally { metadata.release() }
        } finally { result?.file?.delete() }
    }
}
