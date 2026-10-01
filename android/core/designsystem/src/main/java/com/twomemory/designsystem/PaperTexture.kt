package com.twomemory.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A deliberately quiet paper grain used behind the reference screens.
 * It is deterministic (so screenshots do not shimmer) and stays below all
 * content, which keeps text and media readable while avoiding a flat colour
 * block on large empty areas.
 */
fun Modifier.paperTexture(background: Color = TwoMemoryColors.WarmBeigeBackground): Modifier = drawBehind {
    drawRect(background)
    val step = 52.dp.toPx()
    var x = step / 2f
    while (x < size.width) {
        var y = step / 2f
        while (y < size.height) {
            drawCircle(
                color = TwoMemoryColors.WarmBeigeLine.copy(alpha = .13f),
                radius = 0.7.dp.toPx(),
                center = Offset(x, y),
            )
            y += step
        }
        x += step
    }
}
