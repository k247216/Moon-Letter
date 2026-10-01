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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class CoupleToolRoute { ANNIVERSARY, CAPSULE, EXPORT }

enum class ExportScope(val label: String, val description: String) {
    LOCAL_CACHE("本机已缓存的记录", "不依赖服务器，先导出这台手机当前能读到的内容"),
    DATE_RANGE("按时间范围", "填写起止日期，由服务端生成这段时间的 JSON；照片文件不打包"),
    ALL("全部回忆", "由服务端生成完整 JSON，保留作者、发生时间和记录状态；照片文件不打包"),
}

data class AnniversaryDraft(val name: String, val date: String, val repeatsYearly: Boolean)
data class CapsuleDraft(val title: String, val body: String, val unlockDate: String)

@Composable
fun RelationshipToolsScreen(
    route: CoupleToolRoute,
    onBack: () -> Unit,
    onSaveAnniversary: (AnniversaryDraft) -> Unit = {},
    onSaveCapsule: (CapsuleDraft) -> Unit = {},
    onStartExport: (ExportScope, String?, String?) -> Unit = { _, _, _ -> },
    exportStatus: String? = null,
    initialAnniversaryName: String = "",
    initialAnniversaryDate: String = "",
    initialAnniversaryRepeats: Boolean = true,
    initialCapsuleTitle: String = "",
    initialCapsuleBody: String = "",
    initialCapsuleUnlockDate: String = "",
    initialCapsuleLocked: Boolean = false,
) {
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
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var date by rememberSaveable(initialDate) { mutableStateOf(initialDate) }
    var repeats by rememberSaveable(initialRepeats) { mutableStateOf(initialRepeats) }
    var saved by rememberSaveable { mutableStateOf(false) }
    ToolCard {
        Text("你们的纪念日", style = TwoMemoryTypography.title)
        Text(
            "日期写成 2026-10-06 这样的形式，本机就能算出倒计时；农历规则先按文字保存，等服务端计算才判断具体日子。",
            style = TwoMemoryTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        )
        OutlinedTextField(value = name, onValueChange = { name = it; saved = false }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = date, onValueChange = { date = it; saved = false }, label = { Text("日期规则") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("每年重复", style = TwoMemoryTypography.body)
                Text("保存规则，不保存每天变化的剩余天数", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
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
                    Text("本机预览", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                    Text(preview, style = TwoMemoryTypography.title)
                    Text("这条规则目前只保存在这台手机上，对面那台还看不到。", style = TwoMemoryTypography.caption)
                }
            }
        }
        Button(onClick = { onSave(AnniversaryDraft(name.trim(), date.trim(), repeats)); saved = true }, enabled = name.isNotBlank() && date.isNotBlank()) {
            Text(if (saved) "已保存到本机" else "保存纪念日")
        }
        if (saved) {
            Text("已保存日期规则；纪念日同步到对面还没有接通，先不要把它当成两个人的提醒。", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
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
    ToolCard {
        Text("给未来的我们留一封信", style = TwoMemoryTypography.title)
        Text("开启日期前，这台手机不显示正文，导出不包含它；对面那台也读不到这封信。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        if (locked) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(TwoMemoryIcons.Capsule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("时间胶囊已锁定", style = TwoMemoryTypography.title)
                    if (title.isNotBlank()) Text("《$title》", style = TwoMemoryTypography.body)
                    Text("开启日期：$unlockDate", style = TwoMemoryTypography.body)
                    if (capsuleIsDue(unlockDate)) {
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
            OutlinedTextField(value = unlockDate, onValueChange = { unlockDate = it }, label = { Text("开启日期，例如 2027-10-06") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(CapsuleDraft(title.trim(), body, unlockDate.trim())); locked = true }, enabled = title.isNotBlank() && body.isNotBlank() && unlockDate.isNotBlank()) {
                Text("保存并锁定")
            }
        }
    }
}

/**
 * A safe local preview for explicit ISO dates. Lunar rules stay textual until
 * the shared date service is available, so this never guesses a lunar day.
 */
internal fun anniversaryCountdown(
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
internal fun capsuleIsDue(unlockDate: String, today: LocalDate = LocalDate.now()): Boolean {
    val parsed = try {
        LocalDate.parse(unlockDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        return false
    }
    return !parsed.isAfter(today)
}

@Composable
private fun ExportTool(
    onStartExport: (ExportScope, String?, String?) -> Unit,
    status: String?,
) {
    var selected by rememberSaveable { mutableStateOf(ExportScope.LOCAL_CACHE.name) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    ToolCard {
        Text("把回忆带走", style = TwoMemoryTypography.title)
        Text("导出不包含登录令牌、配对密钥或长期下载地址。服务端导出是一段 JSON 文本，照片文件不在其中。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
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
