package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TimelineEntryUi(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val author: String,
    val body: String,
    val shared: Boolean = false,
)

/**
 * Timeline observes LOCAL Room state only — no hardcoded production data.
 * The loader is supplied by the app module (Room-backed); entries appear
 * here after they are written locally or pulled from the change feed.
 */
class TimelineViewModel(
    private val loader: (suspend () -> List<TimelineItem>)? = null,
) : ViewModel() {

    private val mutableEntries = MutableStateFlow<List<TimelineEntryUi>>(emptyList())
    val entries: StateFlow<List<TimelineEntryUi>> = mutableEntries.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val load = loader ?: return
        viewModelScope.launch {
            mutableEntries.value = load().map { it.toUi() }
        }
    }

    private fun TimelineItem.toUi(): TimelineEntryUi {
        val zone = runCatching { ZoneId.of(occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
        val local = occurredAt.atZone(zone)
        val date = local.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
        val time = local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
        return TimelineEntryUi(
            id = id.toString(),
            dateLabel = date,
            timeLabel = time,
            author = "我",
            body = title ?: "",
            shared = mode == com.twomemory.model.EntryMode.COLLABORATIVE,
        )
    }
}
