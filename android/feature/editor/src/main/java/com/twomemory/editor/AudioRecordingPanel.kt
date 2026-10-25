package com.twomemory.editor

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@Composable
fun AudioRecordingPanel(onConfirmed: (RecordedAudio) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = remember(context) { AudioRecordingSession(context.applicationContext) }
    val dismiss by rememberUpdatedState(onDismiss)
    var recording by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<RecordedAudio?>(null) }
    var playing by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableStateOf(0) }
    var amplitudeSamples by remember { mutableStateOf<List<Int>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionPending by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    var resumed by remember { mutableStateOf(true) }
    // Permission UI can pause the host. Grant never starts a microphone in the
    // callback; the user starts explicitly after returning to the foreground.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (active) error = if (granted) "麦克风已允许，点击开始录音" else "未允许麦克风，请允许后再录音"
    }
    val cancel = { active = false; session.cancel(); dismiss() }
    DisposableEffect(context, session) {
        val app = context.applicationContext as Application
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) {
                if (activity !== owner) return
                resumed = false
                val interrupted = session.isRecording
                session.interrupt()
                recording = false
                playing = false
                if (interrupted) { preview = null; elapsed = 0; error = "录音已中断，请重新录制" }
            }
            override fun onActivityResumed(activity: Activity) { if (activity === owner) resumed = true }
            override fun onActivityDestroyed(activity: Activity) { if (activity === owner) { active = false; session.cancel() } }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        app.registerActivityLifecycleCallbacks(callback)
        onDispose { active = false; app.unregisterActivityLifecycleCallbacks(callback); session.cancel() }
    }
    LaunchedEffect(session) {
        while (true) {
            delay(100)
            elapsed = session.elapsedMs
            amplitude = session.amplitude
            // Only actual microphone readings enter the visual history. Playback
            // retains the recorded trace; it does not invent live audio levels.
            if (recording && session.isRecording) amplitudeSamples = (amplitudeSamples + amplitude).takeLast(32)
            playing = session.isPlaying
            if (recording && !session.isRecording) { recording = false; error = "录音已中断，请重新录制" }
        }
    }
    Dialog(onDismissRequest = cancel) {
        RecordingCardContent(
            recording = recording,
            hasPreview = preview != null,
            playing = playing,
            elapsedMs = elapsed,
            amplitudeSamples = amplitudeSamples,
            error = error,
            resumed = resumed,
            permissionPending = permissionPending,
            onRecord = {
                error = null
                if (recording) {
                    runCatching { session.stop() }.onSuccess { preview = it }
                        .onFailure { error = it.message ?: "录音未能保存，请重录" }
                    recording = false
                } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionPending = true
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    runCatching { session.start() }.onSuccess { amplitudeSamples = emptyList(); recording = true }
                        .onFailure { error = "无法使用麦克风，请检查权限或稍后重试" }
                }
            },
            onPlay = {
                if (playing) session.releasePlayer()
                else runCatching { session.play() }.onFailure { error = "这段录音无法试听，请重录" }
                playing = session.isPlaying
            },
            onRerecord = { session.cancel(); preview = null; error = null; elapsed = 0; playing = false; amplitudeSamples = emptyList() },
            onConfirm = {
                val audio = session.confirm()
                if (audio == null) error = "录音文件不可用，请重录"
                else { onConfirmed(audio); active = false; dismiss() }
            },
            onCancel = cancel,
        )
    }
}

/** Presentation only: callers can mount recording/preview states without a MIC,
 * permission launcher, Activity or audio file. Samples are raw MediaRecorder
 * amplitudes (0..32767), oldest first; an empty trace is silent, never animated. */
