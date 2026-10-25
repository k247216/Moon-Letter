package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.twomemory.designsystem.PaperTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twomemory.designsystem.CitySelectionMap
import androidx.compose.runtime.saveable.rememberSaveable
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.PaperRule
import com.twomemory.designsystem.PaperSectionHeading
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.paperTexture
import com.twomemory.model.EntryMode
import com.twomemory.model.CityCatalog
import com.twomemory.model.CityOption
import java.time.Instant
import com.twomemory.model.BlockType

@Composable
fun PersonalEditorScreen(
    state: EditorUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onPublish: () -> Unit,
    onClose: () -> Unit = {},
    mode: EntryMode = EntryMode.PERSONAL,
    onModeChange: (EntryMode) -> Unit = {},
    photoActions: EditorPhotoActions? = null,
    attachmentActions: EditorAttachmentActions? = null,
    ownName: String = "",
    /** Real queue state of the record just published; null until something is published. */
    delivery: MoonLetterRecordStatus? = null,
    onRetryDelivery: () -> Unit = {},
    /** When the story happened; the writer's answer, not the clock's. */
    onOccurrenceChange: (Instant) -> Unit = {},
    partnerName: String? = null,
    partnerBody: String? = null,
    onSaveDraft: (() -> Unit)? = null,
    onImportAudio: (() -> Unit)? = null,
    /** Existing records keep their shared title while a new perspective is written. */
    titleReadOnly: Boolean = state.appendingPerspective,
    ownPublishedBody: String? = state.ownPublishedBody,
    ownAvatar: ImageBitmap? = null,
    partnerAvatar: ImageBitmap? = null,
    onVisibilityChange: (com.twomemory.model.EntryVisibility) -> Unit = {},
    currentUserId: java.util.UUID? = null,
    contributorIds: Set<java.util.UUID> = emptySet(),
    referenceBlocks: List<EditorReferenceBlock>? = null,
) {
    var chooseLocation by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf<java.util.UUID?>(null) }
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(TwoMemoryColors.WarmBeigeNav)
                    .navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                state.error?.let {
                    Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = TwoMemoryTypography.caption,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                PaperRule(color = TwoMemoryColors.WarmBeigeLine.copy(alpha = .75f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        state.attachments.filter { it.type == BlockType.LOCATION }.forEach { location ->
                            val city = runCatching { org.json.JSONObject(location.payload).optString("city") }.getOrDefault("")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { editingLocation = location.id; chooseLocation = true }, enabled = attachmentActions != null) {
                                    Icon(TwoMemoryIcons.Location, null, Modifier.size(18.dp))
                                    Text(city.ifBlank { "地点" }, style = TwoMemoryTypography.caption)
                                }
                                IconButton(onClick = { attachmentActions?.remove?.invoke(location.id) },
                                    enabled = attachmentActions != null,
                                    modifier = Modifier.size(48.dp).semantics { contentDescription = "移除地点$city" }) {
                                    Icon(TwoMemoryIcons.Close, null, Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    if (onSaveDraft != null) TextButton(onClick = onSaveDraft,
                        enabled = state.hasContent && !state.saving && !state.saved) { Text("存为草稿") }
                }
                Spacer(Modifier.size(6.dp))
                EditorAttachmentToolbar(
                    onImageClick = photoActions?.pick,
                    onVideoClick = attachmentActions?.pickVideo,
                    onAudioClick = attachmentActions?.pickAudio,
                    onMusicAdded = attachmentActions?.addMusic,
                    onCitySelected = attachmentActions?.let { actions -> { city ->
                        editingLocation?.let(actions.remove)
                        actions.addCity(city)
                        editingLocation = null
                    } },
                    onSaveDraft = onSaveDraft?.takeIf { state.hasContent && !state.saving && !state.saved },
                    onImportAudio = onImportAudio,
                    cityPickerRequested = chooseLocation,
                    onCityPickerDismiss = { chooseLocation = false; editingLocation = null },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background).padding(padding)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            EditorTopBar(state, onClose, onPublish, onOccurrenceChange)
            if (!state.appendingPerspective && !titleReadOnly) RecordModeSwitch(mode, onModeChange)
            val author = ownName.ifBlank { "我" }
            if (mode == EntryMode.COLLABORATIVE) {
                val actualContributors = contributorIds + referenceBlocks.orEmpty().mapNotNull { it.authorId }
                SharedWritingInvitation(author, partnerName, actualContributors, currentUserId,
                    state.appendingPerspective, ownAvatar, partnerAvatar)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorMark(author, 46.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(author, style = TwoMemoryTypography.title, color = MaterialTheme.colorScheme.primary)
                    Text(if (mode == EntryMode.COLLABORATIVE) "共同记录 · 写下你的视角" else "先写下来，准备好再分享", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                }
            }
            if (mode == EntryMode.PERSONAL && !state.appendingPerspective) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.twomemory.model.EntryVisibility.entries.forEach { choice ->
                        androidx.compose.material3.FilterChip(
                            selected = state.visibility == choice,
                            onClick = { onVisibilityChange(choice) }, enabled = !state.saving,
                            label = { Text(if (choice == com.twomemory.model.EntryVisibility.PRIVATE) "先收在心里" else "给我们看") })
                    }
                }
                Text(if (state.visibility == com.twomemory.model.EntryVisibility.PRIVATE)
                    "云端收好，只对自己可见。准备好后，可以再分享。" else "保存后，你们两个人都能看见。",
                    style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            }
            if (titleReadOnly || state.appendingPerspective) {
                Text(state.title, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "记录标题" },
                    style = TwoMemoryTypography.display, color = MaterialTheme.colorScheme.onSurface)
            } else BasicTextField(
                value = state.title,
                onValueChange = onTitleChange,
                readOnly = titleReadOnly || state.appendingPerspective,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "记录标题" },
                textStyle = TwoMemoryTypography.display.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (state.title.isBlank()) Text("给这一刻起个名字", style = TwoMemoryTypography.display,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
                        inner()
                    }
                },
            )
            if (mode == EntryMode.COLLABORATIVE) {
                if (referenceBlocks != null) {
                    EditorReferenceBlocks(referenceBlocks, currentUserId, author, partnerName)
                } else {
                if (!partnerBody.isNullOrBlank()) {
                    EditorReadOnlyContext(
                        label = "${partnerName?.takeIf { it.isNotBlank() } ?: "成员"}的视角 · 仅 TA 可修改",
                        body = partnerBody, accent = TwoMemoryColors.WarmBeigeSage,
                    )
                }
                if (!ownPublishedBody.isNullOrBlank()) {
                    EditorReadOnlyContext("我已写下的内容 · 只读", ownPublishedBody, TwoMemoryColors.WarmBeigeAccent)
                }
                }
                Text(if (state.appendingPerspective) "补写我的视角" else "写下我的视角", style = TwoMemoryTypography.title, color = MaterialTheme.colorScheme.primary)
            }
            BasicTextField(
                value = state.body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription =
                    if (mode == EntryMode.COLLABORATIVE) "共同记录补写正文" else "个人记录正文" }
                    .then(if (mode == EntryMode.COLLABORATIVE) Modifier.drawBehind {
                        drawLine(TwoMemoryColors.WarmBeigeAccent.copy(alpha = .5f),
                            androidx.compose.ui.geometry.Offset(1.dp.toPx(), 0f),
                            androidx.compose.ui.geometry.Offset(1.dp.toPx(), size.height), 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    }.padding(start = 18.dp) else Modifier),
                textStyle = TwoMemoryTypography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = if (mode == EntryMode.COLLABORATIVE) 3 else 6,
                decorationBox = { inner ->
                    Box {
                        if (state.body.isBlank()) Text(
                            if (mode == EntryMode.COLLABORATIVE) "同一个片刻，你记住了什么？\n把你眼中的故事，留在这一页……" else "今天的心情、做过的事，慢慢写下来……",
                            style = TwoMemoryTypography.body,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                        )
                        inner()
                    }
                },
            )
            EditorPhotoBlocks(
                photos = state.photos,
                photoError = state.photoError,
                onRemove = { photoActions?.remove?.invoke(it) },
            )
            EditorContentBlocks(
                attachments = state.attachments.filterNot { it.type == BlockType.LOCATION },
                onRemove = { attachmentActions?.remove?.invoke(it) },
                onRenameAudio = attachmentActions?.renameAudio,
            )
            Spacer(Modifier.size(24.dp))
        }
        }
    }
}

