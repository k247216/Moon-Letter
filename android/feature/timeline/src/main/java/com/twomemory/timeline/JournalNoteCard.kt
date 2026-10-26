package com.twomemory.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.*
import kotlin.math.ceil

/** Actual wrapped text determines paper size; stable ids determine decoration, never list position. */
@Composable
internal fun JournalNoteCard(entry: TimelineEntryUi, onOpen: (String) -> Unit) {
    val signature = noteSignatureColor(entry.signatureCoral)
    Box(Modifier.fillMaxWidth().journalRail()) {
        Box(Modifier.padding(start = 20.dp, top = 21.dp).size(7.dp).clip(CircleShape).background(signature))
        Column(Modifier.fillMaxWidth().padding(start = 34.dp, end = 20.dp, bottom = 20.dp)
            .semantics { contentDescription = "打开小便签" }.clickable { onOpen(entry.id) },
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val style = TwoMemoryTypography.body
                // Compose rounds each padding edge separately; use identical pixel arithmetic.
                val paddingPx = with(density) { 15.dp.roundToPx() }
                val maxPx = with(density) { (maxWidth.roundToPx() - 2 * paddingPx).coerceAtLeast(1) }
                val layout = remember(entry.body, maxPx, density.fontScale, style) {
                    measurer.measure(entry.body, style, constraints = Constraints(maxWidth = maxPx))
                }
                val short = layout.lineCount <= 2
                val width = if (short) with(density) {
                    (ceil((0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) }
                        ?.toDouble() ?: 0.0).toInt() + 2 * paddingPx).toDp().coerceIn(76.dp.coerceAtMost(maxWidth), maxWidth)
                } else maxWidth
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StickyNotePaper(Modifier.width(width).testTag("note-paper-${entry.id}"), appearance = entry.notePaper,
                    compact = true, fragment = short, decorationKey = entry.id) {
                    Text(entry.body, style = style, color = TwoMemoryColors.WarmBeigeInk,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
                if (layout.lineCount > 5) Text("轻点，读完这张纸条", style = TwoMemoryTypography.metadata,
                    color = TwoMemoryColors.WarmBeigeMuted,
                    modifier = Modifier.padding(start = 5.dp))
                }
            }
            Row(Modifier.testTag("note-signature-${entry.id}").padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(entry.author, Modifier.weight(1f, fill = false), style = TwoMemoryTypography.metadata, color = signature,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.timeLabel, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                if (entry.privateRecord) Text("仅自己", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                if (entry.unread) UnreadLetterIndicator(signature)
            }
        }
    }
}

internal fun noteSignatureColor(coral: Boolean) = if (coral) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage
