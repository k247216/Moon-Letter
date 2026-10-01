package com.twomemory.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.R
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

/**
 * 手帐质感时间轴：按日分组的日期缝线标题、彩色作者圆点、纸面正文。
 * 不呈现条数/天数等任何统计（规格 §3.4）。
 */
@Composable
fun TimelineRoute(
    viewModel: TimelineViewModel,
    coverBitmap: ImageBitmap? = null,
    onChangeCover: () -> Unit = {},
) {
    val entries by viewModel.entries.collectAsState()
    TimelineScreen(entries, coverBitmap, onChangeCover)
}

@Composable
fun TimelineScreen(
    entries: List<TimelineEntryUi>,
    coverBitmap: ImageBitmap? = null,
    onChangeCover: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            TimelineHeader(coverBitmap, onChangeCover)
            Spacer(Modifier.height(22.dp))
        }
        if (entries.isEmpty()) {
            item { EmptyTimeline() }
        }
        entries.groupBy { it.dateLabel }.forEach { (day, dayEntries) ->
            item(key = "header-$day") {
                DayStitchHeader(day, Modifier.padding(horizontal = 28.dp))
                Spacer(Modifier.height(10.dp))
            }
            items(dayEntries, key = { it.id }) { entry ->
                TimelineRow(
                    entry,
                    isMine = entry.mine,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
                Spacer(Modifier.height(18.dp))
            }
        }
        item { Spacer(Modifier.height(36.dp)) }
    }
}

@Composable
private fun TimelineHeader(coverBitmap: ImageBitmap?, onChangeCover: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(238.dp)) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = "首页封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.moonletter_cover_default),
                contentDescription = "首页封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Color(0x440F1722)))
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 42.dp, end = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "和你一起，\n把平凡的日子\n缝成闪闪发光的线。",
                style = TwoMemoryTypography.title,
                color = Color.White,
            )
            Text("中秋，是我们的纪念日", style = TwoMemoryTypography.caption, color = Color(0xFFFFE1A8))
        }
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(topStart = 20.dp))
                .clickable(onClick = onChangeCover).background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TwoMemoryIcons.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text("换一张", style = TwoMemoryTypography.caption, color = Color.White)
        }
    }
    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
        Text("今天", style = TwoMemoryTypography.display, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        StitchDivider()
    }
}

@Composable
private fun EmptyTimeline() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 42.dp, vertical = 48.dp),
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

/** 一条横向虚线缝线。 */
@Composable
private fun StitchDivider() {
    val line = Color(TwoMemoryColors.WarmBeigeLine)
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = line,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

/** 日期标题：居中文字 + 两侧缝线，参考稿的分组日样式。 */
@Composable
private fun DayStitchHeader(day: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StitchDivider(modifier = Modifier.weight(1f))
        Text(
            day,
            style = TwoMemoryTypography.caption,
            color = Color(TwoMemoryColors.WarmBeigeMuted),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StitchDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StitchDivider(modifier: Modifier = Modifier) {
    val line = Color(TwoMemoryColors.WarmBeigeLine)
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = line,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntryUi, isMine: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (isMine) Color(TwoMemoryColors.WarmBeigeAccent) else Color(0xFF8A9A7B)),
            )
            // 竖向缝线，把日子缝在一起
            Canvas(Modifier.width(2.dp).height(120.dp)) {
                drawLine(
                    color = Color(TwoMemoryColors.WarmBeigeLine),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = size.width,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(
                            if (isMine) Color(TwoMemoryColors.WarmBeigeAccent).copy(alpha = 0.18f)
                            else Color(0xFF8A9A7B).copy(alpha = 0.18f),
                        ),
                ) {
                    Text(
                        entry.author.take(1),
                        style = TwoMemoryTypography.caption,
                        color = if (isMine) Color(TwoMemoryColors.WarmBeigeAccent) else Color(0xFF8A9A7B),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(entry.author, style = TwoMemoryTypography.caption)
                if (entry.shared) {
                    Spacer(Modifier.size(7.dp))
                    Text(
                        "共同记录",
                        style = TwoMemoryTypography.caption,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = .10f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    entry.timeLabel,
                    style = TwoMemoryTypography.caption,
                    color = Color(TwoMemoryColors.WarmBeigeMuted),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (entry.title != null) {
                Text(entry.title, style = TwoMemoryTypography.title)
                Spacer(Modifier.height(4.dp))
            }
            if (entry.body.isNotBlank()) {
                Text(entry.body, style = TwoMemoryTypography.body)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TwoMemoryIcons.Like, contentDescription = "喜欢", tint = Color(TwoMemoryColors.WarmBeigeAccent),
                    modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(16.dp))
                Icon(TwoMemoryIcons.Comment, contentDescription = "评论", tint = Color(TwoMemoryColors.WarmBeigeMuted),
                    modifier = Modifier.size(21.dp))
            }
        }
    }
}
