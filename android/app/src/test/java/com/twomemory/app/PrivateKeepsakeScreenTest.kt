package com.twomemory.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivateKeepsakeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun privateLetterKeepsPrivacyAndHasAWorkingSaveAction() {
        val card = CapsuleCard(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "给未来的我们", "愿每一个平凡的日子，都有你。", "2027-05-09", "PRIVATE", false, "")
        var saved: CapsuleCard? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent { TwoMemoryTheme { CapsuleEditor(card, false, null, {}, { saved = it }) } }
        }
        compose.onNodeWithText("给未来的一封信").assertIsDisplayed()
        compose.onNodeWithText("这封信只留给自己，对方不会看到信封或正文。").performScrollTo().assertIsDisplayed()
        capture("capsule-editor-phone.png")
        compose.onNodeWithText("保存到云端信箱").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("PRIVATE", saved?.visibility); assertEquals(card.body, saved?.body) }
    }

    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("../../releases/v32/$name")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun accountSwitchDisposesRememberedScreenIdentityAndDatabase() {
        val context = compose.activity
        val couple = UUID.randomUUID(); val a = UUID.randomUUID(); val b = UUID.randomUUID()
        SyncSession.save(context, "a", couple, a, "http://127.0.0.1:9")
        compose.activity.runOnUiThread {
            compose.activity.setContent { SessionScopedContent {
                val handle = remember { SyncSession.load(context)!! }
                Text("${handle.userId}|${handle.databaseName}")
            } }
        }
        val first = SyncSession.load(context)!!
        compose.onNodeWithText("$a|${first.databaseName}").assertExists()
        compose.runOnIdle { SyncSession.save(context, "b", couple, b, "http://127.0.0.1:9") }
        val second = SyncSession.load(context)!!
        compose.onNodeWithText("$b|${second.databaseName}").assertExists()
        compose.onNodeWithText("$a|${first.databaseName}").assertDoesNotExist()
    }
}
