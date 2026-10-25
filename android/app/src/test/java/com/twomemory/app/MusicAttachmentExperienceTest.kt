package com.twomemory.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.MusicMemoryCard
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicAttachmentExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val songUrl = "https://music.163.com/song?id=186016"

    @Test fun readerOpensOnlyTheProvidedSafeHttpOrHttpsPage() {
        for (url in listOf(songUrl, "http://example.com/song?id=41")) {
            showReader(url)
            compose.onNodeWithText("打开歌曲").performClick()
            compose.runOnIdle {
                val intent = shadowOf(compose.activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals(url, intent.dataString)
                assertTrue(intent.categories.contains(Intent.CATEGORY_BROWSABLE))
            }
        }
    }

    @Test fun readerDoesNotOfferToOpenUnsafeOrCredentialBearingUrls() {
        for (url in listOf("javascript:alert(1)", "file:///synthetic/song", "https://user:pass@example.com/song", "https:///song")) {
            showReader(url)
            compose.onNodeWithText("暮色里的歌").assertIsDisplayed()
            compose.onNodeWithText("打开歌曲").assertDoesNotExist()
        }
    }

    @Test fun aTitleOnlyReaderNoteRetainsItsMetadataWithoutAnOpenAction() {
        showReader("")
        compose.onNodeWithText("暮色里的歌").assertIsDisplayed()
        compose.onNodeWithText("合成歌手").assertIsDisplayed()
        compose.onNodeWithText("打开歌曲").assertDoesNotExist()
    }

    @Test fun capturesMusicReaderOnPhone() = captureReader("music-reader-phone.png")
    @Test @Config(qualifiers = "w960dp-h720dp-mdpi")
    fun capturesMusicReaderOnTablet() = captureReader("music-reader-tablet.png")

    private fun showReader(url: String) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            Column(Modifier.fillMaxSize().background(TwoMemoryColors.WarmBeigeBackground).padding(24.dp)) {
                MusicMemoryCard("暮色里的歌", "合成歌手", url)
            }
        } } }
    }

    private fun captureReader(name: String) {
        showReader(songUrl)
        compose.onNodeWithText("暮色里的歌").assertIsDisplayed()
        compose.onNodeWithText("打开歌曲").assertIsDisplayed().assertIsEnabled()
        savePixels(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun savePixels(name: String, bitmap: Bitmap) {
        val directory = File("../../releases/v43").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
