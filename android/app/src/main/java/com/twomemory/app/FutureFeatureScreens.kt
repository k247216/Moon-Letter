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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

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
)

/** The album is a view over entry blocks, not a second copy of the media. */
@Composable
fun AlbumPreviewScreen(
    media: List<AlbumMediaUi> = emptyList(),
    onOpenEntry: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("共同相册", style = TwoMemoryTypography.display)
                HandDrawnUnderline(170f)
            }
            Icon(TwoMemoryIcons.Album, contentDescription = "共同相册", tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            "照片和视频都回到它们原来的记录里",
            style = TwoMemoryTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        )
        if (media.isEmpty()) {
            AlbumEmptyState(Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                media.groupBy { it.monthLabel }.forEach { (month, monthMedia) ->
                    item(key = "month-$month") {
                        Text(month, style = TwoMemoryTypography.title, fontWeight = FontWeight.Medium)
                    }
                    items(monthMedia.chunked(2), key = { row -> row.joinToString("/") { it.id } }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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
        color = MaterialTheme.colorScheme.surface,
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
                "带照片或视频的记录保存后，这里会自动出现；相册不会复制或伪造一份新的回忆。",
                style = TwoMemoryTypography.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MediaTile(item: AlbumMediaUi, onOpenEntry: (String) -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.clickable { onOpenEntry(item.entryId) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (item.kind == "IMAGE") {
                EntryPhoto(
                    localPath = item.localPath,
                    assetId = item.assetId,
                    modifier = Modifier.height(136.dp),
                    contentDescription = "${item.author}在${item.dateLabel}的照片",
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(136.dp)
                        .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = .45f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(TwoMemoryIcons.Video, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("视频 · 点开原记录", style = TwoMemoryTypography.caption)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.dateLabel, style = TwoMemoryTypography.caption, modifier = Modifier.weight(1f))
                Text(item.timeLabel, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            }
        }
    }
}

/** City snapshots are intentionally list-first: a map outage cannot hide stories. */
@Composable
fun CityMapPreviewScreen(
    stories: List<CityStoryUi> = emptyList(),
    onOpenEntry: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(276.dp).background(Color(0xFFDDE8E1)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFFEFF0D8), radius = size.minDimension * .43f, center = Offset(size.width * .28f, size.height * .55f))
                drawCircle(Color(0xFFC9DDD7), radius = size.minDimension * .36f, center = Offset(size.width * .86f, size.height * .18f))
                drawCircle(Color(0xFF6F8268), radius = 9.dp.toPx(), center = Offset(size.width * .58f, size.height * .58f))
            }
            Column(modifier = Modifier.padding(26.dp)) {
                Text("我们的城市", style = TwoMemoryTypography.display)
                HandDrawnUnderline(178f)
                Spacer(Modifier.height(8.dp))
                Text("只记录你主动留下的一次城市快照", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                shadowElevation = 2.dp,
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(TwoMemoryIcons.Location, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(if (stories.isEmpty()) "还没有留下地点" else "${stories.map { it.city }.distinct().size} 个城市", style = TwoMemoryTypography.body)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 26.dp, vertical = 20.dp)) {
            Text("城市故事", style = TwoMemoryTypography.display)
            HandDrawnUnderline(168f)
            Spacer(Modifier.height(8.dp))
            if (stories.isEmpty()) {
                Text("点击记录里的“城市”后，这里会出现你们主动留下的地点。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stories, key = { it.id }) { story ->
                        CityStoryCard(story, onOpenEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun CityStoryCard(story: CityStoryUi, onOpenEntry: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(story.entryId) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(TwoMemoryIcons.Location, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(story.city, style = TwoMemoryTypography.title)
                Text("${story.dateLabel} · ${story.author}", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                story.title?.let { Text(it, style = TwoMemoryTypography.body, fontWeight = FontWeight.Medium) }
                if (story.preview.isNotBlank()) Text(story.preview, style = TwoMemoryTypography.body, maxLines = 2)
            }
            Icon(TwoMemoryIcons.Chevron, contentDescription = "打开城市故事", tint = TwoMemoryColors.WarmBeigeMuted)
        }
    }
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
