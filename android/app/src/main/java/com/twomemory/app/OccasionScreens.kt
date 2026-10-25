package com.twomemory.app
import com.twomemory.designsystem.PaperTextButton as TextButton

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.twomemory.designsystem.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
fun OccasionLibrary(cards: List<OccasionCard>, ownId: String, coupleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<OccasionCard?>(null) }
    var deleting by remember { mutableStateOf<OccasionCard?>(null) }
    var reading by remember { mutableStateOf<OccasionCard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var partnerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ownId, coupleId) {
        try {
            val session = SyncSession.load(context) ?: return@LaunchedEffect
            if (session.userId.toString() != ownId || session.coupleId.toString() != coupleId) return@LaunchedEffect
            val space = com.twomemory.network.RetrofitSessionApi.create().readSpace(session.baseUrl, session.token, session.coupleId)
            if (space.id == session.coupleId && SyncSession.load(context) == session) {
                partnerId = space.members.firstOrNull { it.userId.toString() != ownId }?.userId?.toString()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A birthday remains editable without a partner. */ }
    }
    fun create(kind: String) {
        editing = OccasionCard(UUID.randomUUID().toString(), coupleId, ownId, kind, "", "", LocalDate.now().toString(),
            kind != "SURPRISE", if (kind == "SURPRISE") partnerId else null, null, "",
            if (kind == "SURPRISE") "PRIVATE" else "SHARED")
        notice = null; error = null
    }
    BackHandler { if (!busy) { if (editing != null) editing = null else onBack() } }
    if (editing != null) {
        OccasionEditor(editing!!, busy, error, onBack = { if (!busy) { editing = null; error = null } }, onSave = { card ->
            busy = true; error = null
            scope.launch {
                try { OccasionCloud.save(context, card); if (editing?.id == card.id) editing = null; notice = if (card.visibility == "PRIVATE") "已收好，仅自己可见，也会提醒你" else if (card.kind == "SURPRISE") "心意已交给共同空间，会在指定日向对方展示" else "已保存到共同空间" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "尚未保存成功，请检查连接后重试；填写的内容还在" }
                finally { busy = false }
            }
        }, partnerId = partnerId)
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(TwoMemoryIcons.Close, "返回") }
            Text("值得记住的日子", style = TwoMemoryTypography.display)
        }
        Text("生日、纪念日，还有想送给你的小惊喜。", style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("生日" to "BIRTHDAY", "纪念日" to "ANNIVERSARY", "惊喜卡" to "SURPRISE").forEach { (label, kind) ->
                OutlinedButton(onClick = { create(kind) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) { Text(label) }
            }
        }
        notice?.let { Text(it, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeSage) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (cards.isEmpty()) {
            OccasionIllustration("ANNIVERSARY", Modifier.fillMaxWidth().height(120.dp))
            Text("把属于你们的日子，一页页收在这里。", style = TwoMemoryTypography.body)
            Text("当天首次打开时，会有一张小贺卡等着你。", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
        cards.sortedBy { it.date }.forEach { card ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeAccent.copy(alpha = .14f))) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OccasionIllustration(card.kind, Modifier.size(52.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(card.title, style = TwoMemoryTypography.title)
                            Text("${card.date}${if (card.repeatsYearly) " · 每年这一天" else ""}", style = TwoMemoryTypography.caption,
                                color = TwoMemoryColors.WarmBeigeMuted)
                        }
                    }
                    occasionCountdown(card)?.let { Text(it, style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeAccent) }
                    if (card.visibility == "PRIVATE") Text("仅自己可见", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { reading = card }) { Text("看看卡片") }
                        if (card.authorId == ownId || (card.kind != "SURPRISE" && card.visibility != "PRIVATE")) {
                            TextButton(onClick = { editing = card; error = null }) { Text("编辑") }
                            TextButton(enabled = !busy, onClick = { deleting = card }) { Text(if (card.kind == "SURPRISE") "撤回" else "删除") }
                        }
                    }
                }
            }
        }
    }
    reading?.let { card -> OccasionGreeting(listOf(card), { reading = null }) }
    deleting?.let { card -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text(if (card.kind == "SURPRISE") "撤回这份心意？" else "删除这个日子？") },
        text = { Text("两个人的列表都会更新。对方离线时，需要恢复同步后才会更新。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; scope.launch {
                try { OccasionCloud.delete(context, card); deleting = null; error = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "没有删除成功，请检查连接后重试"; deleting = null }
                finally { busy = false }
            }
        }) { Text("确认") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("取消") } }) }
}

