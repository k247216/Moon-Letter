package com.twomemory.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.*
import com.twomemory.model.EntryMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DraftLibraryScreen(items: List<SavedDraft>, onBack: () -> Unit,
    onResume: (SavedDraft) -> Unit, onDelete: (SavedDraft) -> Unit, onNew: ((EntryMode) -> Unit)? = null) {
    var deleting by remember { mutableStateOf<SavedDraft?>(null) }
    LazyColumn(Modifier.fillMaxSize().paperTexture(TwoMemoryColors.WarmBeigeBackground),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick = onBack) { Text("返回时间轴") }
            Text("还没写完的日子", style = TwoMemoryTypography.display)
            Text("仅保存在这台设备，暂不分享给对方", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
            onNew?.let {create -> Row {
                TextButton(onClick={create(EntryMode.PERSONAL)}) {Text("另写一篇")}
                TextButton(onClick={create(EntryMode.COLLABORATIVE)}) {Text("新的共同记录")}
            } }
            PaperRule(Modifier.padding(top = 14.dp))
        }
        if (items.isEmpty()) item {
            Text("草稿箱暂时是空的", style = TwoMemoryTypography.title)
            Text("写到一半也没关系，回来时可以接着写。", style = TwoMemoryTypography.body)
        }
        items(items, key = { it.id }) { saved ->
            val title = saved.draft.title.ifBlank { saved.draft.body.lineSequence().firstOrNull()?.take(24).orEmpty().ifBlank { "未完成的回忆" } }
            Surface(Modifier.fillMaxWidth(), color = TwoMemoryColors.WarmBeigeSurface, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onResume(saved) }.padding(vertical = 8.dp)) {
                        Text(title, style = TwoMemoryTypography.title)
                        Text(saved.draft.body.take(100), style = TwoMemoryTypography.body, maxLines = 3)
                        Text(if (saved.mode == EntryMode.COLLABORATIVE) "共同记录 · 我的未完成视角" else "自己的未完成回忆",
                            style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                        Text(Instant.ofEpochMilli(saved.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                            style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                        if (saved.draft.photos.isNotEmpty() || saved.draft.attachments.isNotEmpty())
                            Text("照片、声音和附件已随草稿保留", style = TwoMemoryTypography.caption)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onResume(saved) }) { Text("继续写") }
                        IconButton(onClick = { deleting = saved }) {
                            Icon(TwoMemoryIcons.Close, "删除草稿$title", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    deleting?.let { saved -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除草稿？") },
        text = { Text("只移除这篇草稿，不删除已经发布的记录或原媒体文件。") },
        confirmButton = { TextButton(onClick = { onDelete(saved); deleting = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("保留") } }) }
}
