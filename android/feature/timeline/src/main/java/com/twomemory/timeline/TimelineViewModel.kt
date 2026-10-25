package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.BlockType
import com.twomemory.model.TimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
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
    /** What this record still owes the server was refused outright, so it waits forever. */
    val rejected: Boolean = false,
    val attachments: List<TimelineAttachmentUi> = emptyList(),
    val unread: Boolean = false,
    val privateRecord: Boolean = false,
    val littleNote: Boolean = false,
)

data class TimelineAttachmentUi(
    val type: BlockType,
    val title: String? = null,
    val detail: String? = null,
    val url: String? = null,
    val localPath: String? = null,
    val assetId: String? = null,
)

/**
 * Timeline reflects LOCAL Room state only — no hardcoded production data.
 * The app module hands over a live stream of this device's entries, so a record
 * written here or pulled from the change feed appears without any polling.
 */
class TimelineViewModel(
    observer: () -> Flow<List<TimelineItem>>,
    currentUserId: UUID? = null,
    preparationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val names = MutableStateFlow("" to "")
    private val rejectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val readIds = MutableStateFlow<Set<String>>(emptySet())

    private val mutableEntries = MutableStateFlow<List<TimelineEntryUi>>(emptyList())
    val entries: StateFlow<List<TimelineEntryUi>> = mutableEntries.asStateFlow()
    private val mutableInitialLoading = MutableStateFlow(true)
    val initialLoading: StateFlow<Boolean> = mutableInitialLoading.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observer(), names, rejectedIds, readIds) { items, (own, partner), rejected, read ->
                items.map { item -> item.toEntryUi(own, partner, currentUserId, item.id.toString() in rejected)
                    .copy(unread = currentUserId != null && item.authorId != currentUserId &&
                        item.state == com.twomemory.model.EntryState.PUBLISHED && item.id.toString() !in read) }
            }.flowOn(preparationDispatcher).collect {
                mutableEntries.value = it
                mutableInitialLoading.value = false
            }
        }
    }

    fun updateNames(own: String, partner: String) {
        names.value = own.trim() to partner.trim()
    }

    /** Records this phone still owes the server an operation it refused. */
    fun updateRejectedIds(ids: Set<String>) {
        rejectedIds.value = ids
    }
    fun updateReadIds(ids: Set<String>) { readIds.value = ids }
}

/** Author naming and date rendering shared by the timeline and a record's page. */
internal fun TimelineItem.toEntryUi(
    ownName: String,
    partnerName: String,
    currentUserId: UUID?,
    rejected: Boolean = false,
): TimelineEntryUi {
    val zone = runCatching { ZoneId.of(occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
    val local = occurredAt.atZone(zone)
    val mine = currentUserId != null && authorId == currentUserId
    return TimelineEntryUi(
        id = id.toString(),
        dateLabel = local.format(timelineDateFormatter),
        timeLabel = local.format(timelineTimeFormatter),
        author = when {
            mine -> ownName.ifBlank { "我" }
            else -> partnerName.ifBlank { "对方" }
        },
        title = title?.takeIf { it.isNotBlank() },
        // Body must be the real content text; title is rendered separately.
        body = preview ?: title.orEmpty(),
        photo = photo,
        shared = mode == com.twomemory.model.EntryMode.COLLABORATIVE,
        mine = mine,
        unsent = state == com.twomemory.model.EntryState.DRAFT,
        rejected = rejected,
        privateRecord = visibility == com.twomemory.model.EntryVisibility.PRIVATE,
        littleNote = isLittleNote,
        attachments = attachments.map { TimelineAttachmentUi(it.type, it.title, it.detail, it.url, it.localPath, it.assetId) },
    )
}

private val timelineDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
private val timelineTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
