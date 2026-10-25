package com.twomemory.app
import com.twomemory.designsystem.PaperTextButton as TextButton

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twomemory.designsystem.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** A journal calendar; shared confirmation and local input remain visibly separate. */
@Composable
internal fun IndependentAnniversaryScreen(
    state: IndependentAnniversaryState,
    onDraftChange: (IndependentAnniversaryDraft) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val context = LocalContext.current
    val draft = state.draft
    val validation = independentAnniversaryValidation(draft, today)
    var rulesVisible by remember { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var timePickerOpen by rememberSaveable { mutableStateOf(false) }
    val pickDate = { datePickerOpen = true }
    val pickTime = { timePickerOpen = true }
    if (datePickerOpen) PaperDatePickerDialog(
        runCatching { LocalDate.of(draft.year ?: today.year, draft.month, draft.day) }.getOrDefault(today),
        { datePickerOpen = false }) { date ->
        onDraftChange(draft.copy(year = date.year, month = date.monthValue, day = date.dayOfMonth)); datePickerOpen = false
    }
    if (timePickerOpen) PaperTimePickerDialog(
        runCatching { LocalTime.parse(draft.reminderTime) }.getOrDefault(LocalTime.of(9, 0)),
        { timePickerOpen = false }, onConfirm = { time ->
        onDraftChange(draft.copy(reminderTime = time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))); timePickerOpen = false
    })
    Column(Modifier.fillMaxSize().paperTexture().safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            TextButton(onClick = onBack) { Text("返回我们") }
            Text("属于我们的纪念日", style = TwoMemoryTypography.display.copy(fontSize = 30.sp, lineHeight = 38.sp))
            Text("把重要的一天，轻轻夹进日历。", style = TwoMemoryTypography.caption,
                color = TwoMemoryColors.WarmBeigeMuted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = pickDate, enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "选择原始纪念日期" },
                shape = RoundedCornerShape(20.dp), color = TwoMemoryColors.WarmBeigeSurface,
                border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeLine)) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("公历 · ${draft.month} 月", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeAccent)
                    Text(draft.day.toString(), style = TwoMemoryTypography.display.copy(fontSize = 56.sp, lineHeight = 62.sp),
                        color = TwoMemoryColors.WarmBeigeAccent)
                    PaperRule(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text(draft.year?.let { "原始公历日期 · $it-${draft.month}-${draft.day}" } ?: "原始年份待选择",
                        style = TwoMemoryTypography.caption)
                }
            }
            OutlinedTextField(value = draft.title, onValueChange = { onDraftChange(draft.copy(title = it)) },
                enabled = !state.saving, singleLine = true, label = { Text("给这一天起个名字") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Surface(onClick = pickTime, enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "选择本机纪念日提醒时间" },
                shape = RoundedCornerShape(14.dp), color = TwoMemoryColors.WarmBeigeAccentSoft) {
                Column(Modifier.padding(14.dp)) {
                    Text("本机提醒时间 · ${draft.reminderTime}", style = TwoMemoryTypography.title)
                    Text("给这一刻，留一点期待", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
            }
            state.confirmed?.let { confirmed ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("共同空间已确认 · ${confirmed.title}", style = TwoMemoryTypography.caption)
                    val calendar = when (confirmed.calendarType) { "GREGORIAN" -> "公历"; "LUNAR" -> "农历"; else -> "未支持历法" }
                    Text("$calendar ${confirmed.month} 月 ${confirmed.day} 日" +
                        (confirmed.startYear?.let { " · $it 年" } ?: " · 原始年份未知") +
                        (if (confirmed.repeatsYearly) " · 每年重复" else " · 不重复"), style = TwoMemoryTypography.caption,
                        color = TwoMemoryColors.WarmBeigeMuted)
                }
            } ?: Text("尚未确认共享配置；当前是本机草稿。", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            TextButton(onClick = { rulesVisible = true }) { Text("关于日期与提醒") }
            Spacer(Modifier.height(4.dp))
        }
        // Footer is outside the scroll body, including on phones and with longer errors.
        Surface(color = TwoMemoryColors.WarmBeigeBackground) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                validation?.let { Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error) }
                state.error?.let { Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error) }
                Text("日期共享 · 提醒时间仅本机", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                Button(onClick = onSave, enabled = validation == null && !state.saving,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "保存独立纪念日" }) {
                    Text(if (state.saving) "等待共同空间确认…" else "保存公历年度纪念日")
                }
            }
        }
    }
    if (rulesVisible) AlertDialog(onDismissRequest = { rulesVisible = false }, title = { Text("关于日期与提醒") },
        text = { Text("明确保存后使用公历、每年重复；原始年份必须由你选择。提醒按设备时区设置，后台送达可能延迟。旧的农历、不重复或未知年份规则原样保留，只有明确保存才替换。中秋等农历日子本版暂不换算，不用固定公历日期代替。") },
        confirmButton = { TextButton(onClick = { rulesVisible = false }) { Text("知道了") } })
}
