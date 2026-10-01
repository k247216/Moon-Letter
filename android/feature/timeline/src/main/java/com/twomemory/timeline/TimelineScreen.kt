package com.twomemory.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.R
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

/** Continuous paper timeline. The source of entries remains the Room-backed ViewModel. */
@Composable
fun TimelineRoute(
    viewModel: TimelineViewModel,
    coverBitmap: ImageBitmap? = null,
    onChangeCover: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    val entries by viewModel.entries.collectAsState()
    TimelineScreen(entries, coverBitmap, onChangeCover, onOpen)
}

@Composable
fun TimelineScreen(
    entries: List<TimelineEntryUi>,
    coverBitmap: ImageBitmap? = null,
    onChangeCover: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { TimelineHeader(coverBitmap, onChangeCover) }
        if (entries.isEmpty()) {
            item { EmptyTimeline() }
        }
        items(entries, key = { it.id }) { entry ->
            TimelineRow(entry = entry, onOpen = onOpen, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(22.dp))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun TimelineHeader(coverBitmap: ImageBitmap?, onChangeCover: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(198.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = "首页封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color(0x520F1722)))
        }
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 29.dp, top = 42.dp, end = 58.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "我们的时光",
                style = TwoMemoryTypography.display,
                color = if (coverBitmap == null) MaterialTheme.colorScheme.onBackground else Color.White,
            )
            Text(
                "中秋 · 仍在一起写着",
                style = TwoMemoryTypography.body,
                color = if (coverBitmap == null) MaterialTheme.colorScheme.onBackground.copy(alpha = .82f) else Color.White.copy(alpha = .88f),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onChangeCover)
                .background(
                    if (coverBitmap == null) MaterialTheme.colorScheme.surface.copy(alpha = .72f)
                    else Color.Black.copy(alpha = .28f),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .padding(top = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TwoMemoryIcons.Camera,
                contentDescription = "更换背景图",
                tint = if (coverBitmap == null) MaterialTheme.colorScheme.onSurface else Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "换一张",
                style = TwoMemoryTypography.caption,
                color = if (coverBitmap == null) MaterialTheme.colorScheme.onSurface else Color.White,
            )
        }
    }
    Spacer(Modifier.height(13.dp))
}

@Composable
private fun EmptyTimeline() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("这里会长出你们的第一段时光", style = TwoMemoryTypography.title, textAlign = TextAlign.Center)
        Text(
            "点下面的“＋记录”，先写一句此刻的心情就好。",
            style = TwoMemoryTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayStitchHeader(day: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StitchDivider(modifier = Modifier.weight(1f))
        Text(day, style = TwoMemoryTypography.title, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 12.dp))
        StitchDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StitchDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = TwoMemoryColors.WarmBeigeLine,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)),
        )
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntryUi, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxHeight().width(2.dp)) {
                drawLine(
                    color = TwoMemoryColors.WarmBeigeLine,
                    start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height),
                    strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
                )
            }
            Box(
                Modifier.padding(top = 8.dp).size(12.dp).clip(CircleShape)
                    .background(if (entry.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF8A9A7B)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.weight(1f).semantics { contentDescription = "打开记录" }.clickable { onOpen(entry.id) },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeLine),
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.dateLabel, style = TwoMemoryTypography.body, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text(entry.timeLabel, style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeMuted)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(if (entry.mine) R.drawable.moonletter_avatar_xiaoman else R.drawable.moonletter_avatar_ayu),
                        contentDescription = "${entry.author}的头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(entry.author, style = TwoMemoryTypography.title, color = if (entry.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF6F8268))
                    if (entry.shared) {
                        Spacer(Modifier.width(8.dp))
                        Text("共同", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    if (entry.unsent) Text("未同步", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
                Spacer(Modifier.height(9.dp))
                entry.title?.let {
                    Text(it, style = TwoMemoryTypography.title, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp))
                }
                if (entry.body.isNotBlank()) Text(entry.body, style = TwoMemoryTypography.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                entry.photo?.let { photo ->
                    Spacer(Modifier.height(11.dp))
                    EntryPhoto(
                        localPath = photo.localPath,
                        assetId = photo.assetId,
                        modifier = Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(9.dp)).rotate(if (entry.mine) 1.2f else -1.2f),
                        contentDescription = "记录里的照片",
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TwoMemoryIcons.Comment, contentDescription = "评论", tint = TwoMemoryColors.WarmBeigeMuted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("打开这段记录", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
            }
        }
    }
}
