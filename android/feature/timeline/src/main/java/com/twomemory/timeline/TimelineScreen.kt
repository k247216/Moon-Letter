package com.twomemory.timeline

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.PaperSurface
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography

@Composable
fun TimelineRoute(viewModel: TimelineViewModel) {
    val entries by viewModel.entries.collectAsState()
    TimelineScreen(entries)
}

@Composable
fun TimelineScreen(entries: List<TimelineEntryUi>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("我们的时光", style = TwoMemoryTypography.display)
            Text("中秋 · 仍在一起写着", style = TwoMemoryTypography.caption)
            Spacer(Modifier.height(12.dp))
        }
        items(entries, key = { it.id }) { entry ->
            TimelineCard(entry)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TimelineCard(entry: TimelineEntryUi) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(if (entry.shared) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeMuted),
            )
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(modifier = Modifier.height(78.dp).size(1.dp))
        }
        Spacer(Modifier.size(12.dp))
        PaperSurface(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.dateLabel, style = TwoMemoryTypography.caption)
                    Text(entry.timeLabel, style = TwoMemoryTypography.caption)
                }
                Text(if (entry.shared) "共同记录 · ${entry.author}" else entry.author, style = TwoMemoryTypography.caption)
                Text(entry.body, style = TwoMemoryTypography.body)
            }
        }
    }
}
