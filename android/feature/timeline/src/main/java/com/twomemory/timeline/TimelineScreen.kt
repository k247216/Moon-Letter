package com.twomemory.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.twomemory.designsystem.PaperTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twomemory.designsystem.*
import com.twomemory.designsystem.R
import com.twomemory.model.BlockType

@Composable
fun TimelineRoute(viewModel: TimelineViewModel, coverBitmap: ImageBitmap? = null,
    onChangeCover: () -> Unit = {}, onOpen: (String) -> Unit = {}, onCreate: () -> Unit = {},
    ownAvatar: ImageBitmap? = null, onCoverVisible: (Boolean) -> Unit = {}, partnerAvatar: ImageBitmap? = null,
    onOpenDrafts: () -> Unit = {}, listState: LazyListState = rememberLazyListState(),
    onOpenBookmarks: () -> Unit = {}) {
    val entries by viewModel.entries.collectAsState()
    val initialLoading by viewModel.initialLoading.collectAsState()
    TimelineScreen(entries, coverBitmap, onChangeCover, onOpen, onCreate, ownAvatar, onCoverVisible, partnerAvatar, onOpenDrafts, listState, initialLoading, onOpenBookmarks)
}

@Composable
fun TimelineScreen(entries: List<TimelineEntryUi>, coverBitmap: ImageBitmap? = null,
    onChangeCover: () -> Unit = {}, onOpen: (String) -> Unit = {}, onCreate: () -> Unit = {},
    ownAvatar: ImageBitmap? = null, onCoverVisible: (Boolean) -> Unit = {}, partnerAvatar: ImageBitmap? = null,
    onOpenDrafts: () -> Unit = {}, listState: LazyListState = rememberLazyListState(), initialLoading: Boolean = false,
    onOpenBookmarks: () -> Unit = {}) {
    val scroll = listState
    val coverVisible by remember(scroll) { derivedStateOf { scroll.firstVisibleItemIndex == 0 } }
    val chapters = remember(entries) { entries.groupBy { it.dateLabel } }
    LaunchedEffect(coverVisible) { onCoverVisible(coverVisible) }
    LazyColumn(Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background), state = scroll) {
        item { TimelineHeader(coverBitmap, onChangeCover) }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCreate) {
                    Icon(TwoMemoryIcons.Edit, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp)); Text("记下今天")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenBookmarks) { Text("小书签") }
                TextButton(onClick = onOpenDrafts) { Text("草稿箱") }
            }
            entries.firstOrNull { it.unread }?.let { next ->
                TextButton(onClick = { onOpen(next.id) }, modifier = Modifier.padding(start = 32.dp)) {
                    Text("看看这封小笺", color = TwoMemoryColors.WarmBeigeSage)
                }
            }
        }
        if (entries.isEmpty() && initialLoading) item {
            Column(Modifier.fillMaxWidth().testTag("timeline-initial-loading")
                .padding(horizontal = 30.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("正在翻开本机的手帐…", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            }
        }
        else if (entries.isEmpty()) item { EmptyTimeline(onCreate) }
        else chapters.forEach { (day, dayEntries) ->
            item(key = "day-$day") { DayStitchHeader(day) }
            itemsIndexed(dayEntries, key = { _, it -> it.id }) { index, entry ->
                JournalEntryCard(entry, onOpen, ownAvatar, index % 2 == 1, partnerAvatar)
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun TimelineHeader(coverBitmap: ImageBitmap?, onChangeCover: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale > 1.4f
    val height = (LocalConfiguration.current.screenHeightDp * .235f)
        .coerceIn(if (largeFont) 280f else 168f, if (largeFont) 330f else 210f).dp
    val paper = MaterialTheme.colorScheme.background
    val coverInk = if (coverBitmap == null) TwoMemoryColors.WarmBeigeInk else Color.White
    Box(Modifier.fillMaxWidth().height(height)) {
        if (coverBitmap != null) Image(coverBitmap, "首页半屏背景图", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(R.drawable.moonletter_cover_puppy), "首页小狗背景图", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (coverBitmap != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFF1E2838).copy(alpha = .48f), Color.Transparent, Color.Black.copy(alpha = .36f)))))
        Text("和你一起，\n把平凡的日子\n缝成闪闪发光的线。",
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 24.dp, top = 10.dp, end = 38.dp),
            style = TwoMemoryTypography.display.copy(fontSize = 15.sp, lineHeight = 23.sp), color = coverInk)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("故事，从每一个今天开始", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TwoMemoryTypography.caption, color = coverInk.copy(alpha = .86f))
        Row(Modifier
            .clip(RoundedCornerShape(22.dp)).clickable(onClick = onChangeCover)
            .background(if (coverBitmap == null) TwoMemoryColors.WarmBeigeSurface.copy(alpha = .75f) else Color.Black.copy(alpha = .20f)).heightIn(min = 48.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(TwoMemoryIcons.Camera, "更换背景图", Modifier.size(18.dp), tint = coverInk)
            Spacer(Modifier.width(6.dp))
            Text("换一张", style = TwoMemoryTypography.caption, color = coverInk)
        }
        }
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(9.dp)) {
            val edge = Path().apply {
                moveTo(0f, size.height * .25f)
                cubicTo(size.width * .24f, size.height, size.width * .54f, 0f, size.width, size.height * .3f)
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(edge, paper)
        }
    }
}

internal fun Modifier.journalRail() = drawWithCache {
    val x = 24.dp.toPx()
    val sway = 5.dp.toPx()
    val loops = kotlin.math.ceil(size.height / 96.dp.toPx()).toInt().coerceAtLeast(1)
    val step = size.height / loops
    // Each item returns to x with a vertical tangent so adjacent rail sections join.
    val thread = Path().apply {
        moveTo(x, 0f)
        repeat(loops) { index ->
            val top = index * step
            cubicTo(x, top + step * .12f, x + sway, top + step * .22f, x + sway, top + step * .33f)
            cubicTo(x + sway, top + step * .45f, x - sway, top + step * .55f, x - sway, top + step * .67f)
            cubicTo(x - sway, top + step * .78f, x, top + step * .88f, x, top + step)
        }
    }
    val ink = TwoMemoryColors.WarmBeigeAccent.copy(alpha = .66f)
    val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx())))
    onDrawBehind {
        drawPath(thread, ink.copy(alpha = .15f), style = Stroke(width = .7.dp.toPx(), cap = StrokeCap.Round))
        drawPath(thread, ink, style = stroke)
    }
}

@Composable
private fun DayStitchHeader(day: String) {
    Column(Modifier.fillMaxWidth().journalRail().padding(start = 42.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(day, Modifier.weight(1f), style = TwoMemoryTypography.display.copy(fontSize = 21.sp), maxLines = 2)
        }
        PaperUnderline(Modifier.width(156.dp), color = TwoMemoryColors.WarmBeigeAccent.copy(alpha = .65f), thickness = 2.dp)
    }
}

