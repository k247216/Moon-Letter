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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import java.util.UUID
import kotlinx.coroutines.launch

private const val MAX_PHOTOS = 6

/** What a record can do with its pictures: choose more, take one back out. */
class EditorPhotoActions(
    val pick: () -> Unit,
    val remove: (UUID) -> Unit,
)

@Composable
fun rememberEditorPhotoActions(viewModel: EditorViewModel): EditorPhotoActions =
    EditorPhotoActions(
        pick = rememberPhotoPicker(viewModel::addPhoto, viewModel::reportPhotoFailure),
        remove = viewModel::removePhoto,
    )

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
