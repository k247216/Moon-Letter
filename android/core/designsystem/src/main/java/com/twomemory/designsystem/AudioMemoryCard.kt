package com.twomemory.designsystem

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaPlayer
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/** Only play a real local original; never simulate progress for an unavailable attachment. */
@Composable
fun AudioMemoryCard(localPath: String?, assetId: String?, modifier: Modifier = Modifier, title: String = "") {
    val context = LocalContext.current
    var file by remember(localPath, assetId) { mutableStateOf(localPath?.let(::File)?.takeIf { it.isFile && it.length() > 0 }) }
    var loading by remember(localPath, assetId) { mutableStateOf(false) }
    var request by remember(localPath, assetId) { mutableIntStateOf(0) }
    val holder = remember(localPath, assetId) { arrayOfNulls<MediaPlayer>(1) }
    var playing by remember(localPath, assetId) { mutableStateOf(false) }
    var error by remember(localPath, assetId) { mutableStateOf<String?>(null) }
    LaunchedEffect(localPath, assetId, request) {
        if (file != null || assetId == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            file = EntryPhotos.localOriginal(context, localPath, assetId)
            if (file == null) error = "暂时无法读取录音，点按重试"
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "下载未完成，点按重试" }
        finally { loading = false }
    }
    val release = { holder[0]?.release(); holder[0] = null; playing = false }
    DisposableEffect(context, localPath, assetId) {
        val app = context.applicationContext as? Application
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) { if (activity === owner) release() }
            override fun onActivityDestroyed(activity: Activity) { if (activity === owner) release() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        app?.registerActivityLifecycleCallbacks(callback)
        onDispose { app?.unregisterActivityLifecycleCallbacks(callback); release() }
    }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(TwoMemoryIcons.Voice, null, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title.trim().ifBlank { "留给你的声音" },
                    style = if (title.isBlank()) TwoMemoryTypography.body else TwoMemoryTypography.title)
                Text(error ?: if (file != null) "语音回忆" else if (loading) "正在准备声音…" else "录音文件暂不可用",
                    style = TwoMemoryTypography.caption)
            }
            TextButton(enabled = !loading && (file != null || assetId != null), onClick = {
                if (file == null) { request++; return@TextButton }
                if (playing) { release() } else {
                    error = null
                    val player = MediaPlayer()
                    holder[0] = player
                    player.setOnCompletionListener { release() }
                    player.setOnErrorListener { _, _, _ -> error = "这段录音暂时无法播放"; release(); true }
                    player.setOnPreparedListener { prepared ->
                        if (holder[0] === prepared) { prepared.start(); playing = true }
                    }
                    runCatching { player.setDataSource(file!!.absolutePath); player.prepareAsync() }
                        .onFailure { error = "这段录音暂时无法播放"; release() }
                }
            }) { Text(if (playing) "停止" else if (loading) "准备中" else if (file == null && assetId != null) "重试" else "播放") }
        }
    }
}
