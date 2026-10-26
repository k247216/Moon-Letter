package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.BlockType
import com.twomemory.model.EntryDetail
import com.twomemory.designsystem.MoonLetterRecordStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class EntryBlockUi(
    val id: String,
    val type: BlockType = BlockType.TEXT,
    val text: String?,
    val localPath: String?,
    val assetId: String?,
    val author: String? = null,
    val payload: String? = null,
    val mine: Boolean = false,
    val authorId: UUID? = null,
)

data class EntryCommentUi(
    val id: String,
    val author: String,
    val body: String,
    val timeLabel: String,
    val mine: Boolean,
    val signatureCoral: Boolean = mine,
)

data class EntryDetailUi(
    val header: TimelineEntryUi,
    val blocks: List<EntryBlockUi>,
    val comments: List<EntryCommentUi>,
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val status: MoonLetterRecordStatus = MoonLetterRecordStatus.SYNCED,
) {
    /** Only attributed content blocks count; replies and the entry creator do not. */
    val contributorIds: Set<UUID> get() = blocks.mapNotNull { it.authorId }.toSet()
}

/**
 * One record as a page: everything written in it, its pictures, and the
 * comments under it. The app module hands over a live stream of this device's
 * copy of the record, so a reply arriving later shows up without reopening.
 */
class EntryDetailViewModel(
    observer: () -> Flow<EntryDetail?>,
    private val addComment: suspend (String, String) -> Unit,
    private val currentUserId: UUID?,
    private val syncComment: suspend () -> Unit = {},
) : ViewModel() {

    private val names = MutableStateFlow(Triple("", "", null as UUID?))
    private val delivery = MutableStateFlow<MoonLetterRecordStatus?>(null)
    private var draftRevision = 0L

    private val mutableState = MutableStateFlow<EntryDetailUi?>(null)
    val state: StateFlow<EntryDetailUi?> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observer(), names, delivery) { detail, labels, queueStatus ->
                detail?.toUi(labels.first, labels.second, queueStatus, labels.third)
            }.collect { mutableState.value = it }
        }
    }

    fun updateNames(own: String, partner: String, coralSignerId: UUID? = null) {
        names.value = Triple(own.trim(), partner.trim(), coralSignerId)
    }

    fun updateDraft(draft: String) {
        draftRevision++
        mutableState.value = mutableState.value?.copy(draft = draft, error = null)
    }

    /** The caller supplies observed outbox state, never an inferred publish intent. */
    fun updateDelivery(status: MoonLetterRecordStatus?) {
        delivery.value = status
    }

    /** One comment, one explicit send: the text never reaches storage by itself. */
    fun sendComment() {
        val current = mutableState.value ?: return
        val body = current.draft.trim()
        if (body.isEmpty() || current.sending || current.status == MoonLetterRecordStatus.DRAFT ||
            current.status == MoonLetterRecordStatus.LOCAL_WRITE_FAILED) return
        val sendingRevision = draftRevision
        // Claim the send before dispatch; queued coroutines must not replace newer input.
        mutableState.value = current.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                addComment(current.header.id, body)
                val latest = mutableState.value
                if (latest?.header?.id == current.header.id) {
                    mutableState.value = latest.copy(sending = false,
                        draft = if (draftRevision == sendingRevision) "" else latest.draft)
                }
                // The reply is already durable. Transport must not hold the composer busy,
                // restore a sent draft, or report a local-save failure after that commit.
                viewModelScope.launch {
                    try { syncComment() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The persisted outbox is retried independently. */ }
                }
            } catch (cancelled: CancellationException) {
                val latest = mutableState.value
                if (latest?.header?.id == current.header.id) {
                    mutableState.value = latest.copy(sending = false)
                }
                throw cancelled
            } catch (failure: Exception) {
                val latest = mutableState.value
                if (latest?.header?.id == current.header.id) {
                    mutableState.value = latest.copy(
                        sending = false,
                        error = "这句回应没能存下来：${failure.message ?: "请重试"}",
                    )
                }
            }
        }
    }

    /**
     * What is already on the page survives a new emission: her half-typed reply,
     * the in-flight send, and the error she has not edited away yet.
     */
    private fun EntryDetail.toUi(ownName: String, partnerName: String, queueStatus: MoonLetterRecordStatus?, coralSignerId: UUID?): EntryDetailUi {
        val onScreen = mutableState.value?.takeIf { it.header.id == entry.id.toString() }
        val zone = runCatching { ZoneId.of(entry.occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
        val timeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        return EntryDetailUi(
            header = entry.toEntryUi(ownName, partnerName, currentUserId, coralSignerId = coralSignerId),
            blocks = blocks.sortedBy { it.orderKey }
                .map { block ->
                    EntryBlockUi(
                        id = block.id.toString(),
                        type = block.type,
                        text = block.text,
                        localPath = block.localPath,
                        assetId = block.assetId,
                        payload = block.payload,
                        mine = currentUserId != null && block.authorId == currentUserId,
                        authorId = block.authorId,
                        author = when {
                            block.authorId == null -> null
                            currentUserId == null -> "成员"
                            block.authorId == currentUserId -> ownName.ifBlank { "我" }
                            else -> partnerName.ifBlank { "对方" }
                        },
                    )
                },
            comments = comments.map { comment ->
                val mine = currentUserId != null && comment.authorId == currentUserId
                EntryCommentUi(
                    id = comment.id.toString(),
                    author = when {
                        comment.authorId == null || currentUserId == null -> "成员"
                        mine -> ownName.ifBlank { "我" }
                        else -> partnerName.ifBlank { "对方" }
                    },
                    body = comment.body,
                    timeLabel = comment.createdAt.atZone(zone).format(timeFormatter),
                    mine = mine,
                    signatureCoral = coralSignerId?.let { comment.authorId == it } ?: mine,
                )
            },
            draft = onScreen?.draft.orEmpty(),
            sending = onScreen?.sending == true,
            error = onScreen?.error,
            status = when (entry.state) {
                com.twomemory.model.EntryState.DRAFT -> queueStatus ?: MoonLetterRecordStatus.DRAFT
                com.twomemory.model.EntryState.PUBLISHED -> queueStatus ?: MoonLetterRecordStatus.SYNCED
                else -> MoonLetterRecordStatus.DRAFT
            },
        )
    }
}