@Composable
private fun SharedWritingInvitation(own: String, partner: String?, contributors: Set<java.util.UUID>,
    currentUserId: java.util.UUID?, appending: Boolean,
    ownAvatar: ImageBitmap?, partnerAvatar: ImageBitmap?) {
    val partnerHasWritten = currentUserId != null && contributors.any { it != currentUserId }
    androidx.compose.material3.Surface(shape = RoundedCornerShape(22.dp),
        color = TwoMemoryColors.WarmBeigeAccentSoft.copy(alpha = .65f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("一段时光，两种心情", style = TwoMemoryTypography.title)
            Text(when (contributors.size) {
                0 -> "等第一笔故事落在这一页"
                1 -> "已留下一个人的视角"
                else -> "已留下两个人的视角"
            }, style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(Modifier.weight(1f).semantics { contentDescription = "共同创作签名：我" },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ownAvatar != null) Image(ownAvatar, "${own}的头像", Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else AuthorMark(own, 38.dp, TwoMemoryColors.WarmBeigeAccent)
                    Text(own, style = TwoMemoryTypography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (currentUserId != null && currentUserId in contributors) "我的视角已在这里" else if (appending) "继续写下" else "执笔此刻",
                        style = TwoMemoryTypography.metadata, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(TwoMemoryIcons.Like, "一起留下这一页", tint = TwoMemoryColors.WarmBeigeAccent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).semantics { contentDescription = "共同创作签名：对方" },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (partnerAvatar != null) Image(partnerAvatar, "${partner}的头像", Modifier.size(38.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else AuthorMark(partner?.takeIf { it.isNotBlank() } ?: "TA", 38.dp, TwoMemoryColors.WarmBeigeSage)
                    Text(partner?.takeIf { it.isNotBlank() } ?: "另一位执笔人", style = TwoMemoryTypography.caption,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (partnerHasWritten) "已留下视角" else "留一处给 TA", style = TwoMemoryTypography.metadata)
                }
            }
            PaperRule()
            Text(if (contributors.size >= 2) "两个人的记忆，按原来的顺序留在同一页。" else if (partnerHasWritten)
                "TA 的记忆已在这里，你也可以添上自己的视角。" else "先留下你眼中的故事，再邀请 TA 在同一页添上另一半。",
                style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
    }
}

@Composable
internal fun EditorTopBar(
    state: EditorUiState,
    onClose: () -> Unit,
    onPublish: () -> Unit,
    onOccurrenceChange: (Instant) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(TwoMemoryIcons.Close, contentDescription = "关闭编辑器")
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Icon(TwoMemoryIcons.Time, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(7.dp))
            OccurrenceTime(
                occurrenceTime = state.occurrenceTime,
                timezone = state.timezone,
                editable = !state.saved && !state.saving && !state.appendingPerspective,
                onChange = onOccurrenceChange,
            )
        }
        val actionEnabled = state.saved || (!state.saving && state.hasContent)
        TextButton(
            onClick = if (state.saved) onClose else onPublish,
            enabled = actionEnabled,
            modifier = Modifier.width(64.dp),
        ) {
            Text(
                when {
                    state.saving -> "保存中"
                    state.saved -> "返回"
                    else -> "完成"
                },
                color = if (actionEnabled) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeMuted,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun RecordModeSwitch(mode: EntryMode, onModeChange: (EntryMode) -> Unit) {
            Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(TwoMemoryColors.WarmBeigeLine.copy(alpha = 0.54f)).padding(1.dp),
    ) {
        listOf(EntryMode.PERSONAL to "我的记录", EntryMode.COLLABORATIVE to "共同记录").forEach { (item, label) ->
            val selected = mode == item
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(27.dp)).clickable { onModeChange(item) }
                    .background(if (selected) TwoMemoryColors.WarmBeigeAccentSoft else Color.Transparent)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = TwoMemoryTypography.body,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun EditorAttachmentToolbar(
    onImageClick: (() -> Unit)? = null,
    onVideoClick: (() -> Unit)? = null,
    onAudioClick: (() -> Unit)? = null,
    onMusicAdded: ((title: String, artist: String, url: String) -> Unit)? = null,
    onCitySelected: ((CityOption) -> Unit)? = null,
    onSaveDraft: (() -> Unit)? = null,
    onImportAudio: (() -> Unit)? = null,
    cityPickerRequested: Boolean = false,
    onCityPickerDismiss: () -> Unit = {},
) {
    var musicDialog by rememberSaveable { mutableStateOf(false) }
    var cityDialog by remember { mutableStateOf(false) }
    var moreDialog by remember { mutableStateOf(false) }
    val actions = listOf(
        Triple("图片", TwoMemoryIcons.Image, onImageClick),
        Triple("视频", TwoMemoryIcons.Video, onVideoClick),
        Triple("语音", TwoMemoryIcons.Voice, onAudioClick),
        Triple("音乐", TwoMemoryIcons.Music, { musicDialog = true }),
        Triple("城市", TwoMemoryIcons.Location, { cityDialog = true }),
        Triple("更多", TwoMemoryIcons.More, { moreDialog = true }),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (label, icon, action) ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        when (label) {
                            "音乐" -> musicDialog = true
                            "城市" -> cityDialog = true
                            "更多" -> moreDialog = true
                            else -> action?.invoke()
                        }
                    },
                    enabled = when (label) {
                        "图片" -> onImageClick != null
                        "视频" -> onVideoClick != null
                        "语音" -> onAudioClick != null
                        "音乐" -> onMusicAdded != null
                        "城市" -> onCitySelected != null
                        "更多" -> onSaveDraft != null || onImportAudio != null
                        else -> false
                    },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "添加$label" },
                ) { Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = TwoMemoryColors.WarmBeigeInk.copy(alpha = .75f)) }
                Text(label, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
            }
        }
    }
    if (musicDialog) {
        MusicAttachmentDialog(
            onDismissRequest = { musicDialog = false },
            onAdd = { title, artist, url ->
                onMusicAdded?.invoke(title, artist, url)
                musicDialog = false
            },
        )
    }
    if (moreDialog) {
        AlertDialog(onDismissRequest = { moreDialog = false }, title = { Text("更多") },
            text = { Column {
                onSaveDraft?.let { save -> TextButton(onClick = { moreDialog = false; save() }) { Text("存为草稿") } }
                onImportAudio?.let { importAudioAction -> TextButton(onClick = { moreDialog = false; importAudioAction() }) { Text("导入音频文件") } }
            } }, confirmButton = { TextButton(onClick = { moreDialog = false }) { Text("关闭") } })
    }
    if (cityDialog || cityPickerRequested) {
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        Dialog(onDismissRequest = { cityDialog = false; onCityPickerDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(TwoMemoryColors.WarmBeigeBackground).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cityDialog = false; onCityPickerDismiss() }) { Icon(TwoMemoryIcons.Close, "取消城市选择") }
                    Text("在地图上选择城市", style = TwoMemoryTypography.title)
                }
                Text("拖动和缩放地图，点击城市图钉。不会获取你的实时位置。",
                    style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                CitySelectionMap(selectedCity = selected, onCitySelected = { selected = it.name },
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)))
                Text(selected?.let { "这一页留在 · $it" } ?: "点选一座城市", style = TwoMemoryTypography.body)
                TextButton(onClick = {
                    CityCatalog.majorCities.firstOrNull { it.name == selected }?.let { onCitySelected?.invoke(it) }
                    cityDialog = false
                    onCityPickerDismiss()
                }, enabled = selected != null, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("加入这一页") }
            }
        }
    }
}

@Composable
internal fun AuthorMark(name: String, size: Dp, accent: Color) {
    Box(Modifier.size(size).clip(CircleShape).background(accent.copy(alpha = .14f)),
        contentAlignment = Alignment.Center) {
        Text(name.take(1), style = TwoMemoryTypography.title, color = accent)
    }
}
