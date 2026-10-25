package com.twomemory.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.paperTexture
import com.twomemory.designsystem.PaperRule
import com.twomemory.timeline.TimelineEntryUi

/** Device-personal, not cloud-shared. Caller supplies visible records; missing IDs never cause fetches/deletions. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MemoryBookmarksDialog(
    visibleEntries: List<TimelineEntryUi>,
    bookmarkIds: Set<String>,
    onOpenEntry: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
) {
    // Missing/deleted/invisible records are filtered for presentation, never removed from the sidecar.
    val shown = visibleEntries.filter { it.id in bookmarkIds }.distinctBy { it.id }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TwoMemoryColors.WarmBeigeBackground,
        sheetMaxWidth = 560.dp,
    ) {
            Column(Modifier.fillMaxWidth().paperTexture().padding(horizontal = 24.dp)
                .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("书签", Modifier.weight(1f), style = TwoMemoryTypography.title)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text("我夹住的几页", style = TwoMemoryTypography.caption,
                    color = TwoMemoryColors.WarmBeigeMuted)
                PaperRule(Modifier.fillMaxWidth())
                errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
                }
                if (shown.isEmpty()) {
                    Text("还没有可见的书签记录", style = TwoMemoryTypography.body)
                } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.id }) { entry ->
                        val title = entry.title?.takeIf { it.isNotBlank() }
                            ?: entry.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(40)
                            ?: "未命名记录"
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            color = TwoMemoryColors.WarmBeigeSurface) {
                        Row(Modifier.fillMaxWidth().clickable { onOpenEntry(entry.id) }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.photo?.let { photo ->
                                EntryPhoto(photo.localPath, photo.assetId, Modifier.width(48.dp).heightIn(max = 48.dp),
                                    contentDescription = "书签照片：$title", cornerRadius = 8.dp,
                                    contentScale = ContentScale.Fit, useNaturalAspectRatio = true)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title, style = TwoMemoryTypography.body, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(entry.dateLabel, style = TwoMemoryTypography.caption,
                                    color = TwoMemoryColors.WarmBeigeMuted)
                                entry.body.trim().takeIf { it.isNotBlank() && it != title }?.let {
                                    Text(it.take(90), style = TwoMemoryTypography.caption, maxLines = 2,
                                        color = TwoMemoryColors.WarmBeigeMuted,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            BookmarkToggle(true, "移除书签：$title") { onRemoveBookmark(entry.id) }
                        }
                        }
                    }
                }
            }
    }
}
