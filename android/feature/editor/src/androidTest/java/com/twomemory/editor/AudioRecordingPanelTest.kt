package com.twomemory.editor

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioRecordingPanelTest {
    @get:Rule val compose = createComposeRule()

    private fun open(vm: EditorViewModel) {
        var externalLaunch = false
        // Substitute only the external Activity boundary, retaining the real
        // toolbar/panel. A picker regression now leaves a valid Compose tree and
        // fails on the observable external launch, not "no hierarchy".
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    externalLaunch = true
                    dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
                }
            }
        }
        compose.setContent {
            TwoMemoryTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    val actions = rememberEditorAttachmentActions(vm)
                    Button(onClick = actions.pickAudio) { Text("语音工具") }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("语音工具").assertIsDisplayed()
        compose.onNodeWithText("语音工具").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("Voice tool must open the recording panel without leaving the Activity", externalLaunch) }
    }

    @Test fun voiceToolOpensRecordingControlsRatherThanAFilePicker() {
        open(EditorViewModel())
        compose.onNodeWithText("开始录音").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test fun cancellingAnUnstartedRecordingNeverAddsAnAttachment() {
        val vm = EditorViewModel()
        open(vm)
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(vm.state.value.attachments.isEmpty()) }
    }

    // Direct mounting keeps this test in the host Activity even if the toolbar
    // regresses to a picker. The production panel owns its cancellation policy.
    @Test fun panelCancelNeverConfirmsAudio() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            TwoMemoryTheme {
                AudioRecordingPanel(onConfirmed = { confirmed = true }, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("开始录音").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(dismissed && !confirmed) }
    }

    // Catches a recorder that omits its accessible voice visualization. Mount
    // the real panel without starting a session or requesting MIC permission.
    @Test fun unstartedRecorderExposesAccessibleWaveformWithoutMicrophone() {
        compose.setContent {
            TwoMemoryTheme {
                AudioRecordingPanel(onConfirmed = {}, onDismiss = {})
            }
        }
        compose.onNodeWithContentDescription("录音波形").assertIsDisplayed()
        compose.onNodeWithText("开始录音").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }
}
