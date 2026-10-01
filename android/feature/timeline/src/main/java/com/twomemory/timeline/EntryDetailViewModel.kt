package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.BlockType
import com.twomemory.model.EntryDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * comments under it. Loading and answering run through the callbacks the app
 * module supplies, so this stays unaware of Room and of the network.
 */
class EntryDetailViewModel(
    private val loader: suspend (String) -> EntryDetail?,
    private val addComment: suspend (String, String) -> Unit,
    private val currentUserId: UUID?,
) : ViewModel() {

    private var ownName: String = ""
    private var partnerName: String = ""
    private var entryId: String? = null

    private val mutableState = MutableStateFlow<EntryDetailUi?>(null)
    val state: StateFlow<EntryDetailUi?> = mutableState.asStateFlow()

    fun open(id: String) {
        entryId = id
        reload()
    }

    fun updateNames(own: String, partner: String) {
        ownName = own.trim()
        partnerName = partner.trim()
        reload()
    }

    fun updateDraft(draft: String) {
        mutableState.value = mutableState.value?.copy(draft = draft, error = null)
    }

    /** One comment, one explicit send: the text never reaches storage by itself. */
    fun sendComment() {
        val current = mutableState.value ?: return
        val id = entryId ?: return
        val body = current.draft.trim()
        if (body.isEmpty() || current.sending) return
        viewModelScope.launch {
            mutableState.value = current.copy(sending = true, error = null)
            try {
                addComment(id, body)
                mutableState.value = mutableState.value?.copy(sending = false, draft = "")
            } catch (failure: Exception) {
                mutableState.value = mutableState.value?.copy(
                    sending = false,
                    error = "这句回应没能存下来：${failure.message ?: "请重试"}",
                )
            }
            reload()
        }
    }

    private fun reload() {
        val id = entryId ?: return
        viewModelScope.launch {
            val detail = loader(id) ?: return@launch
            mutableState.value = detail.toUi(draft = mutableState.value?.draft.orEmpty())
        }
    }

    private fun EntryDetail.toUi(draft: String): EntryDetailUi {
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
            draft = draft,
        )
    }
}
