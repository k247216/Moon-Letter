package com.twomemory.app
import com.twomemory.designsystem.PaperTextButton as TextButton

import android.app.DatePickerDialog
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.network.CapsuleValidation
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Integration: CapsuleLibrary(ownId = user UUID string, coupleId = space UUID string, onBack = ...). */
@Composable
fun CapsuleLibrary(ownId: String, coupleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(SyncSession.load(context)) }
    DisposableEffect(context) {
        val prefs = SyncSession.sessionPrefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            scope.launch { session = SyncSession.load(context) }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        session = SyncSession.load(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val current = session
    if (current == null || current.userId.toString() != ownId || current.coupleId.toString() != coupleId) {
        BackHandler(onBack = onBack)
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            CapsuleEnvelope(Modifier.fillMaxWidth().height(140.dp))
            Text("请连接当前的共同空间，再打开时光信箱。", style = TwoMemoryTypography.body)
        }
        return
    }
    // Rebinding removes any old account's reader/editor and cancels its screen coroutines.
    key(current, ownId, coupleId) { CapsuleLibraryBound(current, onBack) }
}

@Composable
private fun CapsuleLibraryBound(session: SyncSession.Session, onBack: () -> Unit) {
    val context = LocalContext.current
    val ownId = session.userId.toString()
    val store = remember(context, session) { CapsuleCloud.store(context, session) }
    val scope = rememberCoroutineScope()
    var cards by remember(store) { mutableStateOf(store.cards()) }
    var editing by remember { mutableStateOf<CapsuleCard?>(null) }
    var readingId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<CapsuleCard?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val legacyPrefs = remember(context) { context.getSharedPreferences("moon_letter_tools", android.content.Context.MODE_PRIVATE) }
    val hasLegacyDraft = remember(legacyPrefs) {
        !legacyPrefs.getString("capsuleTitle", null).isNullOrEmpty() || !legacyPrefs.getString("capsuleBody", null).isNullOrEmpty()
    }
    fun stillCurrent() = SyncSession.load(context) == session
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "cards" || key == null) scope.launch {
                cards = if (stillCurrent()) store.cards() else emptyList()
            }
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        cards = store.cards()
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun refresh() {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try {
                if (!CapsuleCloud.refresh(context) && stillCurrent()) error = "暂时未能更新信箱，显示上次确认的信件。请检查连接后重试。"
                if (stillCurrent()) cards = store.cards()
            } catch (cancelled: CancellationException) { throw cancelled }
            finally { busy = false }
        }
    }
    LaunchedEffect(session) {
        busy = true
        try {
            if (!CapsuleCloud.refresh(context) && stillCurrent()) error = "暂时未能更新信箱，显示上次确认的信件。请检查连接后重试。"
            if (stillCurrent()) cards = store.cards()
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { busy = false }
    }
    BackHandler {
        if (!busy) when { editing != null -> editing = null; readingId != null -> readingId = null; deleting != null -> deleting = null; else -> onBack() }
    }
    val initial = editing
    if (initial != null) {
        CapsuleEditor(initial, busy, error, onBack = { if (!busy) { editing = null; error = null } }, onSave = { card ->
            busy = true; error = null
            scope.launch {
                try {
                    CapsuleCloud.save(context, card, session)
                    if (stillCurrent()) { cards = store.cards(); editing = null; notice = "信件已保存到云端" }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (stillCurrent()) error = "未能确认保存成功，填写内容仍在这里。请检查连接后重试；也可返回刷新确认云端状态。" }
                finally { busy = false }
            }
        })
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = onBack) { Icon(TwoMemoryIcons.Close, "返回") }
                Text("时光信箱", style = TwoMemoryTypography.title, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = ::refresh) { Text(if (busy) "更新中…" else "刷新") }
            }
            CapsuleEnvelope(Modifier.fillMaxWidth().height(124.dp))
            Text("把今天的心意，寄给未来的我们。", style = TwoMemoryTypography.body)
            Text("共享信件按上海日期由云端拆封；只有作者可以提前看、修改或删除。", style = TwoMemoryTypography.caption,
                color = TwoMemoryColors.WarmBeigeMuted)
        }
        item {
            Button(enabled = !busy, onClick = {
                error = null; notice = null
                editing = CapsuleCard(UUID.randomUUID().toString(), session.coupleId.toString(), ownId, "", "",
                    LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString(), "SHARED", false, "")
            }, modifier = Modifier.fillMaxWidth()) { Text("写一封未来的信") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            notice?.let { Text(it, style = TwoMemoryTypography.caption, modifier = Modifier.padding(top = 12.dp)) }
        }
        if (hasLegacyDraft) item {
            Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeAccent.copy(alpha = .2f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("这台设备还留有旧的本地草稿", style = TwoMemoryTypography.body)
                    Text("旧草稿未关联当前账号，也没有上传。确认是你写的信后，可打开编辑；只有你点击保存才会递交到当前云端空间。",
                        style = TwoMemoryTypography.caption)
                    TextButton(enabled = !busy, onClick = {
                        val fallback = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString()
                        val rawDate = legacyPrefs.getString("capsuleUnlockDate", null).orEmpty()
                        val restoredDate = runCatching { CapsuleValidation.date(rawDate); rawDate }.getOrDefault(fallback)
                        editing = CapsuleCard(UUID.randomUUID().toString(), session.coupleId.toString(), ownId,
                            legacyPrefs.getString("capsuleTitle", null).orEmpty(), legacyPrefs.getString("capsuleBody", null).orEmpty(),
                            restoredDate, "PRIVATE", false, "")
                        error = null; notice = null
                    }) { Text("确认是我的信，打开旧草稿") }
                }
            }
        }
        if (cards.isEmpty()) item {
            Text(if (error == null) "信箱还没有云端信件。写下第一封心意吧。" else "本机暂时没有已确认的云端信件。",
                style = TwoMemoryTypography.body, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(cards.sortedWith(compareBy<CapsuleCard> { it.unlockDate }.thenBy { it.id }), key = { it.id }) { card ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeAccent.copy(alpha = .24f))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CapsuleEnvelope(Modifier.size(64.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.title, style = TwoMemoryTypography.title)
                            Text("${if (card.authorId == ownId) "我写的" else "对方寄来的"} · ${if (card.visibility == "PRIVATE") "仅自己" else "两人共享"}", style = TwoMemoryTypography.caption)
                        }
                    }
                    Text("${if (card.unlocked) "云端已拆封" else "封存中"} · ${card.unlockDate}", style = TwoMemoryTypography.caption,
                        color = TwoMemoryColors.WarmBeigeMuted)
                    if (card.authorId != ownId && !card.unlocked) Text("信里的悄悄话，等云端确认拆封后再读。", style = TwoMemoryTypography.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (card.readableBody(ownId) != null) TextButton(enabled = !busy, onClick = { readingId = card.id }) {
                            Text(if (card.authorId == ownId && !card.unlocked) "查看自己的信" else "读信")
                        }
                        if (card.authorId == ownId) {
                            TextButton(enabled = !busy, onClick = { editing = card; error = null; notice = null }) { Text("编辑") }
                            TextButton(enabled = !busy, onClick = { deleting = card }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
    val reading = cards.firstOrNull { it.id == readingId }
    if (reading != null && reading.readableBody(ownId) != null && stillCurrent()) Dialog(onDismissRequest = { readingId = null }) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CapsuleEnvelope(Modifier.fillMaxWidth().height(100.dp))
                Text(reading.title, style = TwoMemoryTypography.title)
                Text(reading.readableBody(ownId).orEmpty(), style = TwoMemoryTypography.body)
                Text("${reading.unlockDate} · ${if (reading.visibility == "PRIVATE") "仅自己可见" else "两人共享"}", style = TwoMemoryTypography.caption)
                Button(onClick = {
                    if (reading.unlocked && stillCurrent()) store.dismiss(reading)
                    readingId = null
                }, modifier = Modifier.fillMaxWidth()) { Text("把信收好") }
            }
        }
    }
    deleting?.let { card -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("删除这封信？") },
        text = { Text("云端确认删除后，这封信会从信箱中移除。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    CapsuleCloud.delete(context, card, session)
                    if (stillCurrent()) { cards = store.cards(); notice = "信件已从云端删除" }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (stillCurrent()) error = "未能确认删除成功，请检查连接后刷新重试。" }
                finally { deleting = null; busy = false }
            }
        }) { Text("确认删除") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
internal fun CapsuleEditor(initial: CapsuleCard, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (CapsuleCard) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var body by rememberSaveable(initial.id) { mutableStateOf(initial.body.orEmpty()) }
    var date by rememberSaveable(initial.id) { mutableStateOf(initial.unlockDate) }
    var datePickerOpen by rememberSaveable(initial.id) { mutableStateOf(false) }
    if (datePickerOpen) com.twomemory.designsystem.PaperDatePickerDialog(LocalDate.parse(date),
        { datePickerOpen = false }) { date = it.toString(); datePickerOpen = false }
    var visibility by rememberSaveable(initial.id) { mutableStateOf(initial.visibility) }
    val edited = initial.copy(title = title.trim(), body = body, unlockDate = date, visibility = visibility)
    val valid = runCatching { CapsuleValidation.request(edited.request()) }.isSuccess
    BackHandler { if (!busy) onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !busy, onClick = onBack) { Icon(TwoMemoryIcons.Close, "返回信箱") }
            Text("给未来的一封信", style = TwoMemoryTypography.title)
        }
        CapsuleEnvelope(Modifier.fillMaxWidth().height(110.dp))
        OutlinedTextField(title, { title = it }, enabled = !busy, singleLine = true, label = { Text("信封标题 · 最多80字") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, enabled = !busy, label = { Text("想对未来说的话 · 最多20000字") },
            minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { datePickerOpen = true }) { Text("拆封日期 · $date") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = visibility == "SHARED", enabled = !busy, onClick = { visibility = "SHARED" })
            Text("两人共享", style = TwoMemoryTypography.body)
            Spacer(Modifier.width(12.dp))
            RadioButton(selected = visibility == "PRIVATE", enabled = !busy, onClick = { visibility = "PRIVATE" })
            Text("仅自己", style = TwoMemoryTypography.body)
        }
        Text(if (visibility == "PRIVATE") "这封信只留给自己，对方不会看到信封或正文。" else "对方在拆封前只能看到标题和日期。作者随时可以查看自己的正文。",
            style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = valid && !busy, onClick = { onSave(edited) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "正在保存…" else "保存到云端信箱")
        }
        Text("仅在点击保存后递交；云端确认成功才会进入信箱。", style = TwoMemoryTypography.caption)
    }
}

