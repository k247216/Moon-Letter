package com.twomemory.designsystem

import androidx.compose.foundation.clickable
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

data class BottomTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String = label,
)

val MoonLetterTabs = listOf(
    BottomTab("timeline", "时光", TwoMemoryIcons.Image),
    BottomTab("album", "相册", TwoMemoryIcons.Camera),
    BottomTab("create", "＋记录", TwoMemoryIcons.Add),
    BottomTab("map", "地图", TwoMemoryIcons.Location),
    BottomTab("couple", "我们", TwoMemoryIcons.Music),
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
                        modifier = Modifier.weight(1f).clickable { onSelect(tab) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(tab.icon, contentDescription = tab.contentDescription, tint = tint, modifier = Modifier.size(26.dp))
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
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.height(42.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.offset(y = (-11).dp).size(54.dp),
                shape = CircleShape,
                color = TwoMemoryColors.WarmBeigeAccent,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(TwoMemoryIcons.Add, contentDescription = "新建记录", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        Text("＋记录", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeInk)
    }
}
