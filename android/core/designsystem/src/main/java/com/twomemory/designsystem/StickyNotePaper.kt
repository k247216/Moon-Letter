package com.twomemory.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
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
    content: @Composable ColumnScope.() -> Unit) {
    val paper = appearance.color.paperColor()
    val edge = Color(0xFF9C8975)
    Column(modifier.padding(top = if (appearance.style == NotePaperStyle.TAPE) 8.dp else 2.dp, bottom = 3.dp)
        .drawWithCache {
            val radius = CornerRadius(7.dp.toPx())
            val tape = Path().apply {
                val cx = size.width * .5f; val w = 43.dp.toPx(); val top = -5.dp.toPx()
                moveTo(cx-w/2, top+2.dp.toPx()); lineTo(cx+w/2, top)
                lineTo(cx+w/2-1.dp.toPx(), top+13.dp.toPx()); lineTo(cx-w/2+1.dp.toPx(), top+15.dp.toPx()); close()
            }
            onDrawBehind {
                drawRoundRect(edge.copy(alpha = .055f), topLeft = Offset(0f, 2.dp.toPx()), cornerRadius = radius)
                drawRoundRect(paper, cornerRadius = radius)
                drawRoundRect(edge.copy(alpha = .11f), cornerRadius = radius, style = Stroke(.65.dp.toPx()))
                when (appearance.style) {
                    NotePaperStyle.TAPE -> drawPath(tape, Color(0xFFE1C9AA).copy(alpha = .63f))
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
        }.padding(horizontal = if (compact) 18.dp else 22.dp, vertical = if (compact) 18.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp), content = content)
}
