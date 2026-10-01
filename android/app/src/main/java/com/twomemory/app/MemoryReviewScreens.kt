package com.twomemory.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

enum class MemoryReviewRoute { PAST_TODAY, WEEKLY_SUMMARY }

data class ReviewMemoryUi(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val author: String,
    val title: String?,
    val body: String,
    val mediaKinds: List<String> = emptyList(),
    val city: String? = null,
)

@Composable
fun MemoryReviewScreen(
    route: MemoryReviewRoute,
    memories: List<ReviewMemoryUi>,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val shown = when (route) {
        MemoryReviewRoute.PAST_TODAY -> memories.filter { it.id.startsWith("past:") }
        MemoryReviewRoute.WEEKLY_SUMMARY -> memories.filter { it.id.startsWith("week:") }
    }
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "返回我们")
                    Text("返回")
                }
                Text(
                    if (route == MemoryReviewRoute.PAST_TODAY) "过去的今天" else "本周小结",
                    style = TwoMemoryTypography.display,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            Text(
                if (route == MemoryReviewRoute.PAST_TODAY) {
                    "只回看同一个月日的更早记录，不复制原数据。"
                } else {
                    "把本周真实出现过的文字、照片、音乐和城市放在一起，不调用 AI。"
                },
                style = TwoMemoryTypography.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            if (shown.isEmpty()) {
                ReviewEmptyState(route, Modifier.weight(1f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.id }) { memory ->
                        ReviewMemoryCard(memory, onOpenEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewEmptyState(route: MemoryReviewRoute, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(TwoMemoryIcons.Time, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Text(
                if (route == MemoryReviewRoute.PAST_TODAY) "还没有更早的同日记录" else "本周还没有可组合的内容",
                style = TwoMemoryTypography.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun ReviewMemoryCard(memory: ReviewMemoryUi, onOpenEntry: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(memory.id.substringAfter(':')) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(memory.dateLabel, style = TwoMemoryTypography.body, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(memory.timeLabel, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            }
            Text(memory.author, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
            memory.title?.let { Text(it, style = TwoMemoryTypography.title) }
            if (memory.body.isNotBlank()) Text(memory.body, style = TwoMemoryTypography.body, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                memory.mediaKinds.forEach { kind ->
                    FilterChip(selected = true, onClick = {}, label = { Text(kind) })
                }
                memory.city?.let {
                    FilterChip(selected = true, onClick = {}, label = { Text(it) })
                }
            }
        }
    }
}
