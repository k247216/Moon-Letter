package com.twomemory.couple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.twomemory.designsystem.PaperTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay

enum class CoupleToolRoute { ANNIVERSARY, CAPSULE, EXPORT }

enum class ExportScope(val label: String, val description: String) {
    MEMORY_BOOK("离线回忆手书", "把本机可读取的已发布文字、照片、声音、视频和回应整理成手账纪念册；解压后离线观看"),
    RECORD_ARCHIVE("记录数据存档", "单独保存记录 JSON、原照片/声音/视频及校验清单，不含独立相册、草稿和未解锁胶囊"),
    LOCAL_CACHE("本机已缓存的记录", "不依赖服务器，先导出这台手机当前能读到的内容"),
    DATE_RANGE("按时间范围", "填写起止日期，由服务端生成这段时间的 JSON；照片文件不打包"),
    ALL("全部回忆", "由服务端生成完整 JSON，保留作者、发生时间和记录状态；照片文件不打包"),
}

data class AnniversaryDraft(val name: String, val date: String, val repeatsYearly: Boolean, val reminderTime: String = "09:00")
data class CapsuleDraft(val title: String, val body: String, val unlockDate: String)

@Composable
fun RelationshipToolsScreen(
    route: CoupleToolRoute,
    onBack: () -> Unit,
    onSaveAnniversary: (AnniversaryDraft) -> Unit = {},
    onSaveCapsule: (CapsuleDraft) -> Unit = {},
    onStartExport: (ExportScope, String?, String?) -> Unit = { _, _, _ -> },
    exportStatus: String? = null,
    anniversaryShared: Boolean = false,
    initialAnniversaryName: String = "",
    initialAnniversaryDate: String = "",
    initialAnniversaryRepeats: Boolean = true,
    initialCapsuleTitle: String = "",
    initialCapsuleBody: String = "",
    initialCapsuleUnlockDate: String = "",
    initialCapsuleLocked: Boolean = false,
    initialAnniversaryReminderTime: String = "09:00",
    onSendTestNotification: () -> String = { "本机通知检查尚未接通" },
    onOpenNotificationSettings: () -> Unit = {},
) {
    var notificationStatus by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "返回我们")
                    Text("返回")
                }
                Text(
                    text = when (route) {
                        CoupleToolRoute.ANNIVERSARY -> "纪念日与倒计时"
                        CoupleToolRoute.CAPSULE -> "时间胶囊"
                        CoupleToolRoute.EXPORT -> "数据与导出"
                    },
                    style = TwoMemoryTypography.display,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            when (route) {
                CoupleToolRoute.ANNIVERSARY -> AnniversaryTool(
                    onSave = onSaveAnniversary,
                    initialName = initialAnniversaryName,
                    initialDate = initialAnniversaryDate,
                    initialRepeats = initialAnniversaryRepeats,
                    shared = anniversaryShared,
                    initialReminderTime = initialAnniversaryReminderTime,
                )
                CoupleToolRoute.CAPSULE -> CapsuleTool(
                    onSave = onSaveCapsule,
                    initialTitle = initialCapsuleTitle,
                    initialBody = initialCapsuleBody,
                    initialUnlockDate = initialCapsuleUnlockDate,
                    initialLocked = initialCapsuleLocked,
                )
                CoupleToolRoute.EXPORT -> ExportTool(onStartExport, exportStatus)
            }
        }
    }
}

