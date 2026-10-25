package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorUiState
import com.twomemory.editor.SharedEditorRoute
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.graphics.Bitmap

class JointMemoryVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sharedPageHasTwoRealNamesAndKeepsPartnersWordsReadOnly() {
        compose.setContent { TwoMemoryTheme {
            SharedEditorRoute(EditorUiState(title = "月亮升起来的时候", body = "你走在我旁边的时候，连空气都是甜的。"),
                onPublish = {}, ownName = "小满", partnerName = "阿屿", partnerBody = "我记住了路边的灯，和你笑起来的样子。")
        } }
        compose.onNodeWithText("一段时光，两种心情").assertIsDisplayed()
        compose.onNodeWithText("阿屿").assertIsDisplayed()
        compose.onNodeWithText("我记住了路边的灯，和你笑起来的样子。").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir("visual-v10")!!
        directory.mkdirs()
        File(directory, "shared-editor.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
