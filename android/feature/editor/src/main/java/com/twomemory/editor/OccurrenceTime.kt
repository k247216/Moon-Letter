package com.twomemory.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val occurrenceFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 · HH:mm", Locale.CHINA)

/**
 * The record's own zone. When its name cannot be read the editor shows UTC, which is
 * what every other page does for the same record — one record must not get two dates.
 */
internal fun occurrenceZone(timezone: String): ZoneId =
    runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("UTC") }

/** How the top bar and the author block name the same moment. */
internal fun occurrenceLabel(occurrenceTime: Instant, timezone: String): String =
    occurrenceTime.atZone(occurrenceZone(timezone)).format(occurrenceFormatter)

/**
 * The day the picker holds is midnight UTC, while a record stores a real instant in
 * the zone it was written. So the day is read back the way the picker wrote it and
 * the hour/minute are applied inside the record's own zone.
 */
internal fun pickedOccurrence(datePickerUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Instant =
    Instant.ofEpochMilli(datePickerUtcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()

private fun LocalDate.asUtcDayMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * The moment the story happened, which the spec makes editable on purpose: writing
 * about this morning at night must not silently record the evening. Once the record
 * is written the time belongs to it, so this page stops offering to change it.
 */
@Composable
fun OccurrenceTime(
    occurrenceTime: Instant,
    timezone: String,
    editable: Boolean,
    onChange: (Instant) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val label = occurrenceLabel(occurrenceTime, timezone)
    val tapTarget = if (editable) {
        Modifier.semantics { contentDescription = "发生时间 $label，点开修改" }
            .clip(RoundedCornerShape(9.dp))
            .clickable { picking = true }
    } else {
        Modifier
    }
    Text(
        label,
        style = TwoMemoryTypography.body,
        modifier = tapTarget.padding(horizontal = 8.dp, vertical = 4.dp),
    )
    if (picking) {
        val written = occurrenceTime.atZone(occurrenceZone(timezone))
        OccurrencePicker(
            written = written,
            onDismiss = { picking = false },
            onPick = { picked ->
                picking = false
                onChange(picked)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccurrencePicker(
    written: ZonedDateTime,
    onDismiss: () -> Unit,
    onPick: (Instant) -> Unit,
) {
    val dateState: DatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = written.toLocalDate().asUtcDayMillis(),
    )
    val timeState = rememberTimePickerState(
        initialHour = written.hour,
        initialMinute = written.minute,
        is24Hour = true,
    )
    val zone = written.zone
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = dateState.selectedDateMillis != null,
                onClick = {
                    dateState.selectedDateMillis?.let { picked ->
                        onPick(pickedOccurrence(picked, timeState.hour, timeState.minute, zone))
                    }
                },
            ) { Text("好") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onPick(Instant.now()) }) { Text("就是现在") }
                TextButton(onClick = onDismiss) { Text("先不改") }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DatePicker(state = dateState)
                TimePicker(state = timeState)
            }
        },
    )
}
