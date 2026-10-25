package com.twomemory.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.timeline.EntryDetailScreen
import com.twomemory.timeline.EntryDetailUi
import com.twomemory.timeline.TimelineEntryUi
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CommentKeyboardLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun composerStaysBesideRealKeyboardBeforeAndAfterSending() {
        val ui = mutableStateOf(EntryDetailUi(
            TimelineEntryUi("keyboard-test", "今天 · 10月3日", "21:30", "小满", "晚风里的回应", "今晚也想和你说说话", mine = true),
            blocks = emptyList(), comments = emptyList()))
        compose.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.setContent { TwoMemoryTheme {
                EntryDetailScreen(ui.value, {}, { ui.value = ui.value.copy(draft = it) },
                    { ui.value = ui.value.copy(draft = "") })
            } }
        }
        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("今天也很想你")
        compose.waitUntil(10_000) { imeBottom() > 0 }
        capture("comment-keyboard-before-send.png")
        checkGap()
        compose.onNodeWithText("发送").performClick()
        compose.waitForIdle()
        capture("comment-keyboard-after-send.png")
        checkGap()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10_000) { imeBottom() == 0 }
        compose.waitForIdle()
        capture("comment-keyboard-closed.png")
        val root = compose.activity.window.decorView
        val navigationBottom = WindowInsetsCompat.toWindowInsetsCompat(root.rootWindowInsets)
            .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val fieldBottom = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.bottom
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Closing keyboard must not leave its height behind",
            (root.height - navigationBottom - fieldBottom) / density in -2f..32f)
    }
    private fun imeBottom(): Int = WindowInsetsCompat.toWindowInsetsCompat(
        compose.activity.window.decorView.rootWindowInsets).getInsets(WindowInsetsCompat.Type.ime()).bottom
    private fun checkGap() {
        val density = compose.activity.resources.displayMetrics.density
        val root = compose.activity.window.decorView
        val keyboardTop = root.height - imeBottom()
        val fieldBottom = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.bottom
        val gap = keyboardTop - fieldBottom
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("composer_keyboard_gap_dp", (gap / density).toString()) })
        assertTrue("Composer / keyboard gap is ${gap / density}dp", gap / density in -2f..32f)
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = instrumentation.targetContext.getExternalFilesDir("visual-v14")!!
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
