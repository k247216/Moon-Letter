package com.twomemory.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.AudioMemoryCard
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class NativeAudioCacheTest {
    @get:Rule val compose = createComposeRule()

    @Test fun downloadedAudioReplaysAfterLeavingWithoutAnotherNetworkDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val asset = UUID.randomUUID().toString()
        val originalSource = EntryPhotos.remoteBytes
        val originalFileSource = EntryPhotos.remoteFile
        val visible = mutableStateOf(true)
        var downloads = 0
        val bytes = ByteBuffer.allocate(48044).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(48036); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(48000)
        }.array()
        try {
            EntryPhotos.remoteFile = null
            EntryPhotos.remoteBytes = { downloads++; bytes }
            compose.setContent { TwoMemoryTheme { if (visible.value) AudioMemoryCard(null, asset) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("语音回忆").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("播放").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("停止").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("停止").performClick()
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            EntryPhotos.remoteBytes = { error("Network is offline") }
            compose.runOnIdle { visible.value = true }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("语音回忆").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("播放").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("停止").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("停止").performClick()
            assertEquals(1, downloads)
            assertEquals(bytes.size.toLong(), File(context.filesDir, "downloaded-media/$asset").length())
        } finally {
            EntryPhotos.remoteBytes = originalSource
            EntryPhotos.remoteFile = originalFileSource
            File(context.filesDir, "downloaded-media/$asset").delete()
        }
    }
}
