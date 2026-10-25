package com.twomemory.designsystem

import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.io.File

/** Download an authenticated original only when the reader asks to play it. */
@Composable
fun VideoMemoryCard(localPath: String?, assetId: String?, modifier: Modifier = Modifier) {
    var open by remember(localPath, assetId) { mutableStateOf(false) }
    Surface(modifier, color = TwoMemoryColors.WarmBeigeLine.copy(alpha = .35f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("视频回忆", modifier = Modifier.weight(1f), style = TwoMemoryTypography.body)
            TextButton(onClick = { open = true }) { Text("播放视频") }
        }
    }
    if (open) VideoMemoryDialog(localPath, assetId) { open = false }
}

private class VideoPlayback {
    var view: VideoView? = null
    var controller: MediaController? = null
    var download: Job? = null

    fun release() {
        download?.cancel()
        download = null
        controller?.hide()
        controller = null
        view?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            setOnCompletionListener(null)
            setMediaController(null)
            stopPlayback()
        }
        view = null
    }
}

@Composable
private fun VideoMemoryDialog(localPath: String?, assetId: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playback = remember { VideoPlayback() }
    var attempt by remember { mutableIntStateOf(0) }
    var original by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(true) }
    var downloadFailed by remember { mutableStateOf(false) }
    var decoderFailed by remember { mutableStateOf(false) }
    val close = { playback.release(); onDismiss() }
    val currentClose by rememberUpdatedState(close)

    DisposableEffect(context) {
        val app = context.applicationContext as? Application
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) { if (activity === owner) currentClose() }
            override fun onActivityStopped(activity: Activity) { if (activity === owner) currentClose() }
            override fun onActivityDestroyed(activity: Activity) { if (activity === owner) currentClose() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        app?.registerActivityLifecycleCallbacks(callbacks)
        onDispose { app?.unregisterActivityLifecycleCallbacks(callbacks); playback.release() }
    }

    LaunchedEffect(localPath, assetId, attempt) {
        playback.download = currentCoroutineContext()[Job]
        loading = true
        downloadFailed = false
        decoderFailed = false
        try {
            original = EntryPhotos.localOriginal(context, localPath, assetId)
            if (original == null) { downloadFailed = true; loading = false }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            downloadFailed = true
            loading = false
        } finally { playback.download = null }
    }

    Dialog(onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            TextButton(onClick = close) { Text("关闭视频", color = Color.White) }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val file = original
                if (file != null && !decoderFailed) key(attempt, file.absolutePath) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            FrameLayout(viewContext).apply {
                                val video = VideoView(viewContext)
                                val controls = MediaController(viewContext)
                                playback.view = video
                                playback.controller = controls
                                // VideoView measures to the decoded dimensions while fitting its
                                // parent; centering preserves portrait/landscape aspect with bars.
                                addView(video, FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                                controls.setAnchorView(video)
                                video.setMediaController(controls)
                                video.setOnPreparedListener {
                                    if (playback.view === video) {
                                        loading = false
                                        video.start()
                                        controls.show()
                                    }
                                }
                                video.setOnErrorListener { _, _, _ ->
                                    if (playback.view === video) {
                                        loading = false
                                        decoderFailed = true
                                        playback.release()
                                    }
                                    true // Suppress VideoView's generic native error dialog.
                                }
                                video.setOnCompletionListener {
                                    if (playback.view === video) controls.show(0)
                                }
                                // Android's media extractor sniffs the file bytes. No extension
                                // gate or image decoder: historical .jpg video originals work too.
                                try { video.setVideoURI(Uri.fromFile(file)) }
                                catch (_: Exception) {
                                    loading = false
                                    decoderFailed = true
                                    playback.release()
                                }
                            }
                        },
                        onRelease = { frame ->
                            val video = frame.getChildAt(0) as VideoView
                            if (playback.view === video) playback.release()
                            else video.stopPlayback()
                        },
                    )
                }
                if (loading) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(if (file == null) "正在下载视频…" else "正在准备播放…", color = Color.White)
                }
                if (downloadFailed || decoderFailed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (downloadFailed) "视频下载失败，请重试" else "这段视频无法解码或格式不受支持",
                        color = Color.White)
                    TextButton(onClick = {
                        playback.release()
                        original = null
                        attempt++
                    }) { Text(if (downloadFailed) "重试下载" else "重试播放", color = Color.White) }
                }
            }
        }
    }
}
