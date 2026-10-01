package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.MoonLetterRecordStatus
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.paperTexture
import com.twomemory.model.EntryMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val occurrenceFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 · HH:mm", Locale.CHINA)

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
    ownName: String = "",
) {
    Scaffold(
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
                EditorAttachmentToolbar(onImageClick = photoActions?.pick)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EditorTopBar(state, onClose, onPublish)
            RecordModeSwitch(mode, onModeChange)
            EditorStatus(state.recordStatus)
            val author = ownName.ifBlank { "我" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorMark(author, 54.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(author, style = TwoMemoryTypography.title, color = MaterialTheme.colorScheme.primary)
            }
            BasicTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TwoMemoryTypography.title.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (state.title.isBlank()) Text("给这一刻起个名字", style = TwoMemoryTypography.title,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
                        inner()
                    }
                },
            )
            BasicTextField(
                value = state.body,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "个人记录正文" },
                textStyle = TwoMemoryTypography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 12,
                decorationBox = { inner ->
                    Box {
                        if (state.body.isBlank()) Text(
                            "今天的心情、做过的事，慢慢写下来……",
                            style = TwoMemoryTypography.body,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                        )
                        inner()
                    }
                },
            )
            EditorPhotoStrip(
                photos = state.photos,
                photoError = state.photoError,
                onRemove = { photoActions?.remove?.invoke(it) },
            )
            Spacer(Modifier.size(100.dp))
        }
    }
}

@Composable
private fun EditorStatus(status: MoonLetterRecordStatus) {
    Text(
        status.label,
        style = TwoMemoryTypography.caption,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun EditorTopBar(state: EditorUiState, onClose: () -> Unit, onPublish: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(TwoMemoryIcons.Close, contentDescription = "关闭编辑器")
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Icon(TwoMemoryIcons.Time, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                state.occurrenceTime.atZone(ZoneId.of(state.timezone)).format(occurrenceFormatter),
                style = TwoMemoryTypography.body,
            )
        }
        val actionEnabled = state.saved || (!state.saving && state.hasContent)
        TextButton(
            onClick = if (state.saved) onClose else onPublish,
            enabled = actionEnabled,
            modifier = Modifier.clip(RoundedCornerShape(18.dp))
                .background(if (actionEnabled) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeLine)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                when {
                    state.saving -> "保存中"
                    state.saved -> "返回时间轴"
                    else -> "完成"
                },
                color = if (actionEnabled) Color.White else TwoMemoryColors.WarmBeigeMuted,
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

private enum class EditorAttachmentKind(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val explanation: String,
) {
    VIDEO(
        "视频",
        TwoMemoryIcons.Video,
        "视频卡片和播放控制已预留；等媒体上传接口接通后，选择的视频会先保存到本机，再进入待同步状态。当前不会生成空卡片或假装已上传。",
    ),
    VOICE(
        "语音",
        TwoMemoryIcons.Voice,
        "语音记录需要录音与媒体上传器。接口接通前，这里只展示接入边界，不会偷偷申请麦克风权限。",
    ),
    MUSIC(
        "音乐",
        TwoMemoryIcons.Music,
        "网易云链接可以通过系统分享直接进入个人草稿；编辑器内的音乐卡片、标题和封面等待 MUSIC block 接口接通后再写入。",
    ),
    LOCATION(
        "城市",
        TwoMemoryIcons.Location,
        "这里只做一次城市位置快照，不做实时定位或轨迹。位置接口接通后会在保存前让你确认城市名称。",
    ),
    MORE(
        "更多",
        TwoMemoryIcons.More,
        "时间胶囊、导出等不属于正文附件的工具会从“我们”页进入；这里保留入口语义，不新增重复的隐藏页面。",
    ),
}

@Composable
fun EditorAttachmentToolbar(onImageClick: (() -> Unit)? = null) {
    var explanation by remember { mutableStateOf<EditorAttachmentKind?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            "图片" to TwoMemoryIcons.Image,
            "视频" to TwoMemoryIcons.Video,
            "语音" to TwoMemoryIcons.Voice,
            "音乐" to TwoMemoryIcons.Music,
            "城市" to TwoMemoryIcons.Location,
            "更多" to TwoMemoryIcons.More,
        ).forEach { (label, icon) ->
            val images = label == "图片"
            val kind = EditorAttachmentKind.values().firstOrNull { it.label == label }
            IconButton(
                onClick = {
                    if (images) onImageClick?.invoke() else kind?.let { explanation = it }
                },
                enabled = images.not() || onImageClick != null,
                modifier = Modifier.size(42.dp).semantics {
                    contentDescription = if (images) "添加照片" else "$label（查看说明）"
                },
            ) {
                Icon(icon, contentDescription = label)
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "自动保存草稿",
            style = TwoMemoryTypography.caption,
            color = TwoMemoryColors.WarmBeigeMuted,
            maxLines = 1,
        )
    }
    explanation?.let { kind ->
        AlertDialog(
            onDismissRequest = { explanation = null },
            icon = { Icon(kind.icon, contentDescription = null) },
            title = { Text("${kind.label}记录") },
            text = { Text(kind.explanation, style = TwoMemoryTypography.body) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { explanation = null }) {
                    Text("知道了")
                }
            },
        )
    }
}

/** The same initial-in-a-circle an entry shows once it reaches the timeline. */
@Composable
internal fun AuthorMark(name: String, size: Dp, accent: Color) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            style = TwoMemoryTypography.title,
            color = accent,
            fontWeight = FontWeight.Medium,
        )
    }
}
