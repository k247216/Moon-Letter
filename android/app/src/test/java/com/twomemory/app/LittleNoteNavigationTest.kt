package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LittleNoteNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun quickEntryRestoresItsOwnNoteAndFullEditorRemainsSeparate() {
        val context = compose.activity
        SyncSession.save(context, "synthetic-note-ui", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        context.runOnUiThread { context.setContent { TwoMemoryTheme { AppNavigation() } } }
        compose.onNodeWithText("记下今天").performClick()
        compose.onNodeWithContentDescription("便签正文").performTextInput("累了也想留一句")
        compose.onNodeWithText("先收好").performClick()
        compose.onNodeWithContentDescription("新建记录").performClick()
        compose.onNodeWithContentDescription("个人记录正文").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("关闭编辑器").performClick()
        compose.onNodeWithText("记下今天").performClick()
        compose.onNodeWithContentDescription("便签正文").assertTextEquals("累了也想留一句")
    }
}
