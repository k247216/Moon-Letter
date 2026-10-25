package com.twomemory.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.AudioMemoryCard
import com.twomemory.designsystem.MusicMemoryCard
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.PolaroidFrame
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.model.BlockType
import java.util.UUID

/** Context is plain text, so it cannot mutate the other member or the stored contribution. */
@Composable
internal fun EditorReadOnlyContext(label: String, body: String, accent: Color) {
    Column(Modifier.fillMaxWidth().drawBehind {
        drawLine(accent.copy(alpha = .5f), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height),
            1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    }.padding(start = 18.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = TwoMemoryTypography.caption, color = accent)
        Text(body, style = TwoMemoryTypography.body)
    }
}

@Composable
internal fun EditorPhotoBlocks(photos: List<EditorPhoto>, photoError: String?, onRemove: (UUID) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        photoError?.let { Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error) }
        photos.forEachIndexed { index, photo -> key(photo.id) {
            Box(Modifier.fillMaxWidth()) {
                PolaroidFrame(modifier = Modifier.fillMaxWidth().height(220.dp), photo = {
                    EntryPhoto(localPath = photo.localPath, assetId = null, modifier = Modifier.fillMaxSize(),
                        contentDescription = "第${index + 1}张已添加照片")
                })
                IconButton(onClick = { onRemove(photo.id) }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                    Icon(TwoMemoryIcons.Close, "移除第${index + 1}张照片")
                }
            }
        } }
    }
}

/** Ordered real attachments in the writer's contribution, with no invented media. */
@Composable
internal fun EditorContentBlocks(
    attachments: List<EditorAttachment>,
    onRemove: (UUID) -> Unit,
    onRenameAudio: ((UUID, String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        attachments.forEach { attachment -> key(attachment.id) {
            when (attachment.type) {
                BlockType.MUSIC, BlockType.AUDIO -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val payload = runCatching { org.json.JSONObject(attachment.payload) }.getOrNull()
                    if (attachment.type == BlockType.MUSIC) MusicMemoryCard(
                        title = payload?.optString("title").orEmpty(),
                        artist = payload?.optString("artist").orEmpty(),
                        url = payload?.optString("url").orEmpty(), modifier = Modifier.weight(1f),
                    ) else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val title = payload?.optString("title").orEmpty()
                        if (onRenameAudio != null) BasicTextField(
                            value = title,
                            onValueChange = { onRenameAudio(attachment.id, it) },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "语音标题：${attachment.id}"
                            },
                            singleLine = true,
                            textStyle = TwoMemoryTypography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                            decorationBox = { inner -> Box {
                                if (title.isEmpty()) Text("给这段声音起个名字", style = TwoMemoryTypography.body,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
                                inner()
                            } },
                        ) else if (title.isNotBlank()) Text(title, style = TwoMemoryTypography.body,
                            color = MaterialTheme.colorScheme.onSurface)
                        AudioMemoryCard(localPath = attachment.localPath, assetId = null, modifier = Modifier.fillMaxWidth())
                    }
                    IconButton(onClick = { onRemove(attachment.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(TwoMemoryIcons.Close, if (attachment.type == BlockType.MUSIC) "移除音乐" else "移除语音")
                    }
                }
                else -> EditorAttachmentStrip(listOf(attachment), onRemove)
            }
        } }
    }
}
