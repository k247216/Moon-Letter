package com.twomemory.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.*
import com.twomemory.model.*

@Composable
internal fun NoteStationeryPicker(paper: NotePaperAppearance, onChange: (NotePaperAppearance) -> Unit,
    enabled: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PaperTextButton(onClick = { expanded = !expanded }, enabled = enabled) {
            Text(if (expanded) "收起纸样" else "换张纸 · ${paper.style.label} / ${paper.color.label}",
                style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotePaperStyle.entries.forEach { style ->
                    val selected = style == paper.style
                    Column(Modifier.weight(1f).heightIn(min = 72.dp)
                        .border(if (selected) 1.dp else .5.dp,
                            TwoMemoryColors.WarmBeigeAccent.copy(alpha = if (selected) .65f else .15f), RoundedCornerShape(10.dp))
                        .selectable(selected, enabled = enabled, role = Role.RadioButton,
                            onClick = { onChange(paper.copy(style = style)) })
                        .semantics { contentDescription = "纸样：${style.label}" }
                        .padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        StickyNotePaper(Modifier.fillMaxWidth(), appearance = paper.copy(style = style), compact = true, miniature = true) {
                            Spacer(Modifier.height(1.dp))
                        }
                        Text(style.label, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeInk)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly) {
                NotePaperColor.entries.forEach { color ->
                    val selected = color == paper.color
                    Column(Modifier.widthIn(min = 48.dp).heightIn(min = 64.dp)
                        .selectable(selected, enabled = enabled, role = Role.RadioButton,
                            onClick = { onChange(paper.copy(color = color)) })
                        .semantics { contentDescription = "纸色：${color.label}" }
                        .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(28.dp).background(color.paperColor(), CircleShape)
                            .border(if (selected) 1.5.dp else .6.dp,
                                TwoMemoryColors.WarmBeigeAccent.copy(alpha = if (selected) .8f else .2f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (selected) Text("·", color = TwoMemoryColors.WarmBeigeInk)
                        }
                        Text(color.label, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                    }
                }
            }
        }
    }
}