fun occasionCountdown(card: OccasionCard, today: LocalDate = LocalDate.now()): String? = runCatching {
    val date = LocalDate.parse(card.date)
    var target = if (card.repeatsYearly && !today.isBefore(date)) date.withYear(today.year) else date
    if (card.repeatsYearly && target < today) target = date.withYear(today.year + 1)
    val days = ChronoUnit.DAYS.between(today, target)
    when { days == 0L -> "就是今天，给这一天留一点温柔。"; days > 0 -> "还有 $days 天"; else -> "这份心意已留在过去的日子里" }
}.getOrNull()

@Composable
internal fun OccasionEditor(initial: OccasionCard, busy: Boolean, error: String?, onBack: () -> Unit,
    onSave: (OccasionCard) -> Unit, partnerId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var body by rememberSaveable(initial.id) { mutableStateOf(initial.body) }
    var date by rememberSaveable(initial.id) { mutableStateOf(initial.date) }
    var datePickerOpen by rememberSaveable(initial.id) { mutableStateOf(false) }
    if (datePickerOpen) com.twomemory.designsystem.PaperDatePickerDialog(
        runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
        { datePickerOpen = false }) { date = it.toString(); datePickerOpen = false }
    var repeats by rememberSaveable(initial.id) { mutableStateOf(initial.repeatsYearly) }
    var visibility by rememberSaveable(initial.id) { mutableStateOf(initial.visibility) }
    var asset by rememberSaveable(initial.id) { mutableStateOf(initial.photoAssetId) }
    var localPhoto by rememberSaveable(initial.id) { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uploading = true; photoError = null
            scope.launch {
                try {
                    val stored = requireNotNull(EntryPhotos.store(context, uri, UUID.randomUUID().toString())) { "照片无法读取，请重新选择" }
                    localPhoto = stored.localPath
                    asset = OccasionCloud.uploadPhoto(context, stored.localPath)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { photoError = failure.message ?: "照片未能上传，请重新选择"; localPhoto = null }
                finally { uploading = false }
            }
        }
    }
    val recipient = (initial.recipientId ?: partnerId)?.takeIf { it != initial.authorId }
    val value = initial.copy(title = title.trim(), body = body.trim(), date = date, repeatsYearly = repeats,
        photoAssetId = asset, visibility = visibility,
        recipientId = if (initial.kind == "SURPRISE" && visibility == "SHARED") recipient else initial.recipientId)
    val locked = busy || uploading
    BackHandler { if (!locked) onBack() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !locked, onClick = onBack) { Icon(TwoMemoryIcons.Close, "返回重要日子") }
            Text(if (initial.kind == "SURPRISE") "给你的小惊喜" else "收好一个重要日子", style = TwoMemoryTypography.title)
        }
        OccasionIllustration(initial.kind, Modifier.fillMaxWidth().height(100.dp))
        val ownId = SyncSession.load(context)?.userId?.toString()
        if (initial.authorId == ownId) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SHARED", "PRIVATE").forEach { choice ->
                    FilterChip(selected = visibility == choice, onClick = { visibility = choice },
                        enabled = !locked && !(choice == "SHARED" && initial.kind == "SURPRISE" && recipient == null),
                        label = { Text(if (choice == "PRIVATE") "我的小计划" else if (initial.kind == "SURPRISE") "当天送给对方" else "我们都能看") })
                }
            }
            Text(if (visibility == "PRIVATE") "云端保存，只提醒自己，不向对方展示。" else "生日和纪念日可随时编辑，每年继续珍藏。",
                style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
        Text(if (initial.kind == "SURPRISE") "写下来，到了那一天，替你轻轻递给对方。" else "当天打开月笺，就会遇见这张小贺卡。", style = TwoMemoryTypography.body)
        OutlinedTextField(title, { title = it.take(80) }, enabled = !locked, label = { Text("卡片标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(body, { body = it.take(4000) }, enabled = !locked, label = { Text("想说的话") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
        OutlinedButton(enabled = !locked, onClick = { datePickerOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("展示日期 · $date") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("每年重复", Modifier.weight(1f), style = TwoMemoryTypography.body)
            Switch(repeats, { repeats = it }, enabled = !locked)
        }
        if (asset != null) {
            EntryPhoto(localPhoto, asset, Modifier.fillMaxWidth().height(140.dp), "贺卡照片", contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            TextButton(enabled = !locked, onClick = { asset = null; localPhoto = null }) { Text("移除照片") }
        }
        OutlinedButton(enabled = !locked, onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (uploading) "正在收好照片…" else "加一张照片") }
        (photoError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(enabled = !locked, onClick = { preview = true }, modifier = Modifier.weight(1f)) { Text("预览") }
            Button(enabled = !locked && title.isNotBlank() && photoError == null, onClick = { onSave(value) }, modifier = Modifier.weight(1f)) {
                Text(if (busy) "正在收好…" else if (visibility == "PRIVATE") "收好我的小计划" else if (initial.kind == "SURPRISE") "送给对方" else "保存这个日子")
            }
        }
        Text("公历日期 · 每天展示一次 · 关闭后仍可在列表回看", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
    }
    if (preview) OccasionGreeting(listOf(value.copy(title = value.title.ifBlank { "一份小心意" })), { preview = false }, localPhoto)
}

@Composable
fun OccasionGreeting(cards: List<OccasionCard>, onClose: () -> Unit, localPhoto: String? = null) {
    if (cards.isEmpty()) return
    var index by remember(cards.map { it.id }) { mutableStateOf(0) }
    val card = cards[index.coerceAtMost(cards.lastIndex)]
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeAccent.copy(alpha = .25f)),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 620.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (card.visibility == "PRIVATE") "我的小计划，今天要记得" else if (card.kind == "SURPRISE") "有人给你留了一份心意" else "今天，值得好好记住", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                OccasionIllustration(card.kind, Modifier.fillMaxWidth().height(96.dp))
                Text(card.title, style = TwoMemoryTypography.display)
                PaperUnderline(Modifier.width(140.dp))
                Text(card.body.ifBlank { if (card.kind == "BIRTHDAY") "生日快乐。愿你被爱，也愿我们把每个平凡的日子过得闪闪发光。" else "和你一起，把这一天温柔地收好。" },
                    style = TwoMemoryTypography.body)
                if (card.photoAssetId != null) EntryPhoto(localPhoto, card.photoAssetId, Modifier.fillMaxWidth().height(170.dp), "心意卡照片", contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                if (cards.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = index > 0, onClick = { index-- }) { Text("上一页") }
                    Text("${index + 1} / ${cards.size}", style = TwoMemoryTypography.caption)
                    TextButton(enabled = index < cards.lastIndex, onClick = { index++ }) { Text("下一页") }
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("把心意收好") }
            }
        }
    }
}

