package com.twomemory.timeline

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.twomemory.designsystem.PaperTextButton as TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.model.BlockType
import com.twomemory.designsystem.MusicMemoryCard
import com.twomemory.designsystem.AudioMemoryCard
import com.twomemory.designsystem.PaperRule
import com.twomemory.designsystem.PaperSectionHeading
import com.twomemory.designsystem.PolaroidFrame
import com.twomemory.designsystem.paperTexture

@Composable
fun EntryDetailRoute(
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
    delivery: MoonLetterRecordStatus? = null,
    onRetryDelivery: () -> Unit = {},
    ownAvatar: ImageBitmap? = null,
    onAddPerspective: (() -> Unit)? = null,
    partnerAvatar: ImageBitmap? = null,
    privacyAction: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel, delivery) { viewModel.updateDelivery(delivery) }
    EntryDetailScreen(
        state,
        onBack,
        viewModel::updateDraft,
        viewModel::sendComment,
        delivery,
        onRetryDelivery,
        ownAvatar,
        onAddPerspective,
        partnerAvatar,
        privacyAction,
    )
}

@Composable
fun EntryDetailScreen(
    state: EntryDetailUi?,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    delivery: MoonLetterRecordStatus? = null,
    onRetryDelivery: () -> Unit = {},
    ownAvatar: ImageBitmap? = null,
    onAddPerspective: (() -> Unit)? = null,
    partnerAvatar: ImageBitmap? = null,
    privacyAction: @Composable () -> Unit = {},
) {
    var moreExpanded by remember(state?.header?.id) { mutableStateOf(false) }
    var viewerIndex by remember(state?.header?.id) { mutableStateOf<Int?>(null) }
    val effectiveStatus = delivery ?: state?.status ?: MoonLetterRecordStatus.SYNCED
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "返回时间线")
                }
                Text(
                    state?.header?.let { if (it.shared) "共同手帐" else "${it.dateLabel} · ${it.timeLabel}" } ?: "记录详情",
                    style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted,
                )
                Spacer(Modifier.weight(1f))
                privacyAction()
                if (effectiveStatus == MoonLetterRecordStatus.REJECTED) {
                    Box {
                        IconButton(
                            onClick = { moreExpanded = true },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "更多操作" },
                        ) {
                            Icon(TwoMemoryIcons.More, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("重新发送") },
                                onClick = {
                                    moreExpanded = false
                                    onRetryDelivery()
                                },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = { CommentField(state, effectiveStatus, onDraftChange, onSend) },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("正在打开…这台手机还没有读到这条记录", style = TwoMemoryTypography.body)
            }
            return@Scaffold
        }
        val currentState = state
        val imageBlocks = currentState.blocks.filter { it.type == BlockType.IMAGE && (it.assetId != null || it.localPath != null) }
        Box(Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background).padding(padding)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            RecordHeader(currentState.header, effectiveStatus, if (currentState.header.mine) ownAvatar else partnerAvatar,
                currentState.blocks, ownAvatar, partnerAvatar)
            PaperRule()
            if (currentState.header.littleNote) {
                com.twomemory.designsystem.StickyNotePaper(mine = currentState.header.mine) {
                    currentState.blocks.filter { it.type == BlockType.TEXT }.forEach { block ->
                        Text(block.text.orEmpty(), style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeInk)
                    }
                }
            } else currentState.blocks.forEachIndexed { index, block -> key(block.id) {
                val imageIndex = imageBlocks.indexOfFirst { it.id == block.id }
                // Keep block order, but sign a continuous contribution once.
                val previous = currentState.blocks.getOrNull(index - 1)
                val showAuthor = previous == null || if (previous.authorId != null || block.authorId != null)
                    previous.authorId != block.authorId else previous.author != block.author
                DetailBlock(block, imageIndex = imageIndex.takeIf { it >= 0 }, showAuthor = showAuthor) {
                    if (imageIndex >= 0) viewerIndex = imageIndex
                }
            } }
            if (currentState.header.shared && onAddPerspective != null &&
                effectiveStatus != MoonLetterRecordStatus.DRAFT && effectiveStatus != MoonLetterRecordStatus.LOCAL_WRITE_FAILED) {
                TextButton(onClick = onAddPerspective, modifier = Modifier.fillMaxWidth()) { Text("补写我的视角") }
            }
            Comments(currentState, canRespond = effectiveStatus != MoonLetterRecordStatus.DRAFT &&
                effectiveStatus != MoonLetterRecordStatus.LOCAL_WRITE_FAILED)
            Spacer(Modifier.height(16.dp))
        }
        }
        if (viewerIndex != null && imageBlocks.isNotEmpty()) {
            PhotoViewer(
                images = imageBlocks,
                initialIndex = viewerIndex!!.coerceIn(0, imageBlocks.lastIndex),
                onDismiss = { viewerIndex = null },
            )
        }
    }
}

