package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.model.EntryMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SharedEditorRoute(
    state: EditorUiState,
    onPublish: () -> Unit,
    onTitleChange: (String) -> Unit = {},
    onBodyChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onModeChange: (EntryMode) -> Unit = {},
    photoActions: EditorPhotoActions? = null,
    ownName: String = "",
) {
    val author = ownName.ifBlank { "我" }
    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            ) { EditorAttachmentToolbar(onImageClick = photoActions?.pick) }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SharedTopBar(state, onClose, onPublish)
            RecordModeSwitch(EntryMode.COLLABORATIVE, onModeChange)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorMark(author, 48.dp, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("先写下你的部分，TA 可以补充自己的视角。", style = TwoMemoryTypography.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
            }
            BasicTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TwoMemoryTypography.display.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (state.title.isBlank()) Text("这段回忆叫什么", style = TwoMemoryTypography.display,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f))
                        inner()
                    }
                },
            )
            AuthorBlock(
                name = author,
                accent = MaterialTheme.colorScheme.primary,
            ) {
                BasicTextField(
                    value = state.body,
                    onValueChange = onBodyChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TwoMemoryTypography.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    minLines = 6,
                    decorationBox = { inner ->
                        Box {
                            if (state.body.isBlank()) Text("从你的视角写下这一刻……", style = TwoMemoryTypography.body,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
                            inner()
                        }
                    },
                )
            }
            EditorPhotoStrip(
                photos = state.photos,
                photoError = state.photoError,
                onRemove = { photoActions?.remove?.invoke(it) },
            )
            state.error?.let {
                Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.size(96.dp))
        }
    }
}

@Composable
private fun SharedTopBar(state: EditorUiState, onClose: () -> Unit, onPublish: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(TwoMemoryIcons.Close, contentDescription = "关闭编辑器")
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Icon(TwoMemoryIcons.Time, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                state.occurrenceTime.atZone(ZoneId.of(state.timezone))
                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日 · HH:mm", Locale.CHINA)),
                style = TwoMemoryTypography.body,
            )
        }
        TextButton(onClick = onPublish, enabled = !state.saving && state.hasContent) {
            Text(if (state.saving) "发布中" else "发布", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AuthorBlock(
    name: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorMark(name, 34.dp, accent)
                Spacer(Modifier.width(8.dp))
                Text(name, style = TwoMemoryTypography.caption, color = accent)
                Spacer(Modifier.width(8.dp))
                Text("此刻", style = TwoMemoryTypography.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
            }
            content()
        }
    }
}
