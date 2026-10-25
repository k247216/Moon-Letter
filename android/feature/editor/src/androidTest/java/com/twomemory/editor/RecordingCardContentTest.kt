package com.twomemory.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the real presentation with supplied state, never a microphone. */
class RecordingCardContentTest {
    @get:Rule val compose = createComposeRule()

    // Catches a fabricated level, unreadable elapsed time, or a stop action
    // accidentally wired to preview/confirmation rather than recording.
    @Test fun recordingShowsSuppliedLevelAndElapsedTimeAndOffersStop() {
        var stops = 0
        compose.setContent {
            TwoMemoryTheme {
                RecordingCardContent(
                    recording = true,
                    elapsedMs = 65_000,
                    amplitudeSamples = listOf(0, 16384, 700),
                    onRecord = { stops++ },
                )
            }
        }
        compose.onNodeWithContentDescription("录音波形")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "麦克风振幅：700"))
        compose.onNodeWithText("1:05").assertIsDisplayed()
        compose.onNodeWithText("停止录音").performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }

    // Catches preview controls routed to the wrong operation or accidentally
    // requiring an Activity/session/audio file just to present a saved take.
    @Test fun previewRoutesListenRerecordConfirmAndCancelIndependently() {
        val actions = mutableListOf<String>()
        compose.setContent {
            TwoMemoryTheme {
                RecordingCardContent(
                    hasPreview = true,
                    elapsedMs = 12_000,
                    amplitudeSamples = listOf(0, 12000, 24000, 0),
                    onPlay = { actions += "listen" },
                    onRerecord = { actions += "rerecord" },
                    onConfirm = { actions += "confirm" },
                    onCancel = { actions += "cancel" },
                )
            }
        }
        compose.onNodeWithText("试听").performClick()
        compose.onNodeWithText("重录").performClick()
        compose.onNodeWithText("确认加入").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(listOf("listen", "rerecord", "confirm", "cancel"), actions) }
    }

    // Catches a visual refactor that allows MIC starts during permission UI.
    @Test fun permissionPendingDisablesStartButKeepsCancellationAvailable() {
        var cancelled = 0
        compose.setContent {
            TwoMemoryTheme {
                RecordingCardContent(permissionPending = true, onCancel = { cancelled++ })
            }
        }
        compose.onNodeWithText("开始录音").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
    }
}