@Composable
private fun RecordHeader(
    header: TimelineEntryUi,
    status: MoonLetterRecordStatus,
    ownAvatar: ImageBitmap?,
    blocks: List<EntryBlockUi>,
    writerAvatar: ImageBitmap?,
    partnerAvatar: ImageBitmap?,
) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PaperSectionHeading(header.dateLabel, subtitle = "${header.timeLabel} · 这一页的回忆")
            if (header.shared) {
                val contributors = blocks.filter { it.authorId != null }.distinctBy { it.authorId }
                Text(when (contributors.size) {
                    0 -> "这一页的执笔人信息尚未完整"
                    1 -> "已留下一个人的视角"
                    else -> "已留下两个人的视角"
                }, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    contributors.forEach { contributor ->
                        Column(Modifier.weight(1f).semantics {
                            contentDescription = if (contributor.mine) "共同创作签名：我" else "共同创作签名：对方"
                        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AuthorAvatar(contributor.mine, if (contributor.mine) writerAvatar else partnerAvatar,
                                38.dp, "${contributor.author ?: "成员"}的头像")
                            Text(contributor.author ?: "成员", style = TwoMemoryTypography.title,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (contributor.mine) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage)
                        }
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(header.mine, ownAvatar, 38.dp, "${header.author}的头像")
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(header.author, style = TwoMemoryTypography.title, color = if (header.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF6F8268))
                    Text("${header.dateLabel} · ${header.timeLabel}", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
            }
            header.title?.takeUnless { header.littleNote }?.let { Text(it, style = TwoMemoryTypography.display, fontWeight = FontWeight.Medium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (header.shared) StatusChip("共同记录")
                if (status == MoonLetterRecordStatus.DRAFT || status == MoonLetterRecordStatus.REJECTED) {
                    StatusChip(status.label)
                }
            }
            if (status == MoonLetterRecordStatus.REJECTED) {
                Text(
                    "服务器没有收下这条内容，TA 那边看不到。可在右上角更多操作中重新发送。",
                    style = TwoMemoryTypography.caption,
                    color = MaterialTheme.colorScheme.error,
                )
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
private fun DetailBlock(block: EntryBlockUi, imageIndex: Int? = null, showAuthor: Boolean = true, onImageClick: () -> Unit = {}) {
    val author = block.author
    val payload = runCatching { org.json.JSONObject(block.payload.orEmpty()) }.getOrNull()
    val accent = if (block.mine) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage
    Column(modifier = Modifier.fillMaxWidth().then(if (block.authorId != null) Modifier.drawBehind {
        drawLine(accent.copy(alpha = .4f), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 1.dp.toPx())
    }.padding(start = 18.dp, top = 6.dp, bottom = 6.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showAuthor) author?.let { Text("${it}的视角", style = TwoMemoryTypography.caption, color = accent) }
        when {
            block.type == BlockType.IMAGE && (block.assetId != null || block.localPath != null) -> {
                PolaroidFrame(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        .clickable(onClick = onImageClick)
                        .semantics { contentDescription = "查看第${(imageIndex ?: 0) + 1}张图片" },
                    photo = {
                    EntryPhoto(
                        localPath = block.localPath,
                        assetId = block.assetId,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        contentDescription = "记录里的照片",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        reading = true,
                    )
                    },
                )
            }
            block.type == BlockType.LOCATION -> {
                val city = payload?.optString("city").orEmpty()
                    .ifBlank { payload?.optString("cityName").orEmpty() }
                    .ifBlank { payload?.optString("name").orEmpty() }
                Text(if (city.isBlank()) "地点快照正在准备中" else "📍 $city", style = TwoMemoryTypography.body)
            }
            block.type == BlockType.MUSIC -> {
                val link = payload?.optString("url").orEmpty()
                    .ifBlank { payload?.optString("link").orEmpty() }
                MusicMemoryCard(title = payload?.optString("title").orEmpty(),
                    artist = payload?.optString("artist").orEmpty(), url = link, modifier = Modifier.fillMaxWidth())
            }
            block.type == BlockType.AUDIO -> AudioMemoryCard(localPath = block.localPath,
                assetId = block.assetId, modifier = Modifier.fillMaxWidth(), title = payload?.optString("title").orEmpty())
            block.type == BlockType.VIDEO -> com.twomemory.designsystem.VideoMemoryCard(
                localPath = block.localPath, assetId = block.assetId, modifier = Modifier.fillMaxWidth())
            block.text?.isNotBlank() == true -> Text(block.text, style = TwoMemoryTypography.body)
            else -> Text(blockTypeLabel(block.type), style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
    }
}

private fun blockTypeLabel(type: BlockType): String = when (type) {
    BlockType.TEXT -> "这段文字暂时为空"
    BlockType.IMAGE -> "照片正在准备中"
    BlockType.VIDEO -> "视频附件暂时无法在本机打开"
    BlockType.AUDIO -> "语音附件暂时无法在本机播放"
    BlockType.MUSIC -> "音乐分享（未附带可打开的歌曲链接）"
    BlockType.LOCATION -> "地点故事暂时没有城市信息"
}

@Composable
private fun PhotoViewer(
    images: List<EntryBlockUi>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember(initialIndex) { mutableStateOf(initialIndex) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = .96f)),
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "关闭图片浏览" },
                    ) {
                        Icon(TwoMemoryIcons.Close, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("图片 ${currentIndex + 1} / ${images.size}", style = TwoMemoryTypography.caption, color = Color.White.copy(alpha = .82f))
                    Spacer(Modifier.width(48.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = images[currentIndex]
                    com.twomemory.designsystem.ZoomablePhoto(image.id, Modifier.fillMaxSize()) {
                    EntryPhoto(
                        localPath = image.localPath,
                        assetId = image.assetId,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "放大查看第${currentIndex + 1}张图片",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        cornerRadius = 0.dp,
                        original = true,
                        useNaturalAspectRatio = false,
                    )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0,
                        modifier = Modifier.semantics { contentDescription = "上一张图片" },
                    ) { Text("上一张", color = Color.White) }
                    Text("双指缩放 · 双击缩放", style = TwoMemoryTypography.caption, color = Color.White.copy(alpha = .58f))
                    TextButton(
                        onClick = { if (currentIndex < images.lastIndex) currentIndex++ },
                        enabled = currentIndex < images.lastIndex,
                        modifier = Modifier.semantics { contentDescription = "下一张图片" },
                    ) { Text("下一张", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun Comments(state: EntryDetailUi, canRespond: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TwoMemoryColors.WarmBeigeLine))
        Text("回应", style = TwoMemoryTypography.title)
        if (state.comments.isEmpty() && canRespond) {
            Text("还没有回应，说一句给 TA 听的话。", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
        state.comments.forEach { comment ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (comment.mine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author, style = TwoMemoryTypography.caption, color = if (comment.mine) TwoMemoryColors.WarmBeigeAccent else Color(0xFF74856B))
                    Spacer(Modifier.width(8.dp))
                    Text(comment.timeLabel, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
                Text(comment.body, style = TwoMemoryTypography.body,
                    modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (comment.mine) TwoMemoryColors.WarmBeigeAccent.copy(alpha = .10f)
                            else TwoMemoryColors.WarmBeigeSage.copy(alpha = .10f))
                        .padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun CommentField(state: EntryDetailUi?, status: MoonLetterRecordStatus, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    if (state == null) return
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        state?.error?.let {
            Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
        }
        if (status == MoonLetterRecordStatus.DRAFT || status == MoonLetterRecordStatus.LOCAL_WRITE_FAILED) {
            Text("尚未发布 · 发布后可以在这一页回应", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
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