@Composable
private fun CapsuleEnvelope(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "手绘封蜡时光信封" }) {
        val h = size.height; val cx = size.width / 2; val ink = TwoMemoryColors.WarmBeigeAccent
        val left = cx - h * .42f; val right = cx + h * .42f
        drawRoundRect(ink.copy(alpha = .10f), Offset(left, h * .22f), Size(h * .84f, h * .56f), CornerRadius(h * .06f))
        val flap = Path().apply { moveTo(left, h * .23f); lineTo(cx, h * .55f); lineTo(right, h * .23f) }
        drawPath(flap, ink.copy(alpha = .75f), style = Stroke(1.8.dp.toPx()))
        val fold = Path().apply { moveTo(left, h * .77f); lineTo(cx - h * .15f, h * .49f); moveTo(right, h * .77f); lineTo(cx + h * .15f, h * .49f) }
        drawPath(fold, ink.copy(alpha = .30f), style = Stroke(1.dp.toPx()))
        drawCircle(ink.copy(alpha = .65f), h * .08f, Offset(cx, h * .55f))
        drawCircle(MaterialThemeEnvelopePaper, h * .022f, Offset(cx, h * .55f))
        drawCircle(TwoMemoryColors.WarmBeigeSage.copy(alpha = .4f), h * .025f, Offset(cx - h * .53f, h * .15f))
        drawCircle(ink.copy(alpha = .3f), h * .035f, Offset(cx + h * .50f, h * .72f))
    }
}

private val MaterialThemeEnvelopePaper = androidx.compose.ui.graphics.Color(0xFFFFF8EE)
