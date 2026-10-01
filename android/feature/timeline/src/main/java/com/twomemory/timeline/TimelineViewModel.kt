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
import java.util.UUID

data class TimelineEntryUi(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val author: String,
    val title: String?,
    val body: String = "",
    val shared: Boolean = false,
    val mine: Boolean = false,
    /** Still only on this device: published records never stay in DRAFT here. */
    val unsent: Boolean = false,
)

/**
 * Timeline observes LOCAL Room state only — no hardcoded production data.
 * The loader is supplied by the app module (Room-backed); entries appear
 * here after they are written locally or pulled from the change feed.
 */
class TimelineViewModel(
    private val loader: (suspend () -> List<TimelineItem>)? = null,
    private val currentUserId: UUID? = null,
) : ViewModel() {

    private var ownName: String = ""
    private var partnerName: String = ""

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

    fun updateNames(own: String, partner: String) {
        ownName = own.trim()
        partnerName = partner.trim()
        refresh()
    }

    private fun TimelineItem.toUi(): TimelineEntryUi {
        val zone = runCatching { ZoneId.of(occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
        val local = occurredAt.atZone(zone)
        val date = local.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
        val time = local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
        val mine = currentUserId != null && authorId == currentUserId
        return TimelineEntryUi(
            id = id.toString(),
            dateLabel = date,
            timeLabel = time,
            author = when {
                mine -> ownName.ifBlank { "我" }
                else -> partnerName.ifBlank { "伴侣" }
            },
            title = title?.takeIf { it.isNotBlank() },
            // Body must be the real content text; title is rendered separately.
            body = preview ?: title.orEmpty(),
            shared = mode == com.twomemory.model.EntryMode.COLLABORATIVE,
            mine = mine,
            unsent = state == com.twomemory.model.EntryState.DRAFT,
        )
    }
}
