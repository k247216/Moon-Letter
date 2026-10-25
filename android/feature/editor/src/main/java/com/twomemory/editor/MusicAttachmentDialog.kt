package com.twomemory.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.MusicMemoryCard
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.isSafeMusicLink

/** Typed metadata only: the dialog does not fetch, resolve or guess a song. */
@Composable
internal fun MusicAttachmentDialog(onDismissRequest: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val safeLink = remember(url) { isSafeMusicLink(url) }
    val canAdd = if (url.isBlank()) title.isNotBlank() else safeLink
    AlertDialog(
        modifier = Modifier.widthIn(max = 480.dp),
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(22.dp),
        containerColor = TwoMemoryColors.WarmBeigeSurface,
        title = { Text("把这首歌留在这一页", style = TwoMemoryTypography.title,
            color = TwoMemoryColors.WarmBeigeInk) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("先贴播放链接，歌名和歌手可以慢慢补上。", style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(),
                    label = { Text("播放链接") }, placeholder = { Text("https://…") }, singleLine = true,
                    isError = url.isNotBlank() && !safeLink,
                    textStyle = TwoMemoryTypography.body)
                if (url.isNotBlank() && !safeLink) Text("请贴完整的 http 或 https 链接；也可以留空，只记下歌名。",
                    style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
                Text("歌名与歌手 · 选填", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(),
                    label = { Text("歌曲名称") }, singleLine = true, textStyle = TwoMemoryTypography.body)
                OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(),
                    label = { Text("歌手 / 专辑") }, singleLine = true, textStyle = TwoMemoryTypography.body)
                if (title.isNotBlank() || artist.isNotBlank() || safeLink) {
                    MusicMemoryCard(title.trim(), artist.trim(), if (safeLink) url.trim() else "")
                }
            }
        },
        confirmButton = { TextButton(enabled = canAdd, onClick = {
            if (canAdd) onAdd(title.trim(), artist.trim(), url.trim())
        }) { Text("加入这一页") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("取消") } },
    )
}
