package com.twomemory.app
import com.twomemory.designsystem.PaperTextButton as TextButton

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twomemory.designsystem.*
import com.twomemory.model.EntryVisibility
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun LittleNoteRoute(onClose: () -> Unit) {
    val context = LocalContext.current
    val binding = remember { requireNotNull(SyncSession.captureBinding(context)) }
    val store = remember { LittleNoteStore(context, binding.session) }
    var pending by remember { mutableStateOf(store.load()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(binding) {
        try { pending = restoreLittleNote(context, binding) }
        catch (failure: Exception) { error = failure.message ?: "小纸条还没翻开，请再试一次" }
        finally { restoring = false }
    }
    fun keep(text: String, visibility: EntryVisibility) {
        pending = pending.copy(text = text, visibility = visibility)
        try { store.keep(text, visibility); error = null }
        catch (_: Exception) { error = "小纸条暂时没存好，请不要退出，再试一次" }
    }
    BackHandler { if (!busy && !saved && !restoring) onClose() }
    LittleNoteComposer(pending.text, { keep(it, pending.visibility) }, pending.visibility,
        { keep(pending.text, it) }, busy || restoring, saved, error, onClose, {
            if (!busy && !saved) {
                busy = true
                val submitted = pending
                scope.launch {
                    try {
                        store.keep(submitted.text, submitted.visibility)
                        val restored = restoreLittleNote(context, binding)
                        if (restored != submitted) { pending = restored; return@launch }
                        publishLittleNote(context, binding, submitted)
                        store.clearIfSame(submitted)
                        // The local note is safe already. Background scheduling cannot turn it into a failed save.
                        runCatching { com.twomemory.sync.SyncWorker.enqueue(context, binding.session.coupleId) }
                        saved = true
                    } catch (failure: Exception) {
                        error = failure.message ?: "这张便签还没贴好，请再试一次"
                    } finally { busy = false }
                }
            }
        }, onClose)
}

@Composable
internal fun LittleNoteComposer(text: String, onTextChange: (String) -> Unit,
    visibility: EntryVisibility, onVisibility: (EntryVisibility) -> Unit, busy: Boolean,
    saved: Boolean, error: String?, onClose: () -> Unit, onPublish: () -> Unit, onPosted: () -> Unit) {
    var unfolded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val inputFocus = remember { FocusRequester() }
    val opening by animateFloatAsState(if (unfolded) 1f else 0f, tween(220), label = "paper-unfold")
    val closing by animateFloatAsState(if (saved) 1f else 0f, tween(280), label = "paper-settle")
    val latestPosted by rememberUpdatedState(onPosted)
    LaunchedEffect(Unit) { unfolded = true }
    LaunchedEffect(busy, saved) { if (!busy && !saved) inputFocus.requestFocus() }
    LaunchedEffect(saved) { if (saved) { focus.clearFocus(); delay(340); latestPosted() } }
    val ink = TwoMemoryColors.WarmBeigeInk
    val muted = TwoMemoryColors.WarmBeigeMuted
    Column(Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().imePadding().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, enabled = !busy && !saved) { Text("先收好") }
            Spacer(Modifier.weight(1f))
            Text("一小句，也很好", style = TwoMemoryTypography.caption, color = muted)
        }
        Column(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(top = 24.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · EEEE", java.util.Locale.CHINA)),
                style = TwoMemoryTypography.caption, color = muted)
            Spacer(Modifier.height(18.dp))
            StickyNotePaper(Modifier.fillMaxWidth().graphicsLayer {
                scaleX = .96f + opening * .04f - closing * .025f
                scaleY = scaleX
                rotationZ = (1f - opening) * -1.6f + closing * -1f
                translationY = closing * -12.dp.toPx()
                alpha = 1f - closing * .25f
            }, mine = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("随手，留一句", style = TwoMemoryTypography.caption, color = muted)
                    Spacer(Modifier.weight(1f))
                    Text("♡", style = TwoMemoryTypography.title, color = TwoMemoryColors.WarmBeigeAccent)
                }
                BasicTextField(value = text, onValueChange = { if (it.codePointCount(0, it.length) <= 500) onTextChange(it) },
                    enabled = !busy && !saved, textStyle = TwoMemoryTypography.body.copy(color = ink, fontSize = 20.sp, lineHeight = 32.sp),
                    cursorBrush = SolidColor(TwoMemoryColors.WarmBeigeAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).focusRequester(inputFocus)
                        .semantics { contentDescription = "便签正文" },
                    decorationBox = { field -> Box {
                        if (text.isEmpty()) Text("今天想留给你的一小句…", style = TwoMemoryTypography.body.copy(fontSize = 20.sp,
                            lineHeight = 32.sp), color = muted.copy(alpha = .68f))
                        field()
                    } })
                if (text.codePointCount(0, text.length) >= 400) Text("${text.codePointCount(0, text.length)} / 500",
                    style = TwoMemoryTypography.metadata, color = muted, modifier = Modifier.align(Alignment.End))
            }
            Spacer(Modifier.height(12.dp))
            if (saved) Text("贴好啦 ♡", style = TwoMemoryTypography.title, color = TwoMemoryColors.WarmBeigeSage)
            else Text("不必写完整，想到哪里就写到哪里。", style = TwoMemoryTypography.caption, color = muted,
                textAlign = TextAlign.Center)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = TwoMemoryTypography.caption,
                modifier = Modifier.padding(top = 12.dp)) }
        }
        Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !busy && !saved, onClick = { onVisibility(if (visibility == EntryVisibility.SHARED)
                EntryVisibility.PRIVATE else EntryVisibility.SHARED) }) {
                Text(if (visibility == EntryVisibility.SHARED) "给我们看" else "先收在心里", style = TwoMemoryTypography.caption)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onPublish, enabled = text.isNotBlank() && !busy && !saved,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "贴好便签" }) {
                Text(if (busy) "收好这句话…" else if (saved) "贴好啦" else "贴进手账", style = TwoMemoryTypography.button)
            }
        }
    }
}
