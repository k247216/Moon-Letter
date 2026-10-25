package com.twomemory.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.LocalEntryCommand
import com.twomemory.model.BlockType
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

/** A non-photo attachment kept with the draft until its block is published. */
data class EditorAttachment(
    val id: UUID,
    val type: BlockType,
    val localPath: String? = null,
    val mimeType: String? = null,
    val payload: String = "{}",
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
    val appendingPerspective: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val photos: List<EditorPhoto> = emptyList(),
    val attachments: List<EditorAttachment> = emptyList(),
    /** Set when persisting the entry failed; the screen shows it inline. */
    val error: String? = null,
    /** Set when a chosen picture could not be read in. */
    val photoError: String? = null,
    val recordStatus: MoonLetterRecordStatus = MoonLetterRecordStatus.DRAFT,
    /** This writer's already-published text; never submitted again as an append. */
    val ownPublishedBody: String? = null,
    val visibility: com.twomemory.model.EntryVisibility = com.twomemory.model.EntryVisibility.SHARED,
) {
    val hasContent: Boolean get() = (!appendingPerspective && title.isNotBlank()) || body.isNotBlank() || photos.isNotEmpty() || attachments.isNotEmpty()
}

class EditorViewModel : ViewModel() {
    private var sessionGeneration = 0L
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    /** Existing entry context is read-only; own text/media remain the new contribution. */
    fun setSharedContext(
        partnerName: String,
        partnerBody: String,
        title: String? = null,
        ownPublishedBody: String? = null,
        occurrenceTime: Instant? = null,
        timezone: String? = null,
    ) {
        mutableState.value = mutableState.value.copy(
            partnerName = partnerName.trim().takeIf { it.isNotEmpty() },
            partnerBody = partnerBody.takeIf { it.isNotBlank() },
            title = title ?: mutableState.value.title,
            appendingPerspective = true,
            ownPublishedBody = ownPublishedBody?.takeIf { it.isNotBlank() },
            occurrenceTime = occurrenceTime ?: mutableState.value.occurrenceTime,
            timezone = timezone ?: mutableState.value.timezone,
            occurrenceEdited = occurrenceTime != null || mutableState.value.occurrenceEdited,
        )
    }

    fun updateBody(body: String) {
        mutableState.value = mutableState.value.copy(body = body, saved = false, error = null, recordStatus = MoonLetterRecordStatus.DRAFT)
    }

    fun updateTitle(title: String) {
        if (mutableState.value.appendingPerspective) return
        mutableState.value = mutableState.value.copy(title = title, saved = false, error = null, recordStatus = MoonLetterRecordStatus.DRAFT)
    }

    fun updateVisibility(value: com.twomemory.model.EntryVisibility) {
        if (mutableState.value.appendingPerspective || mutableState.value.saving) return
        mutableState.value = mutableState.value.copy(visibility = value, saved = false)
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
        // Detach only. A saved record/restored draft may share this exact path.
        // File cleanup must check all persistent references, not just this editor.
        mutableState.value = mutableState.value.copy(
            photos = mutableState.value.photos.filterNot { it.id == id },
            photoError = null,
        )
    }

