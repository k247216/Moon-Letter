package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.PaperSurface
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val occurrenceFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 · HH:mm", Locale.CHINA)

@Composable
fun PersonalEditorRoute(
    viewModel: EditorViewModel,
    onPublish: suspend (EditorUiState) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    PersonalEditorScreen(state, viewModel::updateTitle, viewModel::updateBody) {
        viewModel.publish(onPublish)
    }
}

@Composable
fun PersonalEditorScreen(
    state: EditorUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onPublish: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                state.error?.let {
                    Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = TwoMemoryTypography.caption,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Button(
                    onClick = onPublish,
                    enabled = !state.saving && state.hasContent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            state.saving -> "保存中…"
                            state.saved -> "已保存"
                            else -> "发布这篇记录"
                        }
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("写下此刻", style = TwoMemoryTypography.title)
            Text(
                state.occurrenceTime.atZone(ZoneId.of(state.timezone)).format(occurrenceFormatter),
                style = TwoMemoryTypography.caption,
            )
            PaperSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        placeholder = { Text("给今天留一句标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.body,
                        onValueChange = onBodyChange,
                        placeholder = { Text("今天的心情、做过的事，慢慢写下来…") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "个人记录正文" },
                        minLines = 8,
                    )
                }
            }
            EditorAttachmentToolbar()
            Spacer(Modifier.size(80.dp))
        }
    }
}

@Composable
fun EditorAttachmentToolbar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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
            IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = label }) {
                Icon(icon, contentDescription = label)
            }
        }
    }
}
