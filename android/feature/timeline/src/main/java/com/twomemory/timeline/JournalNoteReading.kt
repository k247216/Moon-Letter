package com.twomemory.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.*
import com.twomemory.model.*

@Composable
internal fun JournalNoteReading(state: EntryDetailUi) {
    Column(Modifier.widthIn(max = 580.dp).padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        StickyNotePaper(Modifier.testTag("note-reading-paper"), appearance = state.header.notePaper,
            decorationKey = state.header.id, fragment = state.header.body.length < 80) {
            state.blocks.filter { it.type == BlockType.TEXT }.forEach { block ->
                Text(block.text.orEmpty(), style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeInk)
            }
        }
        Row(Modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.header.author, Modifier.weight(1f, fill = false), style = TwoMemoryTypography.caption,
                color = noteSignatureColor(state.header.signatureCoral), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(state.header.timeLabel, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
        }
        if (state.status == MoonLetterRecordStatus.REJECTED) Text("服务器没有收下这张纸条，可在更多操作中重新发送。",
            style = TwoMemoryTypography.caption, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun JournalNoteReplies(state: EntryDetailUi, canRespond: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.comments.isNotEmpty()) {
            PaperUnderline(Modifier.width(72.dp), color = TwoMemoryColors.WarmBeigeLine)
        } else if (canRespond) {
            Text("也给这张纸条留一句吧。", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
        state.comments.forEachIndexed { index, comment ->
            Column(Modifier.padding(start = if (index % 2 == 0) 10.dp else 24.dp, end = 12.dp).widthIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StickyNotePaper(Modifier.testTag("note-reply-${comment.id}"),
                    appearance = NotePaperAppearance(color = state.header.notePaper.color), compact = true, quiet = true,
                    decorationKey = comment.id, fragment = true) {
                    Text(comment.body, style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeInk)
                }
                Row(Modifier.padding(start = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(comment.author, Modifier.weight(1f, fill = false), style = TwoMemoryTypography.caption,
                        color = noteSignatureColor(comment.signatureCoral), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(comment.timeLabel, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                }
            }
        }
    }
}