    fun addMediaAttachment(type: BlockType, localPath: String, mimeType: String) {
        mutableState.value = mutableState.value.copy(
            attachments = mutableState.value.attachments + EditorAttachment(
                id = UUID.randomUUID(),
                type = type,
                localPath = localPath,
                mimeType = mimeType,
                payload = org.json.JSONObject().put("mime", mimeType).toString(),
            ),
            saved = false,
            error = null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    /** Rename draft audio metadata without changing its media identity or bytes. */
    fun renameAudio(id: UUID, title: String) {
        val current = mutableState.value
        val attachment = current.attachments.firstOrNull { it.id == id && it.type == BlockType.AUDIO } ?: return
        val payload = try { org.json.JSONObject(attachment.payload) }
        catch (_: org.json.JSONException) {
            mutableState.value = current.copy(error = "这段语音的信息无法读取，标题未修改")
            return
        }
        val bounded = title.substring(0, title.offsetByCodePoints(0, minOf(80, title.codePointCount(0, title.length))))
        if (payload.optString("title") == bounded) return
        val renamed = attachment.copy(payload = payload.put("title", bounded).toString())
        mutableState.value = current.copy(
            attachments = current.attachments.map { if (it.id == id) renamed else it },
            saved = false,
            error = null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    fun addMusic(title: String, artist: String, url: String) {
        if (title.isBlank() && artist.isBlank() && url.isBlank()) return
        val payload = org.json.JSONObject()
            .put("title", title.trim())
            .put("artist", artist.trim())
            .put("url", url.trim())
            .toString()
        mutableState.value = mutableState.value.copy(
            attachments = mutableState.value.attachments + EditorAttachment(UUID.randomUUID(), BlockType.MUSIC, payload = payload),
            saved = false,
            error = null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    fun addCity(city: com.twomemory.model.CityOption) {
        if (mutableState.value.attachments.any { attachment ->
                attachment.type == BlockType.LOCATION && runCatching {
                    com.twomemory.model.CityCatalog.find(org.json.JSONObject(attachment.payload).optString("city"))?.name == city.name
                }.getOrDefault(false)
            }) return
        val payload = org.json.JSONObject()
            .put("city", city.name)
            .put("province", city.province)
            .put("latitude", city.latitude)
            .put("longitude", city.longitude)
            .toString()
        mutableState.value = mutableState.value.copy(
            attachments = mutableState.value.attachments + EditorAttachment(UUID.randomUUID(), BlockType.LOCATION, payload = payload),
            saved = false,
            error = null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    fun removeAttachment(id: UUID) {
        // Never delete a media file on detach: persisted records can own it already.
        mutableState.value = mutableState.value.copy(attachments = mutableState.value.attachments.filterNot { it.id == id })
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
        savedAttachments: List<EditorAttachment> = emptyList(),
        savedVisibility: com.twomemory.model.EntryVisibility = com.twomemory.model.EntryVisibility.SHARED,
    ) {
        if (mutableState.value.hasContent) return
        if (savedTitle.isBlank() && savedBody.isBlank() && savedPhotos.isEmpty() && savedAttachments.isEmpty()) return
        mutableState.value = mutableState.value.copy(
            title = if (mutableState.value.appendingPerspective) mutableState.value.title else savedTitle,
            body = savedBody,
            photos = savedPhotos,
            attachments = savedAttachments,
            visibility = savedVisibility,
            occurrenceTime = if (mutableState.value.appendingPerspective) mutableState.value.occurrenceTime
                else savedOccurrence ?: mutableState.value.occurrenceTime,
            occurrenceEdited = if (mutableState.value.appendingPerspective) mutableState.value.occurrenceEdited
                else savedOccurrence != null,
            recordStatus = MoonLetterRecordStatus.DRAFT,
        )
    }

    /** Clears a completed editing session before a fresh one begins. */
    fun reset() {
        sessionGeneration++
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
        val submittedGeneration = sessionGeneration
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true, error = null, recordStatus = MoonLetterRecordStatus.LOCAL_SAVED)
            try {
                save(current)
                if (sessionGeneration != submittedGeneration) return@launch
                val latest = mutableState.value
                val unchanged = latest.title == current.title && latest.body == current.body &&
                    latest.photos == current.photos && latest.attachments == current.attachments &&
                    latest.occurrenceTime == current.occurrenceTime && latest.visibility == current.visibility
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    saved = unchanged,
                    recordStatus = if (unchanged) MoonLetterRecordStatus.PENDING_SYNC else MoonLetterRecordStatus.DRAFT,
                )
            } catch (expected: Exception) {
                if (sessionGeneration != submittedGeneration) return@launch
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
