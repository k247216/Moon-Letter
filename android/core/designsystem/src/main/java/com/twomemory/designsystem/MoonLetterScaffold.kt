package com.twomemory.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The shared page frame: every feature keeps the same paper, insets and rhythm. */
@Composable
fun MoonLetterPaperScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.paperTexture(MaterialTheme.colorScheme.background),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = topBar,
        bottomBar = bottomBar,
        content = content,
    )
}
