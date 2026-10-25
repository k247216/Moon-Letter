package com.twomemory.editor

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real microphone/decoder checks. Run in a host declaring RECORD_AUDIO.
 * These validate a real local original, not audible quality on a physical device.
 */
@RunWith(AndroidJUnit4::class)
class AudioRecordingSessionTest {
    @get:Rule val compose = createComposeRule()

    private fun withMicrophone(test: (AudioRecordingSession) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assumeTrue("Host must declare RECORD_AUDIO; run this in the app host for real microphone verification",
            info.requestedPermissions?.contains(Manifest.permission.RECORD_AUDIO) == true)
        assumeTrue("Main coordinator must grant RECORD_AUDIO before running microphone tests",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        compose.setContent { Text("录音测试宿主") }
        compose.waitForIdle()
        val session = AudioRecordingSession(context)
        try { test(session) } finally {
            compose.runOnIdle { session.cancel() }
        }
    }

    @Test fun deniedMicrophoneLeavesNoPendingFileOrAttachment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Run denial case in an ungranted host",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        compose.setContent { Text("拒权限测试宿主") }
        compose.waitForIdle()
        val directory = File(context.filesDir, "recordings")
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        val session = AudioRecordingSession(context)
        compose.runOnIdle {
            assertTrue(runCatching { session.start() }.isFailure)
            assertFalse(session.isRecording)
            assertNull(session.confirm())
            session.cancel()
        }
        assertEquals(before, directory.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test fun recordingStopPreviewConfirmKeepsDecodableOriginalAfterDisposal() = withMicrophone { session ->
        var audio: RecordedAudio? = null
        try {
            compose.runOnIdle { session.start(); assertTrue(session.isRecording) }
            SystemClock.sleep(1_600) // Physical capture time; no synthetic timer/bytes.
            compose.runOnIdle {
                val preview = session.stop()
                assertFalse(session.isRecording)
                assertTrue(preview.durationMs >= 1_000)
                assertEquals(preview.file.length(), preview.sizeBytes)
                session.play()
                assertTrue(session.isPlaying)
                audio = session.confirm()
                assertNotNull(audio)
                assertFalse(session.isPlaying)
                session.cancel()
            }
            val saved = audio!!
            assertTrue(saved.file.isFile)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(saved.file.absolutePath)
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            } finally { metadata.release() }
        } finally { audio?.file?.delete() }
    }

    @Test fun interruptionDiscardsOnlyPendingOriginalAndReleasesMicrophone() = withMicrophone { session ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val unrelated = File.createTempFile("saved-", ".m4a", directory).apply { writeText("previous original") }
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        try {
            compose.runOnIdle { session.start() }
            SystemClock.sleep(300)
            compose.runOnIdle {
                session.interrupt()
                assertFalse(session.isRecording)
                assertNull(session.confirm())
                // Reacquire the hardware immediately; an unreleased recorder fails here.
                session.start()
                session.cancel()
            }
            assertEquals(before, directory.listFiles().orEmpty().map { it.name }.toSet())
            assertEquals("previous original", unrelated.readText())
        } finally { unrelated.delete() }
    }

    @Test fun stoppingTooSoonCannotConfirmEvenIfEncoderProducedBytes() = withMicrophone { session ->
        compose.runOnIdle { session.start() }
        SystemClock.sleep(100)
        compose.runOnIdle {
            assertTrue(runCatching { session.stop() }.isFailure)
            assertFalse(session.isRecording)
            assertNull(session.confirm())
        }
    }
}
