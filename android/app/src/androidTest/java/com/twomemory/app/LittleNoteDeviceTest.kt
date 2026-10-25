package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.EntryVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LittleNoteDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun keyboardOpenStillAllowsPostingASentence() {
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("expectedLayout") == "tablet") {
            val config = compose.activity.resources.configuration
            assertTrue("Tablet acceptance must really run in a landscape tablet window",
                config.screenWidthDp >= 600 && config.screenWidthDp > config.screenHeightDp)
        }
        var text by mutableStateOf(""); var posted = ""
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            LittleNoteComposer(text, { text = it }, EntryVisibility.SHARED, {}, false, false, null,
                {}, { posted = text }, {})
        } } }
        compose.onNodeWithContentDescription("便签正文").performClick().performTextInput("今天也想你。")
        compose.waitUntil(8_000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithContentDescription("贴好便签").assertIsDisplayed().performClick()
        assertEquals("今天也想你。", posted)
    }
}
