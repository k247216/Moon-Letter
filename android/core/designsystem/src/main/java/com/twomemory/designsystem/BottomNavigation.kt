package com.twomemory.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
        // The reference uses one quiet lavender rail for all five actions;
        // selection is shown by the small pill, not by a floating action
        // button that competes with the records.
        color = Color(0xFFF4ECFA),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonLetterTabs.forEach { tab ->
                val selected = selectedKey == tab.key
                val tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { onSelect(tab) }
                        .background(
                            if (selected) Color(0xFFE7D9F6) else Color.Transparent,
                            RoundedCornerShape(28.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = tab.contentDescription, tint = tint, modifier = Modifier.size(27.dp))
                    Text(tab.label, style = TwoMemoryTypography.caption, color = tint)
                }
            }
        }
    }
}
