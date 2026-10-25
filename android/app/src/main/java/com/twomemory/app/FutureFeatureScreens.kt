package com.twomemory.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.PaperRule
import com.twomemory.designsystem.PaperSectionHeading
import com.twomemory.designsystem.PolaroidFrame
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.paperTexture
import com.twomemory.model.CityCatalog
import com.twomemory.model.CityOption

data class AlbumMediaUi(
    val id: String,
    val entryId: String,
    val monthLabel: String,
    val dateLabel: String,
    val timeLabel: String,
    val author: String,
    val kind: String,
    val localPath: String?,
    val assetId: String?,
)

data class CityStoryUi(
    val id: String,
    val entryId: String,
    val city: String,
    val dateLabel: String,
    val title: String?,
    val preview: String,
    val author: String,
    val photoLocalPath: String? = null,
    val photoAssetId: String? = null,
    val timeLabel: String = "",
    val mine: Boolean = false,
    val shared: Boolean = false,
    val attachments: List<com.twomemory.timeline.TimelineAttachmentUi> = emptyList(),
    val occurredAtEpochMillis: Long = 0,
)

/** The album is a view over entry blocks, not a second copy of the media. */
@Composable
fun AlbumPreviewScreen(
    media: List<AlbumMediaUi> = emptyList(),
    onOpenEntry: (String) -> Unit = {},
    onAddPhotos: () -> Unit = {},
) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val visibleMedia = when (filter) {
        "IMAGE" -> media.filter { it.kind == "IMAGE" }
        "VIDEO" -> media.filter { it.kind != "IMAGE" }
        else -> media
    }
    Column(
        modifier = Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                PaperSectionHeading("共同相册", subtitle = "照片都回到它们原来的记录里")
            }
            TextButton(onClick = onAddPhotos, modifier = Modifier.semantics { contentDescription = "新建相册" }) {
                Icon(TwoMemoryIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("新建相册")
            }
        }
        PaperRule()
        val videoInAlbum = media.any { it.kind != "IMAGE" }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("全部") })
            FilterChip(selected = filter == "IMAGE", onClick = { filter = "IMAGE" }, label = { Text("照片") })
            if (videoInAlbum) {
                FilterChip(selected = filter == "VIDEO", onClick = { filter = "VIDEO" }, label = { Text("视频") })
            } else {
                FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("视频") })
            }
        }
        if (!videoInAlbum) {
            Text(
                "视频上传还没有接通，所以这里现在只有照片。",
                style = TwoMemoryTypography.caption,
                color = TwoMemoryColors.WarmBeigeMuted,
            )
        }
        if (visibleMedia.isEmpty()) {
            AlbumEmptyState(Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                visibleMedia.groupBy { it.monthLabel }.forEach { (month, monthMedia) ->
                    item(key = "month-$month") {
                        Text(month, style = TwoMemoryTypography.title, fontWeight = FontWeight.Medium)
                    }
                    items(monthMedia.chunked(3), key = { row -> row.joinToString("/") { it.id } }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { item ->
                                MediaTile(item, onOpenEntry, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun AlbumEmptyState(modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(TwoMemoryIcons.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(18.dp))
            Text("照片会在这里按月份长出来", style = TwoMemoryTypography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "带照片的记录保存后，这里会自动出现；相册不会复制或伪造一份新的回忆。",
                style = TwoMemoryTypography.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MediaTile(item: AlbumMediaUi, onOpenEntry: (String) -> Unit, modifier: Modifier) {
    PolaroidFrame(
        modifier = modifier.clickable { onOpenEntry(item.entryId) },
        rotation = if (item.id.hashCode() % 2 == 0) 1.1f else -1.1f,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (item.kind == "IMAGE") {
                EntryPhoto(
                    localPath = item.localPath,
                    assetId = item.assetId,
                    modifier = Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(2.dp)),
                    contentDescription = "${item.author}在${item.dateLabel}的照片",
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                        .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = .45f), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(TwoMemoryIcons.Video, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("视频 · 点开原记录", style = TwoMemoryTypography.caption)
                    }
                }
            }
            Text(item.dateLabel, style = TwoMemoryTypography.metadata, maxLines = 1)
            Text(item.author, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted, maxLines = 1)
        }
    }
}


@Composable
internal fun CityStoryCard(story: CityStoryUi, onOpenEntry: (String) -> Unit) {
    com.twomemory.timeline.JournalEntryCard(
        com.twomemory.timeline.TimelineEntryUi(story.entryId, story.dateLabel, story.timeLabel, story.author,
            story.title, body = story.preview,
            photo = if (story.photoLocalPath != null || story.photoAssetId != null)
                com.twomemory.model.TimelinePhoto(story.photoLocalPath, story.photoAssetId) else null,
            mine = story.mine, shared = story.shared, attachments = story.attachments),
        onOpenEntry, alternate = story.id.hashCode() % 2 == 0,
    )
}

@Composable
private fun HandDrawnUnderline(width: Float) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawLine(
            color = accent,
            start = Offset(0f, size.height * .55f),
            end = Offset(width.coerceAtMost(size.width), size.height * .35f),
            strokeWidth = 4f,
            pathEffect = PathEffect.cornerPathEffect(5f),
        )
    }
}
