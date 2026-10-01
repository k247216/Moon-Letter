package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.BlockType
import com.twomemory.model.EntryDetail
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

data class EntryBlockUi(
    val id: String,
    val text: String?,
    val localPath: String?,
    val assetId: String?,
)

data class EntryCommentUi(
    val id: String,
    val author: String,
    val body: String,
    val timeLabel: String,
    val mine: Boolean,
)

data class EntryDetailUi(
    val header: TimelineEntryUi,
    val blocks: List<EntryBlockUi>,
    val comments: List<EntryCommentUi>,
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)

/**
 * One record as a page: everything written in it, its pictures, and the
 * comments under it. The app module hands over a live stream of this device's
 * copy of the record, so a reply arriving later shows up without reopening.
 */
class EntryDetailViewModel(
    observer: () -> Flow<EntryDetail?>,
    private val addComment: suspend (String, String) -> Unit,
    private val currentUserId: UUID?,
) : ViewModel() {

    private val names = MutableStateFlow("" to "")

    private val mutableState = MutableStateFlow<EntryDetailUi?>(null)
    val state: StateFlow<EntryDetailUi?> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observer(), names) { detail, (own, partner) ->
                detail?.toUi(own, partner)
            }.collect { mutableState.value = it }
        }
    }

    fun updateNames(own: String, partner: String) {
        names.value = own.trim() to partner.trim()
    }

    fun updateDraft(draft: String) {
        mutableState.value = mutableState.value?.copy(draft = draft, error = null)
    }

    /** One comment, one explicit send: the text never reaches storage by itself. */
    fun sendComment() {
        val current = mutableState.value ?: return
        val body = current.draft.trim()
        if (body.isEmpty() || current.sending) return
        viewModelScope.launch {
            mutableState.value = current.copy(sending = true, error = null)
            try {
                addComment(current.header.id, body)
                mutableState.value = mutableState.value?.copy(sending = false, draft = "")
            } catch (failure: Exception) {
                mutableState.value = mutableState.value?.copy(
                    sending = false,
                    error = "这句回应没能存下来：${failure.message ?: "请重试"}",
                )
            }
        }
    }

    /**
     * What is already on the page survives a new emission: her half-typed reply,
     * the in-flight send, and the error she has not edited away yet.
     */
    private fun EntryDetail.toUi(ownName: String, partnerName: String): EntryDetailUi {
        val onScreen = mutableState.value
        val zone = runCatching { ZoneId.of(entry.occurredTimezone) }.getOrElse { ZoneId.of("UTC") }
        val timeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        return EntryDetailUi(
            header = entry.toEntryUi(ownName, partnerName, currentUserId),
            blocks = blocks.filter { it.type == BlockType.TEXT || it.type == BlockType.IMAGE }
                .sortedBy { it.orderKey }
                .map { block ->
                    EntryBlockUi(
                        id = block.id.toString(),
                        text = block.text,
                        localPath = block.localPath,
                        assetId = block.assetId,
                    )
                },
            comments = comments.map { comment ->
                val mine = currentUserId != null && comment.authorId == currentUserId
                EntryCommentUi(
                    id = comment.id.toString(),
                    author = when {
                        mine -> ownName.ifBlank { "我" }
                        else -> partnerName.ifBlank { "伴侣" }
                    },
                    body = comment.body,
                    timeLabel = comment.createdAt.atZone(zone).format(timeFormatter),
                    mine = mine,
                )
            },
            draft = onScreen?.draft.orEmpty(),
            sending = onScreen?.sending == true,
            error = onScreen?.error,
        )
    }
}
