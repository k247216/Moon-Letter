package com.twomemory.app

import android.content.ContentProvider
import androidx.activity.compose.setContent
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.compose.ui.graphics.asAndroidBitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import com.twomemory.timeline.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoRecordTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun videoRecordOffersPlaybackInsteadOfAnUnsupportedPlaceholder() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TwoMemoryTheme {
                    EntryDetailScreen(EntryDetailUi(
                        TimelineEntryUi("video-record", "10月9日", "20:00", "我", "小片段"),
                        listOf(EntryBlockUi("video", BlockType.VIDEO, null, null, UUID.randomUUID().toString())),
                        emptyList()), {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("播放视频").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("视频附件暂时无法在本机打开").assertDoesNotExist()
    }

    @Test fun importedVideoKeepsOriginalBytesAndItsVideoExtension() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val uri = Uri.parse("content://video-fixture/movie")
        ShadowContentResolver.registerProviderInternal("video-fixture", VideoMimeProvider())
        val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        val stored = requireNotNull(EntryPhotos.store(context, uri, UUID.randomUUID().toString()))
        val file = File(stored.localPath)
        try {
            assertEquals("video/mp4", stored.mimeType)
            assertEquals("mp4", file.extension)
            assertArrayEquals(bytes, file.readBytes())
        } finally { file.delete() }
    }

    @Test fun mediaImportsUseContainerExtensionsWithoutChangingBytes() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cases = listOf("video/webm" to "webm", "video/quicktime" to "mov",
            "audio/mp4" to "m4a", "audio/mpeg" to "mp3", "audio/ogg" to "ogg",
            "audio/wav" to "wav", "audio/aac" to "aac", "audio/webm" to "webm",
            "audio/flac" to "flac", "audio/amr" to "amr", "audio/opus" to "opus",
            "Video/MP4; codecs=avc1" to "mp4")
        for ((mime, extension) in cases) {
            val uri = Uri.parse("content://video-fixture/$extension")
            ShadowContentResolver.registerProviderInternal("video-fixture", VideoMimeProvider(mime))
            val bytes = byteArrayOf(1, 2, 3, 4)
            org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
            val stored = requireNotNull(EntryPhotos.store(context, uri, UUID.randomUUID().toString()))
            val file = File(stored.localPath)
            try {
                assertEquals(extension, file.extension)
                assertArrayEquals(bytes, file.readBytes())
            } finally { file.delete() }
        }
    }

    @Test fun videoDownloadStartsOnClickAndCanRetryWithoutImagePreview() {
        val previousFile = EntryPhotos.remoteFile
        val previousPreview = EntryPhotos.remotePreview
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val previews = java.util.concurrent.atomic.AtomicInteger()
        val assetId = UUID.randomUUID().toString()
        EntryPhotos.remoteFile = { _, _ -> calls.incrementAndGet(); error("offline") }
        EntryPhotos.remotePreview = { previews.incrementAndGet(); error("video has no image preview") }
        try {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    TwoMemoryTheme {
                        EntryDetailScreen(EntryDetailUi(
                            TimelineEntryUi("video-download", "10月9日", "20:00", "我", "小片段"),
                            listOf(EntryBlockUi("video", BlockType.VIDEO, null, null, assetId)),
                            emptyList()), {}, {}, {})
                    }
                }
            }
            compose.waitForIdle()
            assertEquals(0, calls.get())
            compose.onNodeWithText("播放视频").performScrollTo().performClick()
            compose.waitForIdle()
            // The IO callback starts before its failure is posted back to Compose.
            // Await the visible outcome rather than only the transport invocation.
            compose.waitUntil(10_000) {
                compose.waitForIdle()
                calls.get() == 1 && compose.onAllNodesWithText("视频下载失败，请重试").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("视频下载失败，请重试").assertExists()
            compose.onNodeWithText("重试下载").performClick()
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.waitForIdle(); calls.get() == 2 }
            compose.onNodeWithText("关闭视频").performClick()
            compose.onNodeWithText("重试下载").assertDoesNotExist()
            assertEquals(0, previews.get())
        } finally {
            EntryPhotos.remoteFile = previousFile
            EntryPhotos.remotePreview = previousPreview
        }
    }

    @Test fun closingVideoCancelsDownloadAndDoesNotPublishPartialOriginal() {
        val previous = EntryPhotos.remoteFile
        val assetId = UUID.randomUUID().toString()
        val started = kotlinx.coroutines.CompletableDeferred<File>()
        val cancelled = java.util.concurrent.atomic.AtomicBoolean()
        EntryPhotos.remoteFile = { _, destination ->
            destination.writeBytes(byteArrayOf(1, 2, 3))
            started.complete(destination)
            try { kotlinx.coroutines.awaitCancellation() }
            finally { cancelled.set(true) }
        }
        try {
            showVideo(null, assetId)
            compose.onNodeWithText("播放视频").performClick()
            compose.waitForIdle()
            compose.waitUntil(10_000) { started.isCompleted }
            compose.onNodeWithText("正在下载视频…").assertExists()
            compose.onNodeWithText("关闭视频").performClick()
            compose.waitForIdle()
            val temporary = runBlocking { started.await() }
            compose.waitUntil(10_000) { cancelled.get() && !temporary.exists() }
            assertFalse(File(compose.activity.filesDir, "downloaded-media/$assetId").exists())
        } finally { EntryPhotos.remoteFile = previous }
    }

    @Test fun historicalJpgOriginalIsPassedToNativePlayerAndDecoderRetryKeepsIt() {
        val file = File(compose.activity.filesDir, "legacy-${UUID.randomUUID()}.jpg")
        val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)
        file.writeBytes(bytes)
        try {
            showVideo(file.absolutePath, null)
            compose.onNodeWithText("播放视频").performClick()
            compose.waitForIdle()
            try {
                compose.waitUntil(10_000) { compose.waitForIdle(); nativeVideo() != null }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                val root = compose.onAllNodes(isRoot()).onLast()
                println(root.printToString())
                val bitmap = root.captureToImage().asAndroidBitmap()
                File("../../releases/v32/video-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                throw AssertionError("Video window missing; file=${file.isFile}, bytes=${file.length()}, pathLength=${file.absolutePath.length}, dialogs=${org.robolectric.shadows.ShadowDialog.getShownDialogs().size}", failure)
            }
            val video = requireNotNull(nativeVideo())
            val shadow = org.robolectric.Shadows.shadowOf(video)
            assertEquals(Uri.fromFile(file).toString(), shadow.videoURIString)
            compose.activity.runOnUiThread {
                val player = android.media.MediaPlayer()
                try { shadow.onErrorListener.onError(player, 1, 0) }
                finally { player.release() }
            }
            compose.waitForIdle()
            compose.onNodeWithText("这段视频无法解码或格式不受支持").assertExists()
            compose.onNodeWithText("重试下载").assertDoesNotExist()
            assertEquals(org.robolectric.shadows.ShadowVideoView.STOP, shadow.currentVideoState)
            compose.onNodeWithText("重试播放").performClick()
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.waitForIdle(); nativeVideo()?.let { it !== video } == true }
            val retried = requireNotNull(nativeVideo())
            assertEquals(Uri.fromFile(file).toString(), org.robolectric.Shadows.shadowOf(retried).videoURIString)
            compose.onNodeWithText("关闭视频").performClick()
            compose.waitForIdle()
            assertEquals(org.robolectric.shadows.ShadowVideoView.STOP,
                org.robolectric.Shadows.shadowOf(retried).currentVideoState)
            assertArrayEquals(bytes, file.readBytes())
        } finally { file.delete() }
    }

    @Test fun backgroundingStopsNativeVideoAndDoesNotResumeAutomatically() {
        val file = File(compose.activity.filesDir, "background-${UUID.randomUUID()}.mp4")
        file.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112))
        try {
            showVideo(file.absolutePath, null)
            compose.onNodeWithText("播放视频").performClick()
            compose.waitForIdle()
            compose.waitUntil(10_000) { compose.waitForIdle(); nativeVideo() != null }
            val video = requireNotNull(nativeVideo())
            compose.activity.runOnUiThread { video.start() }
            val shadow = org.robolectric.Shadows.shadowOf(video)
            assertEquals(org.robolectric.shadows.ShadowVideoView.START, shadow.currentVideoState)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertEquals(org.robolectric.shadows.ShadowVideoView.STOP, shadow.currentVideoState)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitForIdle()
            compose.onNodeWithText("关闭视频").assertDoesNotExist()
        } finally { file.delete() }
    }

    private fun showVideo(localPath: String?, assetId: String?) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TwoMemoryTheme { com.twomemory.designsystem.VideoMemoryCard(localPath, assetId) }
            }
        }
        compose.waitForIdle()
    }

    private fun nativeVideo(): android.widget.VideoView? {
        fun find(view: android.view.View): android.widget.VideoView? {
            if (view is android.widget.VideoView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                find(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        // Native controls can create a newer window; the video belongs to the
        // currently shown Compose dialog, not necessarily the last-created one.
        return org.robolectric.shadows.ShadowDialog.getShownDialogs().asReversed().asSequence()
            .filter { it.isShowing }.mapNotNull { it.window?.decorView?.let(::find) }.firstOrNull()
    }
}

class VideoMimeProvider(private val mime: String = "video/mp4") : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = mime
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