@Composable
private fun OccasionIllustration(kind: String, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = if (kind == "BIRTHDAY") "生日小蛋糕" else "手绘心意信封" }) {
        val w = size.width; val h = size.height; val cx = w / 2; val ink = TwoMemoryColors.WarmBeigeAccent
        if (kind == "BIRTHDAY") {
            drawRoundRect(ink.copy(alpha = .13f), androidx.compose.ui.geometry.Offset(cx - h * .34f, h * .46f),
                androidx.compose.ui.geometry.Size(h * .68f, h * .34f), androidx.compose.ui.geometry.CornerRadius(h * .07f))
            drawLine(ink, androidx.compose.ui.geometry.Offset(cx - h * .34f, h * .8f), androidx.compose.ui.geometry.Offset(cx + h * .34f, h * .8f), 2.dp.toPx())
            (-1..1).forEach { i ->
                val x = cx + i * h * .17f
                drawLine(ink, androidx.compose.ui.geometry.Offset(x, h * .46f), androidx.compose.ui.geometry.Offset(x, h * .29f), 2.dp.toPx())
                drawOval(Color(0xFFE2B96F), androidx.compose.ui.geometry.Offset(x - h * .025f, h * .18f), androidx.compose.ui.geometry.Size(h * .05f, h * .08f))
            }
        } else {
            val left = cx - h * .42f; val right = cx + h * .42f
            drawRoundRect(ink.copy(alpha = .09f), androidx.compose.ui.geometry.Offset(left, h * .24f), androidx.compose.ui.geometry.Size(h * .84f, h * .56f), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            val path = Path().apply { moveTo(left, h * .25f); lineTo(cx, h * .56f); lineTo(right, h * .25f) }
            drawPath(path, ink.copy(alpha = .7f), style = Stroke(1.8.dp.toPx()))
            drawCircle(ink.copy(alpha = .6f), h * .055f, androidx.compose.ui.geometry.Offset(cx, h * .58f))
        }
        drawCircle(ink.copy(alpha = .22f), h * .025f, androidx.compose.ui.geometry.Offset(cx - h * .53f, h * .18f))
        drawCircle(TwoMemoryColors.WarmBeigeSage.copy(alpha = .4f), h * .035f, androidx.compose.ui.geometry.Offset(cx + h * .5f, h * .66f))
    }
}
