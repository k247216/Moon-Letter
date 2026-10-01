package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.TimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val photo: com.twomemory.model.TimelinePhoto? = null,
    val shared: Boolean = false,
    val mine: Boolean = false,
    /** Still only on this device: published records never stay in DRAFT here. */
    val unsent: Boolean = false,
)

/**
 * Timeline reflects LOCAL Room state only — no hardcoded production data.
 * The app module hands over a live stream of this device's entries, so a record
 * written here or pulled from the change feed appears without any polling.
 */
class TimelineViewModel(
    observer: () -> Flow<List<TimelineItem>>,
    currentUserId: UUID? = null,
) : ViewModel() {

    private val names = MutableStateFlow("" to "")

    private val mutableEntries = MutableStateFlow<List<TimelineEntryUi>>(emptyList())
    val entries: StateFlow<List<TimelineEntryUi>> = mutableEntries.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observer(), names) { items, (own, partner) ->
                items.map { it.toEntryUi(own, partner, currentUserId) }
            }.collect { mutableEntries.value = it }
        }
    }

    fun updateNames(own: String, partner: String) {
        names.value = own.trim() to partner.trim()
    }
}

/** Author naming and date rendering shared by the timeline and a record's page. */
internal fun TimelineItem.toEntryUi(
    ownName: String,
    partnerName: String,
    currentUserId: UUID?,
): TimelineEntryUi {
    val zone = runCatching { ZoneId.of(occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
    val local = occurredAt.atZone(zone)
    val mine = currentUserId != null && authorId == currentUserId
    return TimelineEntryUi(
        id = id.toString(),
        dateLabel = local.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
        timeLabel = local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)),
        author = when {
            mine -> ownName.ifBlank { "我" }
            else -> partnerName.ifBlank { "伴侣" }
        },
        title = title?.takeIf { it.isNotBlank() },
        // Body must be the real content text; title is rendered separately.
        body = preview ?: title.orEmpty(),
        photo = photo,
        shared = mode == com.twomemory.model.EntryMode.COLLABORATIVE,
        mine = mine,
        unsent = state == com.twomemory.model.EntryState.DRAFT,
    )
}
