package com.twomemory.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.PaperSurface
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography

@Composable
fun CoupleRoute(viewModel: CoupleViewModel, onThemeChange: (MoonLetterTheme) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    CoupleScreen(state, viewModel::updateOwnName) {
        viewModel.updateTheme(it)
        onThemeChange(it)
    }
}

@Composable
fun CoupleScreen(
    state: CoupleUiState,
    onOwnNameChange: (String) -> Unit,
    onThemeChange: (MoonLetterTheme) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("我们", style = TwoMemoryTypography.display)
        Text("把两个人的小世界，慢慢整理好", style = TwoMemoryTypography.caption)
        PaperSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AvatarBadge(state.ownName)
                    Text("＋", modifier = Modifier.align(Alignment.CenterVertically), style = TwoMemoryTypography.title)
                    AvatarBadge(state.partnerName)
                }
                OutlinedTextField(
                    value = state.ownName,
                    onValueChange = onOwnNameChange,
                    label = { Text("我的名字") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(state.anniversaryLabel, style = TwoMemoryTypography.body)
            }
        }
        Text("界面颜色", style = TwoMemoryTypography.title)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.theme == MoonLetterTheme.WARM_BEIGE,
                onClick = { onThemeChange(MoonLetterTheme.WARM_BEIGE) },
                label = { Text("米色") },
            )
            FilterChip(
                selected = state.theme == MoonLetterTheme.PURE_WHITE,
                onClick = { onThemeChange(MoonLetterTheme.PURE_WHITE) },
                label = { Text("纯白") },
            )
        }
        Spacer(Modifier.height(50.dp))
    }
}

@Composable
private fun AvatarBadge(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(TwoMemoryColors.WarmBeigeAccent),
        )
        Text(name, style = TwoMemoryTypography.caption)
    }
}
