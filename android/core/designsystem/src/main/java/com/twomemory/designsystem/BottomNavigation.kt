package com.twomemory.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomTab(val key: String, val label: String, val icon: ImageVector)

val MoonLetterTabs = listOf(
    BottomTab("timeline", "时光", TwoMemoryIcons.Image),
    BottomTab("album", "相册", TwoMemoryIcons.Camera),
    BottomTab("create", "＋记录", TwoMemoryIcons.Add),
    BottomTab("map", "地图", TwoMemoryIcons.Location),
    BottomTab("couple", "我们", TwoMemoryIcons.Music),
)

@Composable
fun MoonLetterBottomNavigation(selectedKey: String, onSelect: (BottomTab) -> Unit) {
    NavigationBar(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        MoonLetterTabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedKey == tab.key,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
