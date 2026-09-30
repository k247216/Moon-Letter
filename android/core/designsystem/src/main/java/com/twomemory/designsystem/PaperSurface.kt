package com.twomemory.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PaperSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline),
        content = content,
    )
}
