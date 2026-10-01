package com.twomemory.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography

/**
 * 手帐质感时间轴：按日分组的日期缝线标题、彩色作者圆点、纸面正文。
 * 不呈现条数/天数等任何统计（规格 §3.4）。
 */
@Composable
fun TimelineRoute(viewModel: TimelineViewModel) {
    val entries by viewModel.entries.collectAsState()
    TimelineScreen(entries)
}

@Composable
fun TimelineScreen(entries: List<TimelineEntryUi>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            TimelineHeader()
            Spacer(Modifier.height(20.dp))
        }
        entries.groupBy { it.dateLabel }.forEach { (day, dayEntries) ->
            item(key = "header-$day") {
                DayStitchHeader(day)
                Spacer(Modifier.height(10.dp))
            }
            items(dayEntries, key = { it.id }) { entry ->
                TimelineRow(entry, isMine = entry.author == "我")
                Spacer(Modifier.height(18.dp))
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun TimelineHeader() {
    Column {
        Text(
            "和你一起，把平凡的日子\n缝成闪闪发光的线。",
            style = TwoMemoryTypography.title,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(6.dp))
        Text("这里是我们亲手写下的一天又一天", style = TwoMemoryTypography.caption,
            color = Color(TwoMemoryColors.WarmBeigeMuted))
        Spacer(Modifier.height(14.dp))
        StitchDivider()
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
private fun DayStitchHeader(day: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
private fun TimelineRow(entry: TimelineEntryUi, isMine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
        }
    }
}