@Composable
private fun AnniversaryTool(
    onSave: (AnniversaryDraft) -> Unit,
    initialName: String,
    initialDate: String,
    initialRepeats: Boolean,
    shared: Boolean,
    initialReminderTime: String,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var date by rememberSaveable(initialDate) { mutableStateOf(initialDate) }
    var repeats by rememberSaveable(initialRepeats) { mutableStateOf(initialRepeats) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var reminderTime by rememberSaveable(initialReminderTime) { mutableStateOf(initialReminderTime) }
    val validTime = runCatching { LocalTime.parse(reminderTime.trim()) }.isSuccess
    ToolCard {
        Text("你们的纪念日", style = TwoMemoryTypography.title)
        Text(
            "选一个属于你们的日子，让每一次靠近都有期待。",
            style = TwoMemoryTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        )
        OutlinedTextField(value = name, onValueChange = { name = it; saved = false }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CalendarDateButton("纪念日期", date) { date = it; saved = false }
        ReminderTimeButton(reminderTime) { reminderTime = it; saved = false }
        if (!validTime) Text("请输入有效时间，例如 09:00", color = MaterialTheme.colorScheme.error)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("每年重复", style = TwoMemoryTypography.body)
                Text("每年在同一天，重新期待一次", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
            Switch(checked = repeats, onCheckedChange = { repeats = it; saved = false })
        }
        anniversaryCountdown(date, repeats)?.let { preview ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (shared) "两个人的共同预览" else "本机预览", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                    Text(preview, style = TwoMemoryTypography.title)
                    Text(
                        if (shared) "已保存到共同空间，对面那台也能看到。"
                        else "目前只保存在这台手机上；网络恢复并保存成功后，对面才能看到。",
                        style = TwoMemoryTypography.caption,
                    )
                }
            }
        }
        if (date.isNotBlank() && anniversaryCountdown(date, repeats) == null) {
            Text("请输入有效公历日期，例如 2026-10-06", color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { onSave(AnniversaryDraft(name.trim(), date.trim(), repeats, reminderTime.trim())); saved = true }, enabled = name.isNotBlank() && anniversaryCountdown(date, repeats) != null && validTime) {
            Text(if (saved && shared) "已保存并同步" else if (saved) "已保存到本机" else "保存纪念日")
        }
        if (saved) {
            Text(
                if (shared) "已保存日期规则，并同步到两个人的空间。"
                else "已保存日期规则；服务端同步尚未确认，先不要把它当成两个人的提醒。",
                style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CapsuleTool(
    onSave: (CapsuleDraft) -> Unit,
    initialTitle: String,
    initialBody: String,
    initialUnlockDate: String,
    initialLocked: Boolean,
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var body by rememberSaveable(initialBody) { mutableStateOf(initialBody) }
    var unlockDate by rememberSaveable(initialUnlockDate) { mutableStateOf(initialUnlockDate) }
    var locked by rememberSaveable(initialLocked) { mutableStateOf(initialLocked) }
    var due by rememberSaveable(unlockDate) { mutableStateOf(capsuleIsDue(unlockDate)) }
    LaunchedEffect(locked, unlockDate) {
        while (locked) {
            due = capsuleIsDue(unlockDate)
            delay(1_000)
        }
    }
    ToolCard {
        Text("给未来的我们留一封信", style = TwoMemoryTypography.title)
        Text("这是本机信件：正文保存在本机，开启前在此页面隐藏；未同步到云端，修改手机时间可能影响开启。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        if (locked) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(TwoMemoryIcons.Capsule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("时间胶囊已锁定", style = TwoMemoryTypography.title)
                    Text("仅本机隐藏正文，不是云端封存或加密锁", style = TwoMemoryTypography.caption)
                    if (title.isNotBlank()) Text("《$title》", style = TwoMemoryTypography.body)
                    Text("开启日期：$unlockDate", style = TwoMemoryTypography.body)
                    if (due) {
                        Text("到期了，可以读了：", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                        Text(body, style = TwoMemoryTypography.body)
                    } else {
                        Text("正文只保存在这台手机上，对面那台读不到；开启日期前不显示，也还没有同步到服务端。", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("信件标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("写给未来的正文") }, minLines = 7, modifier = Modifier.fillMaxWidth())
            CalendarDateButton("开启日期", unlockDate.substringBefore('T')) { unlockDate = it }
            if (unlockDate.isNotBlank() && parseCapsuleLocalTime(unlockDate) == null) {
                Text("请输入有效日期或日期时间，例如 2026-10-06T20:30", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { onSave(CapsuleDraft(title.trim(), body, unlockDate.trim())); locked = true }, enabled = title.isNotBlank() && body.isNotBlank() && parseCapsuleLocalTime(unlockDate) != null) {
                Text("保存并锁定")
            }
        }
    }
}

@Composable
private fun CalendarDateButton(label: String, date: String, onDate: (String) -> Unit) {
    var picking by rememberSaveable(label, date) { mutableStateOf(false) }
    if (picking) com.twomemory.designsystem.PaperDatePickerDialog(
        runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()), { picking = false }) {
        onDate(it.toString()); picking = false
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        TextButton(onClick = { picking = true }) {
            Icon(TwoMemoryIcons.Calendar, null, tint = MaterialTheme.colorScheme.primary)
            Text("$label · ${runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日")) }.getOrDefault("选择日期")}",
                Modifier.padding(12.dp), style = TwoMemoryTypography.body)
        }
    }
}

@Composable
private fun ReminderTimeButton(value: String, onChange: (String) -> Unit) {
    var picking by rememberSaveable(value) { mutableStateOf(false) }
    if (picking) com.twomemory.designsystem.PaperTimePickerDialog(
        runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0)), { picking = false }, {
            onChange(it.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))); picking = false
        })
    TextButton({ picking = true }) { Text("提醒时间 · $value") }
}

/**
 * A safe local preview for explicit ISO dates. Lunar rules stay textual until
 * the shared date service is available, so this never guesses a lunar day.
 */
fun anniversaryCountdown(
    rule: String,
    repeatsYearly: Boolean,
    today: LocalDate = LocalDate.now(),
): String? {
    val parsed = try {
        LocalDate.parse(rule.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        return null
    }
    val target = if (!repeatsYearly) {
        parsed
    } else {
        nextYearlyOccurrence(parsed, today)
    }
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, target)
    return when {
        days > 0 -> "距离 ${target} 还有 $days 天"
        days == 0L -> "就是今天 · 记得留下一句话"
        else -> "这一天已过去 ${-days} 天"
    }
}

private fun nextYearlyOccurrence(rule: LocalDate, today: LocalDate): LocalDate {
    fun inYear(year: Int): LocalDate = runCatching { rule.withYear(year) }
        .getOrElse { LocalDate.of(year, 2, 28) }
    val thisYear = inYear(today.year)
    return if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
}

/** A capsule opens on its own date, and an unreadable date keeps it shut. */
internal fun parseCapsuleLocalTime(value: String): LocalDateTime? = runCatching {
    val text = value.trim()
    if ('T' in text) LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    else LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
}.getOrNull()

internal fun capsuleIsDue(
    unlockDate: String,
    today: LocalDate = LocalDate.now(),
    time: LocalTime = LocalTime.now(),
): Boolean = parseCapsuleLocalTime(unlockDate)?.let { !it.isAfter(today.atTime(time)) } ?: false

@Composable
private fun ExportTool(
    onStartExport: (ExportScope, String?, String?) -> Unit,
    status: String?,
) {
    var selected by rememberSaveable { mutableStateOf(ExportScope.MEMORY_BOOK.name) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    ToolCard {
        Text("把回忆带走", style = TwoMemoryTypography.title)
        Text("手书留作温暖的纪念册；数据存档保留记录和原媒体。两者只包含本机可读取的已发布记录，也包含自己暂时收起的记录。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        ExportScope.entries.forEach { scope ->
            FilterChip(
                selected = selected == scope.name,
                onClick = { selected = scope.name },
                label = { Text(scope.label) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(scope.description, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        }
        if (selected == ExportScope.DATE_RANGE.name) {
            OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("开始日期，例如 2026-10-01") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("结束日期，例如 2026-10-31") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            onStartExport(
                ExportScope.valueOf(selected),
                from.trim().ifBlank { null },
                to.trim().ifBlank { null },
            )
        }) {
            Text("开始导出")
        }
        if (selected == ExportScope.LOCAL_CACHE.name) {
            Text("本机缓存会以可读 Markdown + 机器可读 JSON 一起分享；原始媒体文件不会通过文本通道伪装成已打包。", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
        }
        if (selected == ExportScope.MEMORY_BOOK.name) {
            Text("照片预览和手写字体直接保存在手书里。完整解压后打开 index.html；播放声音、视频和保存原图请保留 media 文件夹。分享会交出全部正文和原媒体，请自行确认。",
                style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
        }
        if (selected == ExportScope.RECORD_ARCHIVE.name) {
            Text("保存为 ZIP：记录 JSON + 原媒体 + SHA-256 校验清单。目前不支持应用内导入，也不等于云端全量备份。未下载的原媒体会先获取，缺失时不生成假完整存档。",
                style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
        }
        status?.let { Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun ToolCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}
