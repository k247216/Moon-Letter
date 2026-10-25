package com.twomemory.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role

data class BottomTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String = label,
)

val MoonLetterTabs = listOf(
    BottomTab("timeline", "时光", TwoMemoryIcons.Home),
    BottomTab("album", "相册", TwoMemoryIcons.Album),
    BottomTab("create", "＋记录", TwoMemoryIcons.Add),
    BottomTab("map", "地图", TwoMemoryIcons.Location),
    BottomTab("couple", "我们", TwoMemoryIcons.Couple),
)

@Composable
fun MoonLetterBottomNavigation(selectedKey: String, onSelect: (BottomTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TwoMemoryColors.WarmBeigeNav,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonLetterTabs.forEach { tab ->
                val selected = selectedKey == tab.key
                if (tab.key == "create") {
                    CreateTab(onClick = { onSelect(tab) })
                } else {
                    val tint = if (selected) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeInk.copy(alpha = .68f)
                    Column(
                        modifier = Modifier.weight(1f).height(60.dp).selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                            .semantics { contentDescription = tab.contentDescription },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                            Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
                        }
                        Text(tab.label, style = TwoMemoryTypography.caption, color = tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CreateTab(onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).height(60.dp).clickable(onClick = onClick)
            .semantics { contentDescription = "新建记录" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.height(36.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.offset(y = (-4).dp).size(44.dp),
                shape = CircleShape,
                color = TwoMemoryColors.WarmBeigeAccent,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(TwoMemoryIcons.Add, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
        Text("＋记录", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeInk)
    }
}