@Composable
fun JournalEntryCard(entry: TimelineEntryUi, onOpen: (String) -> Unit,
    ownAvatar: ImageBitmap? = null, alternate: Boolean = false, partnerAvatar: ImageBitmap? = null) {
    if (entry.littleNote) {
        JournalNoteCard(entry, onOpen)
        return
    }
    val accent = if (entry.mine) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage
    Box(Modifier.fillMaxWidth().journalRail()) {
        Box(Modifier.padding(start = 19.dp, top = 10.dp).size(10.dp).clip(CircleShape).background(accent))
        Column(Modifier.fillMaxWidth().padding(start = if (alternate) 48.dp else 42.dp, end = if (alternate) 16.dp else 22.dp, bottom = 18.dp)
            .testTag(if (alternate) "timeline-row-right" else "timeline-row-left")
            .background(Color.White.copy(alpha = .44f), RoundedCornerShape(8.dp))
            .semantics { contentDescription = "打开记录" }.clickable { onOpen(entry.id) },
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(entry.mine, if (entry.mine) ownAvatar else partnerAvatar, 28.dp, "${entry.author}的头像")
                Spacer(Modifier.width(8.dp))
                Text(entry.author, Modifier.weight(1f, fill = false), style = TwoMemoryTypography.title.copy(fontSize = 15.sp),
                    color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(9.dp))
                Text(entry.timeLabel, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                if (entry.privateRecord) {
                    Spacer(Modifier.width(8.dp))
                    Text("仅自己", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                }
                if (entry.unread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadLetterIndicator(accent)
                }
                if (entry.shared) {
                    Spacer(Modifier.weight(1f))
                    Text("共同", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeSage)
                }
            }
            if (entry.photo != null && LocalDensity.current.fontScale <= 1.4f && LocalConfiguration.current.screenWidthDp >= 360) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    val photo: @Composable () -> Unit = {
                        PolaroidFrame(Modifier.width(108.dp).height(124.dp), rotation = if (alternate) -1.5f else 1.5f) {
                            EntryPhoto(entry.photo.localPath, entry.photo.assetId, Modifier.fillMaxSize(), "记录里的照片", cornerRadius = 1.dp)
                        }
                    }
                    if (alternate) photo()
                    Column(Modifier.weight(1f).background(Color.White.copy(alpha = .44f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { JournalText(entry) }
                    if (!alternate) photo()
                }
            } else {
                Column(Modifier.fillMaxWidth().background(accent.copy(alpha = .035f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    JournalText(entry)
                }
                entry.photo?.let { photo ->
                    PolaroidFrame(Modifier.fillMaxWidth().height(150.dp), rotation = 0f) {
                        EntryPhoto(photo.localPath, photo.assetId, Modifier.fillMaxSize(), "记录里的照片", cornerRadius = 1.dp)
                    }
                }
            }
            entry.attachments.forEach { TimelineAttachmentCard(it) }
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TwoMemoryIcons.Comment, "打开记录回应", Modifier.size(16.dp), tint = TwoMemoryColors.WarmBeigeMuted)
                Spacer(Modifier.width(6.dp))
                Text("回应这一刻", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
            }
            Canvas(Modifier.fillMaxWidth(.68f).height(10.dp)) {
                val seam = Path().apply {
                    moveTo(1.dp.toPx(), size.height * .45f)
                    cubicTo(size.width * .24f, size.height * .05f,
                        size.width * .52f, size.height * .92f, size.width - 1.dp.toPx(), size.height * .4f)
                }
                drawPath(seam, accent.copy(alpha = .52f),
                    style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round))
                val pencilEcho = Path().apply {
                    moveTo(size.width * .12f, size.height * .7f)
                    cubicTo(size.width * .3f, size.height * .4f,
                        size.width * .48f, size.height * .95f, size.width * .7f, size.height * .7f)
                }
                drawPath(pencilEcho, accent.copy(alpha = .18f),
                    style = Stroke(width = .8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
internal fun UnreadLetterIndicator(accent: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 18.dp)
        .background(accent.copy(alpha = .065f), RoundedCornerShape(6.dp))
        .semantics { contentDescription = "这封小笺还没看过" }) {
        val left = 4.dp.toPx()
        val right = size.width - left
        val top = 5.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val envelope = Path().apply {
            moveTo(left, top)
            lineTo(right, top + .4.dp.toPx())
            lineTo(right - .3.dp.toPx(), bottom)
            lineTo(left + .3.dp.toPx(), bottom - .3.dp.toPx())
            close()
            moveTo(left, top)
            lineTo(size.width * .5f, size.height * .57f)
            lineTo(right, top + .4.dp.toPx())
        }
        drawPath(envelope, accent.copy(alpha = .55f),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun JournalText(entry: TimelineEntryUi) {
    entry.title?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = TwoMemoryTypography.title.copy(fontSize = 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (entry.body.isNotBlank()) Text(entry.body, style = TwoMemoryTypography.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
        maxLines = if (entry.photo == null) 5 else 4, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TimelineAttachmentCard(attachment: TimelineAttachmentUi) {
    if (attachment.type == BlockType.MUSIC) {
        MusicMemoryCard(attachment.title.orEmpty(), attachment.detail.orEmpty(), attachment.url.orEmpty())
        return
    }
    if (attachment.type == BlockType.AUDIO) {
        AudioMemoryCard(attachment.localPath, attachment.assetId, title = attachment.title.orEmpty().takeUnless { it == "语音片段" }.orEmpty())
        return
    }
    val (label, icon, accent) = when (attachment.type) {
        BlockType.LOCATION -> Triple("城市故事", TwoMemoryIcons.Location, TwoMemoryColors.WarmBeigeSage)
        BlockType.MUSIC -> Triple("一起听过", TwoMemoryIcons.Music, TwoMemoryColors.WarmBeigeAccent)
        BlockType.VIDEO -> Triple("视频片段", TwoMemoryIcons.Video, TwoMemoryColors.WarmBeigeAccent)
        BlockType.AUDIO -> Triple("留下的声音", TwoMemoryIcons.Voice, TwoMemoryColors.WarmBeigeSage)
        else -> return
    }
    val music = attachment.type == BlockType.MUSIC
    Surface(Modifier.fillMaxWidth().semantics { contentDescription = label }, shape = RoundedCornerShape(if (music) 10.dp else 20.dp),
        color = accent.copy(alpha = .07f)) {
        Row(Modifier.padding(if (music) 12.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (music) 48.dp else 30.dp).clip(if (music) CircleShape else RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(if (music) 24.dp else 19.dp), tint = accent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(attachment.title.orEmpty().ifBlank { label }, style = TwoMemoryTypography.body, maxLines = 2)
                Text(attachment.detail?.takeIf { it.isNotBlank() } ?: label, style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun EmptyTimeline(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("时光还留着空白", style = TwoMemoryTypography.title)
        Text("一句心情、一张照片，\n都是你们故事的第一笔。", style = TwoMemoryTypography.body,
            textAlign = TextAlign.Center, color = TwoMemoryColors.WarmBeigeMuted)
        Text("写下第一句", Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onCreate)
            .background(TwoMemoryColors.WarmBeigeAccent).padding(horizontal = 24.dp, vertical = 14.dp),
            style = TwoMemoryTypography.button, color = Color.White)
    }
}

@Composable
internal fun AuthorAvatar(mine: Boolean, ownAvatar: ImageBitmap?, size: Dp, contentDescription: String) {
    val modifier = Modifier.size(size).clip(CircleShape)
    if (ownAvatar != null) Image(ownAvatar, contentDescription, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(if (mine) TwoMemoryColors.WarmBeigeAccentSoft else TwoMemoryColors.WarmBeigeSage.copy(alpha = .14f))
        .semantics { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        Text(contentDescription.removeSuffix("的头像").take(1).ifBlank { "·" },
            style = TwoMemoryTypography.body, color = if (mine) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage)
    }
}
