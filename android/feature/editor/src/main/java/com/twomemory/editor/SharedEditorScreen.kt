package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryTypography

data class SharedBlockUi(val author: String, val text: String)

@Composable
fun SharedEditorRoute(
    state: EditorUiState,
    blocks: List<SharedBlockUi>,
    onPublish: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Button(
                onClick = onPublish,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) { Text("发布共同记录") }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("一起写这篇", style = TwoMemoryTypography.title)
            Text("同一段回忆，两个视角", style = TwoMemoryTypography.caption)
            blocks.forEach { block ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(block.author, style = TwoMemoryTypography.caption)
                            Text("  ·  可追溯编辑", style = TwoMemoryTypography.caption)
                        }
                        Text(block.text, style = TwoMemoryTypography.body)
                    }
                }
            }
        }
    }
}
