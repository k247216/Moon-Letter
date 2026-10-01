package com.twomemory.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.LocalEntryCommand
import com.twomemory.designsystem.MoonLetterRecordStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** One attached picture; [id] becomes the block id and the stored file's name. */
data class EditorPhoto(
    val id: UUID,
    val localPath: String,
    val mimeType: String,
)

data class EditorUiState(
    val title: String = "",
    val body: String = "",
    val occurrenceTime: Instant = Instant.now(),
    /** The zone this phone is in: what the record stores as 故事发生时区. */
    val timezone: String = ZoneId.systemDefault().id,
    /** True once the writer picked a moment, so a page can stop saying 此刻. */
    val occurrenceEdited: Boolean = false,
    val authors: List<String> = listOf("我"),
    /** Read-only partner block supplied when opening an existing shared record. */
    val partnerName: String? = null,
    val partnerBody: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val photos: List<EditorPhoto> = emptyList(),
    /** Set when persisting the entry failed; the screen shows it inline. */
    val error: String? = null,
    /** Set when a chosen picture could not be read in. */
    val photoError: String? = null,
    val recordStatus: MoonLetterRecordStatus = MoonLetterRecordStatus.DRAFT,
) {
    val hasContent: Boolean get() = title.isNotBlank() || body.isNotBlank() || photos.isNotEmpty()
}

class EditorViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    fun updateBody(body: String) {
        mutableState.value = mutableState.value.copy(body = body, saved = false, error = null, recordStatus = MoonLetterRecordStatus.DRAFT)
    }

    fun updateTitle(title: String) {
        mutableState.value = mutableState.value.copy(title = title, saved = false, error = null, recordStatus = MoonLetterRecordStatus.DRAFT)
    }

    fun addPhoto(photo: EditorPhoto) {
        mutableState.value = mutableState.value.copy(
            photos = mutableState.value.photos + photo,
            saved = false,
            photoError = null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    fun removePhoto(id: UUID) {
        // The picker's copy in filesDir belongs to this editor alone: a published
        // record owns its own file, a dropped one owns nothing.
        mutableState.value.photos.firstOrNull { it.id == id }
            ?.let { java.io.File(it.localPath).delete() }
        mutableState.value = mutableState.value.copy(
            photos = mutableState.value.photos.filterNot { it.id == id },
            photoError = null,
        )
    }

    fun reportPhotoFailure(message: String) {
        mutableState.value = mutableState.value.copy(photoError = message)
    }

    /**
     * When the story happened. Writing about this morning at night must not record
     * the evening, so this is the writer's answer rather than the clock's.
     */
    fun updateOccurrence(occurrenceTime: Instant) {
        mutableState.value = mutableState.value.copy(
            occurrenceTime = occurrenceTime,
            occurrenceEdited = true,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    /** Restores an interrupted session's text so a killed process loses nothing. */
    fun restore(
        savedTitle: String,
        savedBody: String,
        savedPhotos: List<EditorPhoto> = emptyList(),
        savedOccurrence: Instant? = null,
    ) {
        if (mutableState.value.hasContent) return
        if (savedTitle.isBlank() && savedBody.isBlank() && savedPhotos.isEmpty()) return
        mutableState.value = mutableState.value.copy(
            title = savedTitle,
            body = savedBody,
            photos = savedPhotos,
            occurrenceTime = savedOccurrence ?: mutableState.value.occurrenceTime,
            occurrenceEdited = savedOccurrence != null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    /** Clears a completed editing session before a fresh one begins. */
    fun reset() {
        mutableState.value = EditorUiState()
    }

    /**
     * Persisting runs in the caller-supplied suspend block; failures land in
     * [EditorUiState.error] instead of crashing the process, so the text
     * stays on screen and can be retried.
     */
    fun publish(save: suspend (EditorUiState) -> Unit) {
        val current = mutableState.value
        if (current.saving || !current.hasContent) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true, error = null, recordStatus = MoonLetterRecordStatus.LOCAL_SAVED)
            try {
                save(mutableState.value)
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    saved = true,
                    recordStatus = MoonLetterRecordStatus.PENDING_SYNC,
                )
            } catch (expected: Exception) {
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    saved = false,
                    error = expected.message ?: "保存失败，请重试",
                    recordStatus = MoonLetterRecordStatus.LOCAL_WRITE_FAILED,
                )
            }
        }
    }
}
