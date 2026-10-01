package com.twomemory.app

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
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

/**
 * M1 deliberately does not fake a populated media library. This screen keeps
 * the approved album hierarchy while stating the real product boundary.
 */
@Composable
fun AlbumPreviewScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("共同相册", style = TwoMemoryTypography.display)
                HandDrawnUnderline(170f)
            }
            Icon(TwoMemoryIcons.Album, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        SegmentedPreview("照片", "相册集")
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(TwoMemoryIcons.Image, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(18.dp))
                Text("照片会在这里按月份长出来", style = TwoMemoryTypography.title, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    "带照片的记录已经能可靠到达两台手机。这里还空着：相册的整理方式想等你们的照片真的多起来再定，不会用演示照片冒充你的回忆。",
                    style = TwoMemoryTypography.body,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun CityMapPreviewScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(310.dp)
                .background(Color(0xFFDDE8E1)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFFEFF0D8), radius = size.minDimension * .43f,
                    center = Offset(size.width * .28f, size.height * .55f))
                drawCircle(Color(0xFFC9DDD7), radius = size.minDimension * .36f,
                    center = Offset(size.width * .86f, size.height * .18f))
            }
            Column(modifier = Modifier.padding(26.dp)) {
                Text("我们的城市", style = TwoMemoryTypography.display)
                HandDrawnUnderline(178f)
                Spacer(Modifier.height(8.dp))
                Text("故事留在去过的地方", style = TwoMemoryTypography.body,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
            }
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(TwoMemoryIcons.Location, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(6.dp))
                    Text("还没有留下地点", style = TwoMemoryTypography.body)
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("我们的故事", style = TwoMemoryTypography.display)
            HandDrawnUnderline(168f)
            Text("城市故事尚未开放", style = TwoMemoryTypography.title)
            Text(
                "这里只会保存你主动添加的一次城市快照，不做实时定位，也不记录行动轨迹。",
                style = TwoMemoryTypography.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun SegmentedPreview(first: String, second: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outline.copy(alpha = .22f), RoundedCornerShape(28.dp))
            .padding(1.dp),
    ) {
        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(27.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = .16f)) {
            Text(first, modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center,
                style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium)
        }
        Text(second, modifier = Modifier.weight(1f).padding(vertical = 12.dp), textAlign = TextAlign.Center,
            style = TwoMemoryTypography.body)
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
