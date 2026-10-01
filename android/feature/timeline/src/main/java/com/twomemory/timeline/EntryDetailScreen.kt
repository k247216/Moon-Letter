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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.R
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.model.BlockType

@Composable
fun EntryDetailRoute(viewModel: EntryDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    EntryDetailScreen(state, onBack, viewModel::updateDraft, viewModel::sendComment)
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
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "返回时间线")
                }
                Text(
                    state?.header?.let { "${it.dateLabel} · ${it.timeLabel}" } ?: "记录详情",
                    style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted,
                )
            }
        },
        bottomBar = { CommentField(state, onDraftChange, onSend) },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("正在等待同步，这条记录还不在这台手机上", style = TwoMemoryTypography.body)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            RecordHeader(state.header, state.status)
            state.blocks.forEach { block -> DetailBlock(block) }
            Comments(state)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun RecordHeader(header: TimelineEntryUi, status: MoonLetterRecordStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, TwoMemoryColors.WarmBeigeLine),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = painterResource(if (header.mine) R.drawable.moonletter_avatar_xiaoman else R.drawable.moonletter_avatar_ayu),
                    contentDescription = "${header.author}的头像",
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(header.author, style = TwoMemoryTypography.title, color = if (header.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF6F8268))
                    Text("${header.dateLabel} · ${header.timeLabel}", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
                if (header.shared) StatusChip("共同记录")
                Spacer(Modifier.width(6.dp))
                StatusChip(status.label)
            }
            header.title?.let { Text(it, style = TwoMemoryTypography.title, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Text(
        text,
        style = TwoMemoryTypography.caption,
        color = TwoMemoryColors.WarmBeigeAccent,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TwoMemoryColors.WarmBeigeAccent.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun DetailBlock(block: EntryBlockUi) {
    val author = block.author
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        author?.let { Text("$it的视角", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted) }
        when {
            block.type == BlockType.IMAGE && (block.assetId != null || block.localPath != null) -> {
                EntryPhoto(localPath = block.localPath, assetId = block.assetId, modifier = Modifier.fillMaxWidth().height(220.dp), contentDescription = "记录里的照片")
            }
            block.text?.isNotBlank() == true -> Text(block.text, style = TwoMemoryTypography.body)
            else -> Text(blockTypeLabel(block.type), style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
    }
}

private fun blockTypeLabel(type: BlockType): String = when (type) {
    BlockType.TEXT -> "这段文字暂时为空"
    BlockType.IMAGE -> "照片正在准备中"
    BlockType.VIDEO -> "视频将在媒体功能接通后播放"
    BlockType.AUDIO -> "语音将在媒体功能接通后播放"
    BlockType.MUSIC -> "音乐分享将在媒体功能接通后显示"
    BlockType.LOCATION -> "地点快照将在地图功能接通后显示"
}

@Composable
private fun Comments(state: EntryDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TwoMemoryColors.WarmBeigeLine))
        Text("回应", style = TwoMemoryTypography.title)
        if (state.comments.isEmpty()) {
            Text("还没有回应，说一句给 TA 听的话。", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
        state.comments.forEach { comment ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author, style = TwoMemoryTypography.caption, color = if (comment.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF74856B))
                    Spacer(Modifier.width(8.dp))
                    Text(comment.timeLabel, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
                Text(comment.body, style = TwoMemoryTypography.body)
            }
        }
    }
}

@Composable
private fun CommentField(state: EntryDetailUi?, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        state?.error?.let {
            Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
        }
        if (state?.status == MoonLetterRecordStatus.DRAFT) {
            Text("这条记录已保存在本机，等待同步后才能回应。", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = state?.draft.orEmpty(),
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TwoMemoryTypography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (state?.draft.isNullOrBlank()) Text("写一句回应……", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onSend, enabled = state != null && !state.sending && state.draft.isNotBlank()) {
                Text(if (state?.sending == true) "发送中" else "发送")
            }
        }
    }
}
