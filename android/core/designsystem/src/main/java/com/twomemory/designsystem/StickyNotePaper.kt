package com.twomemory.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

import com.twomemory.model.*

fun NotePaperColor.paperColor(): Color = when (this) {
    NotePaperColor.CREAM -> Color(0xFFFFF3D9)
    NotePaperColor.ROSE -> Color(0xFFF9E7E3)
    NotePaperColor.SAGE -> Color(0xFFEBF0E1)
    NotePaperColor.MIST -> Color(0xFFE7EFF3)
    NotePaperColor.LILAC -> Color(0xFFF0E9F4)
}

/** Static cached paper: no list animations or author-based colours. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun StickyNotePaper(modifier: Modifier = Modifier, mine: Boolean = true,
    appearance: NotePaperAppearance = NotePaperAppearance(), compact: Boolean = false, miniature: Boolean = false,
    fragment: Boolean = false, decorationKey: String = "", quiet: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val paper = appearance.color.paperColor()
    val edge = Color(0xFF9C8975)
    val variant = (decorationKey.hashCode() and Int.MAX_VALUE) % 4
    Column(modifier.padding(top = if (!quiet && appearance.style == NotePaperStyle.TAPE) 7.dp else 2.dp, bottom = 2.dp)
        .drawWithCache {
            val radius = CornerRadius(if (fragment) 2.dp.toPx() else 5.dp.toPx())
            val contour = Path().apply {
                if (fragment && appearance.style == NotePaperStyle.TAPE && !miniature) {
                    moveTo(0f, 1.dp.toPx()); lineTo(size.width, 0f); lineTo(size.width, size.height - 1.dp.toPx())
                    val steps = (size.width / 8.dp.toPx()).toInt().coerceAtLeast(2)
                    for (i in steps downTo 0) lineTo(size.width * i / steps,
                        size.height - (if ((i + variant) % 3 == 0) 1.4f else .3f).dp.toPx())
                    close()
                } else addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius))
            }
            val tape = Path().apply {
                val cx = if (miniature) size.width * .5f else if (variant == 0) 28.dp.toPx() else size.width - 29.dp.toPx()
                val w = if (miniature) 34.dp.toPx() else 31.dp.toPx(); val top = -4.dp.toPx()
                moveTo(cx-w/2, top+2.dp.toPx()); lineTo(cx+w/2, top)
                lineTo(cx+w/2-1.dp.toPx(), top+10.dp.toPx()); lineTo(cx-w/2+1.dp.toPx(), top+12.dp.toPx()); close()
            }
            val fibres = Path().apply {
                repeat(if (miniature) 0 else 24) { index ->
                    val x = 6.dp.toPx() + ((index * 37 + variant * 13) % 97) / 97f * (size.width - 12.dp.toPx()).coerceAtLeast(0f)
                    val y = 5.dp.toPx() + ((index * 29 + variant * 7) % 89) / 89f * (size.height - 10.dp.toPx()).coerceAtLeast(0f)
                    moveTo(x, y); lineTo(x + 2.dp.toPx(), y + .4.dp.toPx())
                }
            }
            onDrawBehind {
                drawPath(contour, if (quiet) paper.copy(alpha = .50f) else paper)
                drawPath(contour, edge.copy(alpha = if (quiet) .07f else .09f), style = Stroke(.55.dp.toPx()))
                // Bounded, deterministic fibres stay behind text; no textures fetched or animated.
                drawPath(fibres, edge.copy(alpha = .035f), style = Stroke(.4.dp.toPx()))
                when (appearance.style) {
                    NotePaperStyle.TAPE -> if (!quiet) when (if (miniature) 0 else variant) {
                        0, 1 -> drawPath(tape, Color(0xFFE1C9AA).copy(alpha = .48f))
                        2 -> {
                            drawCircle(edge.copy(alpha = .25f), 2.2.dp.toPx(), Offset(12.dp.toPx(), 10.dp.toPx()))
                            drawCircle(Color.White.copy(alpha = .6f), .7.dp.toPx(), Offset(11.5.dp.toPx(), 9.5.dp.toPx()))
                        }
                        else -> Unit
                    }
                    NotePaperStyle.RULED -> {
                        var y = if (miniature) 11.dp.toPx() else if (compact) 39.dp.toPx() else 57.dp.toPx()
                        val margin = if (miniature) 7.dp.toPx() else 16.dp.toPx()
                        while (y < size.height - if (miniature) 6.dp.toPx() else 20.dp.toPx()) {
                            drawLine(edge.copy(alpha = .11f), Offset(margin, y), Offset(size.width-margin, y), .65.dp.toPx())
                            y += if (miniature) 9.dp.toPx() else if (compact) 27.dp.toPx() else 32.dp.toPx()
                        }
                    }
                    NotePaperStyle.LETTER -> {
                        drawLine(edge.copy(alpha = .21f), Offset(8.dp.toPx(), 12.dp.toPx()),
                            Offset(8.dp.toPx(), size.height-12.dp.toPx()), 1.3.dp.toPx())
                        drawCircle(edge.copy(alpha = .22f), 2.dp.toPx(), Offset(size.width-16.dp.toPx(), 16.dp.toPx()))
                    }
                }
            }
        }.padding(horizontal = if (compact) 15.dp else 22.dp, vertical = if (compact) 12.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp), content = content)
}
