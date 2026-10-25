package com.twomemory.editor

import androidx.compose.runtime.Composable
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.model.EntryMode
import java.time.Instant

/** Both modes use one writing surface; the partner's contribution remains read-only. */
@Composable
fun SharedEditorRoute(
    state: EditorUiState,
    onPublish: () -> Unit,
    onTitleChange: (String) -> Unit = {},
    onBodyChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onModeChange: (EntryMode) -> Unit = {},
    photoActions: EditorPhotoActions? = null,
    attachmentActions: EditorAttachmentActions? = null,
    ownName: String = "",
    partnerName: String? = state.partnerName,
    partnerBody: String? = state.partnerBody,
    delivery: MoonLetterRecordStatus? = null,
    onRetryDelivery: () -> Unit = {},
    onOccurrenceChange: (Instant) -> Unit = {},
    onSaveDraft: (() -> Unit)? = null,
    onImportAudio: (() -> Unit)? = null,
    titleReadOnly: Boolean = state.appendingPerspective,
    ownPublishedBody: String? = state.ownPublishedBody,
    ownAvatar: androidx.compose.ui.graphics.ImageBitmap? = null,
    partnerAvatar: androidx.compose.ui.graphics.ImageBitmap? = null,
    currentUserId: java.util.UUID? = null,
    contributorIds: Set<java.util.UUID> = emptySet(),
    referenceBlocks: List<EditorReferenceBlock>? = null,
) {
    PersonalEditorScreen(
        state = state, onTitleChange = onTitleChange, onBodyChange = onBodyChange,
        onPublish = onPublish, onClose = onClose, mode = EntryMode.COLLABORATIVE,
        onModeChange = onModeChange, photoActions = photoActions,
        attachmentActions = attachmentActions, ownName = ownName, delivery = delivery,
        onRetryDelivery = onRetryDelivery, onOccurrenceChange = onOccurrenceChange,
        partnerName = partnerName, partnerBody = partnerBody,
        onSaveDraft = onSaveDraft, onImportAudio = onImportAudio,
        titleReadOnly = titleReadOnly, ownPublishedBody = ownPublishedBody,
        ownAvatar = ownAvatar, partnerAvatar = partnerAvatar,
        currentUserId = currentUserId, contributorIds = contributorIds,
        referenceBlocks = referenceBlocks,
    )
}
