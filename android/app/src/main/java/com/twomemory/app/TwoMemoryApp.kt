package com.twomemory.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme

@Composable
fun TwoMemoryApp() {
    var theme by remember { mutableStateOf(MoonLetterTheme.WARM_BEIGE) }
    TwoMemoryTheme(theme = theme) {
        AppNavigation(onThemeChange = { theme = it })
    }
}