@Composable
fun RecordingCardContent(
    recording: Boolean = false,
    hasPreview: Boolean = false,
    playing: Boolean = false,
    elapsedMs: Long = 0,
    amplitudeSamples: List<Int> = emptyList(),
    error: String? = null,
    resumed: Boolean = true,
    permissionPending: Boolean = false,
    onRecord: () -> Unit = {},
    onPlay: () -> Unit = {},
    onRerecord: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ink = Color(0xFF59483F)
    val muted = Color(0xFF77685C)
    val rose = Color(0xFF98594F)
    val cream = Color(0xFFFFF9EE)
    val line = Color(0xFFE5D5BE)
    val status = when {
        permissionPending -> "正在等麦克风许可"
        recording -> "正在录音，把想说的话慢慢说"
        playing -> "正在试听这段声音"
        hasPreview -> "声音留下了，听听再加入日记"
        else -> "把这一刻，轻轻说给 TA 听"
    }
    val seconds = elapsedMs.coerceAtLeast(0) / 1_000
    Surface(
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cream,
        contentColor = ink,
        border = BorderStroke(1.dp, line),
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("· 声音小记 ·", color = muted, style = MaterialTheme.typography.bodyLarge)
            Text("留下一段声音", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(status, color = muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFF6EDDD), RoundedCornerShape(20.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                    fontFamily = FontFamily.Monospace, fontSize = 36.sp, color = ink,
                    modifier = Modifier.semantics { contentDescription = "录音时长" })
                val trace = if (recording || hasPreview) amplitudeSamples.takeLast(32) else emptyList()
                Canvas(Modifier.fillMaxWidth().height(72.dp).semantics {
                    contentDescription = "录音波形"
                    stateDescription = when {
                        recording -> "麦克风振幅：${trace.lastOrNull() ?: 0}"
                        hasPreview -> "已录制声音的波形，试听时保持静止"
                        else -> "等待录音"
                    }
                }) {
                    val center = size.height / 2
                    drawLine(line, Offset(0f, center), Offset(size.width, center), 1.dp.toPx())
                    val spacing = size.width / 32
                    trace.forEachIndexed { index, raw ->
                        val level = sqrt(raw.coerceIn(0, 32767) / 32767f)
                        val halfHeight = level * (center - 4.dp.toPx())
                        val x = (32 - trace.size + index + 0.5f) * spacing
                        // A zero reading is a resting dot, not a fabricated wave.
                        if (raw <= 0) drawCircle(line, 1.5.dp.toPx(), Offset(x, center))
                        else drawLine(rose, Offset(x, center - halfHeight), Offset(x, center + halfHeight),
                            4.dp.toPx(), StrokeCap.Round)
                    }
                }
                Text(if (hasPreview) "这一小段，留给我们" else "波形跟着真实声音起伏", color = muted,
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
            if (error != null) Text(error, color = rose, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (!hasPreview) {
                Button(
                    onClick = onRecord,
                    enabled = resumed && !permissionPending,
                    modifier = Modifier.size(116.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = rose, contentColor = cream),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.size(30.dp)) {
                            if (recording) drawRoundRect(cream, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            else {
                                val stroke = 2.dp.toPx()
                                drawRoundRect(cream, Offset(size.width * .35f, 0f), Size(size.width * .3f, size.height * .6f),
                                    androidx.compose.ui.geometry.CornerRadius(size.width * .15f), style = Stroke(stroke))
                                drawArc(cream, 0f, 180f, false, Offset(size.width * .15f, size.height * .2f),
                                    Size(size.width * .7f, size.height * .6f), style = Stroke(stroke, cap = StrokeCap.Round))
                                drawLine(cream, Offset(size.width / 2, size.height * .8f), Offset(size.width / 2, size.height), stroke)
                                drawLine(cream, Offset(size.width * .3f, size.height), Offset(size.width * .7f, size.height), stroke, StrokeCap.Round)
                            }
                        }
                        Text(if (recording) "停止录音" else "开始录音", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(if (recording) "说完后，点一下停止" else "点一下，开始收集声音", color = muted,
                    style = MaterialTheme.typography.labelMedium)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onPlay, enabled = resumed, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (playing) "停止试听" else "试听", color = ink)
                    }
                    OutlinedButton(onClick = onRerecord, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("重录", color = ink) }
                }
                Button(onClick = onConfirm, enabled = resumed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = rose, contentColor = cream)) { Text("确认加入") }
            }
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消", color = muted) }
        }
    }
}
