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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.twomemory.designsystem.paperTexture

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
                Text("共同相册", style = TwoMemoryTypography.display)
                HandDrawnUnderline(150f)
            }
            Icon(TwoMemoryIcons.Album, contentDescription = "共同相册", tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            "照片和视频都回到它们原来的记录里",
            style = TwoMemoryTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("全部 ${media.size}") })
            FilterChip(selected = filter == "IMAGE", onClick = { filter = "IMAGE" }, label = { Text("照片") })
            FilterChip(selected = filter == "VIDEO", onClick = { filter = "VIDEO" }, label = { Text("视频") })
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
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (item.kind == "IMAGE") {
                EntryPhoto(
                    localPath = item.localPath,
                    assetId = item.assetId,
                    modifier = Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp)),
                    contentDescription = "${item.author}在${item.dateLabel}的照片",
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                        .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = .45f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(TwoMemoryIcons.Video, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("视频 · 点开原记录", style = TwoMemoryTypography.caption)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.dateLabel, style = TwoMemoryTypography.caption, maxLines = 1, modifier = Modifier.weight(1f))
                Text(item.author, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted, maxLines = 1)
            }
        }
    }
}

/** City snapshots are intentionally list-first: a map outage cannot hide stories. */
@Composable
fun CityMapPreviewScreen(
    stories: List<CityStoryUi> = emptyList(),
    onOpenEntry: (String) -> Unit = {},
    onSaveCitySnapshot: (String) -> Unit = {},
    snapshotStatus: String? = null,
) {
    var showSnapshotDialog by rememberSaveable { mutableStateOf(false) }
    var cityDraft by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(276.dp).background(Color(0xFFE3ECE4)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFFF2F0D9), radius = size.minDimension * .43f, center = Offset(size.width * .28f, size.height * .55f))
                drawCircle(Color(0xFFC9DDD7), radius = size.minDimension * .36f, center = Offset(size.width * .86f, size.height * .18f))
                drawCircle(Color(0xFFD6E1C7), radius = size.minDimension * .28f, center = Offset(size.width * .62f, size.height * .84f))
                val route = listOf(
                    Offset(size.width * .18f, size.height * .62f),
                    Offset(size.width * .43f, size.height * .37f),
                    Offset(size.width * .69f, size.height * .58f),
                    Offset(size.width * .84f, size.height * .30f),
                )
                route.zipWithNext().forEach { (from, to) ->
                    drawLine(
                        color = TwoMemoryColors.WarmBeigeSage.copy(alpha = .74f),
                        start = from,
                        end = to,
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    )
                }
                route.take(stories.size.coerceAtMost(route.size)).forEach { point ->
                    drawCircle(TwoMemoryColors.WarmBeigeAccent, radius = 8.dp.toPx(), center = point)
                    drawCircle(Color.White.copy(alpha = .86f), radius = 3.dp.toPx(), center = point)
                }
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
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(TwoMemoryIcons.Location, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (stories.isEmpty()) "还没有留下地点" else "${stories.map { it.city }.distinct().size} 个城市", style = TwoMemoryTypography.body)
                    Button(onClick = { showSnapshotDialog = true }) { Text("记录城市") }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 26.dp, vertical = 20.dp)) {
            Text("城市故事", style = TwoMemoryTypography.display)
            HandDrawnUnderline(168f)
            Spacer(Modifier.height(8.dp))
            snapshotStatus?.let {
                Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }
            if (stories.isEmpty()) {
                Text("记录一个城市后，这里会出现你们主动留下的地点。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stories, key = { it.id }) { story ->
                        CityStoryCard(story, onOpenEntry)
                    }
                }
            }
        }
    }
    if (showSnapshotDialog) {
        AlertDialog(
            onDismissRequest = { showSnapshotDialog = false },
            icon = { Icon(TwoMemoryIcons.Location, contentDescription = null) },
            title = { Text("记录一次城市快照") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("只保存城市级名称，不做实时定位或轨迹。", style = TwoMemoryTypography.body)
                    OutlinedTextField(
                        value = cityDraft,
                        onValueChange = { cityDraft = it },
                        label = { Text("城市名称") },
                        placeholder = { Text("例如：杭州") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = cityDraft.isNotBlank(),
                    onClick = {
                        onSaveCitySnapshot(cityDraft.trim())
                        cityDraft = ""
                        showSnapshotDialog = false
                    },
                ) { Text("保存快照") }
            },
            dismissButton = { TextButton(onClick = { showSnapshotDialog = false }) { Text("取消") } },
        )
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
