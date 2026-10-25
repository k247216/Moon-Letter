package com.twomemory.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.MusicMemoryCard
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorAttachmentToolbar
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)


class MusicAttachmentPlatformTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val submitted = mutableListOf<Triple<String, String, String>>()
    private val songUrl = "https://music.163.com/song?id=186016"

    @Test
    fun linkAppearsBeforeBothAccessibleSupplementaryFields() {
        openDialog()
        assertLinkFirst()
        field("歌曲名称").assertIsDisplayed().assert(hasSetTextAction())
        field("歌手 / 专辑").assertIsDisplayed().assert(hasSetTextAction())
    }

    @Test fun artistAloneCannotCreateAMeaninglessMusicAttachment() {
        openDialog()
        editField("歌手 / 专辑", "合成歌手")
        compose.onNodeWithText("加入这一页").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    @Test fun aDirectLinkNeedsNoInventedSongMetadata() {
        openDialog()
        editField("播放链接", "  $songUrl  ")
        compose.onNodeWithText("加入这一页").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("", "", songUrl)), submitted) }
    }

    @Test fun aSongTitleWithoutALinkCanStillBeKeptAsAMusicNote() {
        openDialog()
        editField("歌曲名称", "  暮色里的歌  ")
        editField("歌手 / 专辑", "  合成歌手  ")
        compose.onNodeWithText("加入这一页").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(Triple("暮色里的歌", "合成歌手", "")), submitted) }
    }

    @Test fun anUnsafeLinkAloneCannotBeSavedAsAnOpenableSong() {
        openDialog()
        for (unsafe in listOf("javascript:alert(1)", "file:///synthetic/song.mp3", "https://user:pass@example.com/song")) {
            editField("播放链接", unsafe)
            compose.onNodeWithText("加入这一页").assertIsNotEnabled()
        }
        compose.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    @Test fun previewReflectsOnlyTheSongAndArtistActuallyTyped() {
        openDialog()
        editField("播放链接", songUrl)
        editField("歌曲名称", "暮色里的歌")
        editField("歌手 / 专辑", "合成歌手")
        // The edit field and preview must both expose the supplied metadata;
        // no remote lookup or made-up metadata is involved in this fixture.
        compose.onAllNodesWithText("暮色里的歌").assertCountEquals(2)
        compose.onAllNodesWithText("合成歌手").assertCountEquals(2)
        compose.runOnIdle { assertTrue("Preview must not submit an attachment", submitted.isEmpty()) }
    }

    @Test fun cancellationDoesNotAddAPartialSong() {
        openDialog()
        editField("播放链接", songUrl)
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    @Test fun openMusicDialogAndUnsubmittedFieldsSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TwoMemoryTheme {
            Column(Modifier.fillMaxSize().background(TwoMemoryColors.WarmBeigeBackground).padding(24.dp)) {
                EditorAttachmentToolbar(onMusicAdded = { title, artist, url -> submitted += Triple(title, artist, url) })
            }
        } }
        settleAndroidFrames()
        clickMusic()
        editField("播放链接", songUrl)
        editField("歌曲名称", "暮色里的歌")
        editField("歌手 / 专辑", "合成歌手")

        restoration.emulateSavedInstanceStateRestore()
        settleAndroidFrames()

        compose.onNode(isDialog()).assertExists()
        field("播放链接").assert(hasText(songUrl))
        field("歌曲名称").assert(hasText("暮色里的歌"))
        field("歌手 / 专辑").assert(hasText("合成歌手"))
        compose.runOnIdle { assertTrue("Restoration must not submit the unfinished song", submitted.isEmpty()) }
        compose.onNodeWithText("加入这一页").assertIsEnabled().performClick()
        settleAndroidFrames()
        compose.runOnIdle { assertEquals(listOf(Triple("暮色里的歌", "合成歌手", songUrl)), submitted) }
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

    @Test fun capturesLinkFirstMusicDialogOnPhone() = captureDialog("music-dialog-phone.png")


    @Test fun capturesMusicReaderOnPhone() = captureReader("music-reader-phone.png")


    private fun openDialog() {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            Column(Modifier.fillMaxSize().background(TwoMemoryColors.WarmBeigeBackground).padding(24.dp)) {
                EditorAttachmentToolbar(onMusicAdded = { title, artist, url -> submitted += Triple(title, artist, url) })
            }
        } } }
        settleAndroidFrames()
        clickMusic()
    }

    private fun clickMusic() {
        val click = requireNotNull(compose.onNodeWithContentDescription("添加音乐")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.runOnIdle { assertTrue(click()) }
        settleAndroidFrames()
        compose.onNode(isDialog()).assertExists()
    }

    private fun settleAndroidFrames() {
        compose.waitForIdle()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun editField(label: String, value: String) {
        // Invoke the actual field callback without focusing it or opening IME.
        val setText = requireNotNull(field(label).fetchSemanticsNode().config[SemanticsActions.SetText].action)
        compose.runOnIdle { assertTrue(setText(AnnotatedString(value))) }
        compose.waitForIdle()
    }

    private fun assertLinkFirst() {
        val link = field("播放链接").fetchSemanticsNode().boundsInRoot
        val title = field("歌曲名称").fetchSemanticsNode().boundsInRoot
        val artist = field("歌手 / 专辑").fetchSemanticsNode().boundsInRoot
        assertTrue("The pasteable link must precede the optional song title", link.bottom <= title.top)
        assertTrue("Optional fields must remain separate accessible inputs", title.bottom <= artist.top)
    }

    private fun showReader(url: String) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            Column(Modifier.fillMaxSize().background(TwoMemoryColors.WarmBeigeBackground).padding(24.dp)) {
                MusicMemoryCard("暮色里的歌", "合成歌手", url)
            }
        } } }
    }

    private fun captureDialog(name: String) {
        openDialog()
        editField("播放链接", songUrl)
        editField("歌曲名称", "暮色里的歌")
        editField("歌手 / 专辑", "合成歌手")
        assertLinkFirst()
        compose.onNodeWithText("加入这一页").assertIsDisplayed().assertIsEnabled()
        savePixels(name, compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
    }

    private fun captureReader(name: String) {
        showReader(songUrl)
        compose.onNodeWithText("暮色里的歌").assertIsDisplayed()
        compose.onNodeWithText("打开歌曲").assertIsDisplayed().assertIsEnabled()
        savePixels(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun savePixels(name: String, bitmap: Bitmap) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-v41")!!.absolutePath).apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
