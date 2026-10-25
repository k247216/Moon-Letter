package com.twomemory.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.model.BlockType
import com.twomemory.model.CityOption
import java.util.UUID
import kotlinx.coroutines.launch

private const val MAX_PHOTOS = 6

/** What a record can do with its pictures: choose more, take one back out. */
class EditorPhotoActions(
    val pick: () -> Unit,
    val remove: (UUID) -> Unit,
)

class EditorAttachmentActions(
    val pickVideo: () -> Unit,
    val pickAudio: () -> Unit,
    val addMusic: (title: String, artist: String, url: String) -> Unit,
    val addCity: (CityOption) -> Unit,
    val remove: (UUID) -> Unit,
    val renameAudio: ((UUID, String) -> Unit)? = null,
)

@Composable
fun rememberEditorPhotoActions(viewModel: EditorViewModel): EditorPhotoActions =
    EditorPhotoActions(
        pick = rememberPhotoPicker(viewModel::addPhoto, viewModel::reportPhotoFailure),
        remove = viewModel::removePhoto,
    )

@Composable
fun rememberEditorAttachmentActions(viewModel: EditorViewModel): EditorAttachmentActions =
    EditorAttachmentActions(
        pickVideo = rememberLocalMediaPicker(viewModel, BlockType.VIDEO, "video/*"),
        pickAudio = rememberAudioRecorder { audio ->
            viewModel.addMediaAttachment(BlockType.AUDIO, audio.file.absolutePath, audio.mimeType)
        },
        addMusic = viewModel::addMusic,
        addCity = viewModel::addCity,
        remove = viewModel::removeAttachment,
        renameAudio = viewModel::renameAudio,
    )

@Composable
private fun rememberAudioRecorder(onConfirmed: (RecordedAudio) -> Unit): () -> Unit {
    var show by remember { mutableStateOf(false) }
    if (show) AudioRecordingPanel(onConfirmed = onConfirmed, onDismiss = { show = false })
    return { show = true }
}

@Composable
private fun rememberLocalMediaPicker(
    viewModel: EditorViewModel,
    type: BlockType,
    mimeFilter: String,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val limit=if(type==BlockType.VIDEO) 32L*1024*1024 else 16L*1024*1024
            val stored = EntryPhotos.store(context, uri, UUID.randomUUID().toString(),maxBytes=limit)
            if (stored != null) viewModel.addMediaAttachment(type, stored.localPath, stored.mimeType)
            else viewModel.reportPhotoFailure(if(type==BlockType.VIDEO) "视频未导入，请选择32 MB以内的文件" else "音频未导入，请选择16 MB以内的文件")
        }
    }
    return { launcher.launch(mimeFilter) }
}

/**
 * Copies the chosen pictures into the app's own storage and reports each one.
 * The picker's uri only lives as long as this process does, so a record cannot
 * refer to it: the file the app keeps is what the uploader sends and what the
 * page shows.
 */
@Composable
fun rememberPhotoPicker(
    onPhotoAdded: (EditorPhoto) -> Unit,
    onPhotoFailed: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            for (uri in uris) {
                val id = UUID.randomUUID()
                val stored = EntryPhotos.store(context, uri, id.toString())
                if (stored == null) onPhotoFailed("这张照片没能读进来，换一张试试。")
                else onPhotoAdded(EditorPhoto(id, stored.localPath, stored.mimeType))
            }
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

@Composable
fun EditorPhotoStrip(
    photos: List<EditorPhoto>,
    photoError: String?,
    onRemove: (UUID) -> Unit,
) {
    if (photos.isEmpty() && photoError == null) return
    Column {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            photos.forEach { photo ->
                Box {
                    EntryPhoto(
                        localPath = photo.localPath,
                        assetId = null,
                        modifier = Modifier.size(104.dp),
                        contentDescription = "要附上的照片",
                    )
                    IconButton(
                        onClick = { onRemove(photo.id) },
                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                    ) {
                        Icon(TwoMemoryIcons.Close, contentDescription = "移除这张照片")
                    }
                }
            }
        }
        if (photoError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                photoError,
                style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun EditorAttachmentStrip(
    attachments: List<EditorAttachment>,
    onRemove: (UUID) -> Unit,
) {
    if (attachments.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            val label = when (attachment.type) {
                BlockType.VIDEO -> "视频片段"
                BlockType.AUDIO -> "语音片段"
                BlockType.MUSIC -> "音乐分享"
                BlockType.LOCATION -> "城市故事"
                else -> "附件"
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = TwoMemoryColors.WarmBeigeLine.copy(alpha = .28f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (attachment.type) {
                            BlockType.VIDEO -> TwoMemoryIcons.Video
                            BlockType.AUDIO -> TwoMemoryIcons.Voice
                            BlockType.MUSIC -> TwoMemoryIcons.Music
                            BlockType.LOCATION -> TwoMemoryIcons.Location
                            else -> TwoMemoryIcons.More
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                        val detail = runCatching { org.json.JSONObject(attachment.payload) }.getOrNull()
                        Text(
                            detail?.optString("title").orEmpty().ifBlank {
                                detail?.optString("city").orEmpty().ifBlank { attachment.mimeType ?: "已加入这一页" }
                            },
                            style = TwoMemoryTypography.body,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(TwoMemoryIcons.Close, contentDescription = "移除$label")
                    }
                }
            }
        }
    }
}
