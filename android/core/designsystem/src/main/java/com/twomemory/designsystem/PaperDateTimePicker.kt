package com.twomemory.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun PaperDatePickerDialog(initialDate: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) =
    PaperDateTimePickerDialog(initialDate, null, onDismiss) { date, _ -> onConfirm(date) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDateTimePickerDialog(initialDate: LocalDate, initialTime: LocalTime?,
    onDismiss: () -> Unit, onNow: (() -> Unit)? = null, onConfirm: (LocalDate, LocalTime?) -> Unit) {
    var dateText by rememberSaveable(initialDate) { mutableStateOf(initialDate.toString()) }
    val date = LocalDate.parse(dateText)
    var hour by rememberSaveable(initialTime) { mutableStateOf(initialTime?.hour ?: 0) }
    var minute by rememberSaveable(initialTime) { mutableStateOf(initialTime?.minute ?: 0) }
    var timePage by rememberSaveable { mutableStateOf(false) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf(emptySet<String>()) }
    fun movement(id: String, active: Boolean) { moving = if (active) moving + id else moving - id }
    val calendarState = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val selectedDate = if (calendar) calendarState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    } ?: date else date
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        title = { Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = TwoMemoryTypography.title) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialTime != null) Row {
                    PaperTextButton({ timePage = false }, enabled = moving.isEmpty()) { Text("日期") }
                    PaperTextButton({ timePage = true }, enabled = moving.isEmpty()) { Text("时间 · %02d:%02d".format(java.util.Locale.ROOT, hour, minute)) }
                }
                if (timePage) PaperTimeWheels(hour, minute, { hour = it }, { minute = it }, onMoving = ::movement)
                else if (calendar) DatePicker(calendarState, showModeToggle = false)
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(1.3f)) { PaperWheel("年份", "year", (minOf(1900, initialDate.year)..maxOf(2100, initialDate.year)).toList(),
                        date.year, "年", onBusy = { movement("year", it) }) { dateText = LocalDate.parse(dateText).withYear(it).toString() } }
                    Box(Modifier.weight(1f)) { PaperWheel("月份", "month", (1..12).toList(), date.monthValue, "月", onBusy = { movement("month", it) }) {
                        dateText = LocalDate.parse(dateText).withMonth(it).toString()
                    } }
                    Box(Modifier.weight(1f)) { PaperWheel("日期", "day", (1..date.lengthOfMonth()).toList(), date.dayOfMonth, "日", onBusy = { movement("day", it) }) {
                        val current = LocalDate.parse(dateText)
                        dateText = current.withDayOfMonth(it.coerceAtMost(current.lengthOfMonth())).toString()
                    } }
                }
                if (!timePage) PaperTextButton({
                    if (calendar) dateText = selectedDate.toString()
                    else calendarState.selectedDateMillis = LocalDate.parse(dateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    calendar = !calendar
                }, enabled = moving.isEmpty()) { Text(if (calendar) "用滚轮慢慢选" else "用日历找一天", style = TwoMemoryTypography.caption) }
                onNow?.let { PaperTextButton(it, enabled = moving.isEmpty()) { Text("就是现在", style = TwoMemoryTypography.caption) } }
            }
        }, confirmButton = { PaperTextButton({
            val chosen = if (calendar) calendarState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                ?: LocalDate.parse(dateText) else LocalDate.parse(dateText)
            onConfirm(chosen, initialTime?.let { LocalTime.of(hour, minute) })
        }, enabled = moving.isEmpty()) { Text("好") } },
        dismissButton = { PaperTextButton(onDismiss) { Text("先不改") } })
}

@Composable
fun PaperTimePickerDialog(initialTime: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit,
    hourOnly: Boolean = false) {
    var hour by rememberSaveable(initialTime) { mutableStateOf(initialTime.hour) }
    var minute by rememberSaveable(initialTime) { mutableStateOf(if (hourOnly) 0 else initialTime.minute) }
    var moving by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        title = { Text("把时间轻轻定下来", style = TwoMemoryTypography.title) },
        text = { PaperTimeWheels(hour, minute, { hour = it }, { minute = it }, hourOnly) { id, active ->
            moving = if (active) moving + id else moving - id
        } },
        confirmButton = { PaperTextButton({ onConfirm(LocalTime.of(hour, minute)) }, enabled = moving.isEmpty()) { Text("好") } },
        dismissButton = { PaperTextButton(onDismiss) { Text("先不改") } })
}

@Composable
private fun PaperTimeWheels(hour: Int, minute: Int, onHour: (Int) -> Unit, onMinute: (Int) -> Unit,
    hourOnly: Boolean = false, onMoving: (String, Boolean) -> Unit = { _, _ -> }) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(1f)) { PaperWheel("小时", "hour", (0..23).toList(), hour, "时", onBusy = { onMoving("hour", it) }, onSelect = onHour) }
        if (!hourOnly) Box(Modifier.weight(1f)) { PaperWheel("分钟", "minute", (0..59).toList(), minute, "分", onBusy = { onMoving("minute", it) }, onSelect = onMinute) }
    }
}

@Composable
private fun PaperWheel(label: String, id: String, values: List<Int>, selected: Int, suffix: String,
    onBusy: (Boolean) -> Unit = {}, onSelect: (Int) -> Unit) = key(id, values.size) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0))
    val rowHeight = maxOf(42f, 30f * LocalDensity.current.fontScale).dp
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onSelect)
    val busyCallback by rememberUpdatedState(onBusy)
    DisposableEffect(state) { onDispose { busyCallback(false) } }
    fun centerIndex(): Int? {
        val layout = state.layoutInfo
        val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        return layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) }?.index
    }
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress) state.scrollToItem(values.indexOf(selected).coerceAtLeast(0))
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centerIndex() }.collectLatest { (moving, index) ->
            if (moving) busyCallback(true)
            else if (index != null) { values.getOrNull(index)?.let { callback(it) }; busyCallback(false) }
        }
    }
    fun choose(index: Int) {
        busyCallback(true)
        scope.launch { try { state.animateScrollToItem(index); values.getOrNull(index)?.let { callback(it) } }
            finally { busyCallback(false) } }
    }
    Box(Modifier.fillMaxWidth().height(rowHeight * 5), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(rowHeight).background(MaterialTheme.colorScheme.primary.copy(alpha = .085f), RoundedCornerShape(8.dp)))
        LazyColumn(Modifier.fillMaxSize().semantics {
            contentDescription = label; stateDescription = "$selected$suffix"
            customActions = listOf(CustomAccessibilityAction("前一个") { choose((values.indexOf(selected) - 1).coerceAtLeast(0)); true },
                CustomAccessibilityAction("后一个") { choose((values.indexOf(selected) + 1).coerceAtMost(values.lastIndex)); true })
        }, state = state, contentPadding = PaddingValues(vertical = rowHeight * 2),
            flingBehavior = rememberSnapFlingBehavior(state)) {
            itemsIndexed(values, key = { _, value -> value }) { index, value ->
                Box(Modifier.fillMaxWidth().height(rowHeight).testTag("$id-$value")
                    .semantics { this.selected = value == selected }.clickable { choose(index) }, contentAlignment = Alignment.Center) {
                    Text("$value$suffix", style = TwoMemoryTypography.body, maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (value == selected) 1f else .35f))
                }
            }
        }
    }
}
