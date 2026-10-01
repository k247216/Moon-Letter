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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

@Composable
fun EntryDetailRoute(
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    EntryDetailScreen(
        state = state,
        onBack = onBack,
        onDraftChange = viewModel::updateDraft,
        onSend = viewModel::sendComment,
    )
}

@Composable
fun EntryDetailScreen(
    state: EntryDetailUi?,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "返回时间线")
                }
                Text(
                    state?.header?.let { "${it.dateLabel} · ${it.timeLabel}" } ?: "记录",
                    style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted,
                )
            }
        },
        bottomBar = { CommentField(state, onDraftChange, onSend) },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("这条记录还不在这台手机上", style = TwoMemoryTypography.body)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 26.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RecordHeader(state.header)
            state.blocks.forEach { block ->
                if (block.assetId != null || block.localPath != null) {
                    EntryPhoto(localPath = block.localPath, assetId = block.assetId)
                } else {
                    block.text?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = TwoMemoryTypography.body)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Comments(state)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun RecordHeader(header: TimelineEntryUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(
                    if (header.mine) TwoMemoryColors.WarmBeigeAccent.copy(alpha = 0.18f)
                    else Color(0xFF8A9A7B).copy(alpha = 0.18f),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    header.author.take(1),
                    style = TwoMemoryTypography.caption,
                    color = if (header.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF8A9A7B),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(header.author, style = TwoMemoryTypography.caption)
            if (header.shared) {
                Spacer(Modifier.width(9.dp))
                Text(
                    "共同记录",
                    style = TwoMemoryTypography.caption,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (header.unsent) {
                Spacer(Modifier.width(9.dp))
                Text("未寄出", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            }
        }
        header.title?.let {
            Text(it, style = TwoMemoryTypography.title, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Comments(state: EntryDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TwoMemoryColors.WarmBeigeLine))
        if (state.comments.isEmpty()) {
            Text(
                "还没有回应，说一句给 TA 听的话。",
                style = TwoMemoryTypography.caption,
                color = TwoMemoryColors.WarmBeigeMuted,
            )
        }
        state.comments.forEach { comment ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.author,
                        style = TwoMemoryTypography.caption,
                        color = if (comment.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF74856B),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        comment.timeLabel,
                        style = TwoMemoryTypography.caption,
                        color = TwoMemoryColors.WarmBeigeMuted,
                    )
                }
                Text(comment.body, style = TwoMemoryTypography.body)
            }
        }
    }
}

@Composable
private fun CommentField(
    state: EntryDetailUi?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        state?.error?.let {
            Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state?.header?.unsent == true) {
                Text(
                    "这条记录还没寄出。寄出之后，TA 的回应才会落到这一页。",
                    style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted,
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = state?.draft.orEmpty(),
                        onValueChange = onDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TwoMemoryTypography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (state?.draft.isNullOrBlank()) {
                                Text(
                                    "写一句回应……",
                                    style = TwoMemoryTypography.body,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                                )
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = onSend,
                    enabled = state != null && !state.sending && state.draft.isNotBlank(),
                ) {
                    Text(if (state?.sending == true) "发送中" else "发送")
                }
            }
        }
    }
}
