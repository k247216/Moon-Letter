package com.twomemory.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private object NoteShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val fold = with(density) { 19.dp.toPx() }.coerceAtMost(size.height / 3)
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - fold)
            lineTo(size.width - fold, size.height); lineTo(0f, size.height); close()
        })
    }
}

/** Static vector paper; cheap in a scrolling list, shared with the writing sheet. */
@Composable
fun StickyNotePaper(modifier: Modifier = Modifier, mine: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    val paper = if (mine) Color(0xFFFFF0C9) else Color(0xFFEAF0DD)
    val ink = if (mine) Color(0xFFB39159) else Color(0xFF829675)
    Box(modifier.padding(top = 12.dp, bottom = 3.dp)) {
        Column(Modifier.fillMaxWidth().shadow(2.dp, NoteShape).background(paper, NoteShape)
            .drawBehind {
                val fold = 19.dp.toPx()
                drawPath(Path().apply {
                    moveTo(size.width - fold, size.height); lineTo(size.width - fold, size.height - fold)
                    lineTo(size.width, size.height - fold); close()
                }, ink.copy(alpha = .16f))
                drawLine(ink.copy(alpha = .14f), Offset(22.dp.toPx(), size.height - 17.dp.toPx()),
                    Offset(size.width * .45f, size.height - 17.dp.toPx()), 1.dp.toPx())
            }.padding(start = 22.dp, end = 24.dp, top = 27.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        Canvas(Modifier.align(Alignment.TopCenter).offset(y = (-9).dp).size(62.dp, 22.dp)) {
            rotate(-5f) { drawRect(ink.copy(alpha = .23f)) }
            drawLine(Color.White.copy(alpha = .4f), Offset(5.dp.toPx(), 5.dp.toPx()),
                Offset(size.width - 5.dp.toPx(), 3.dp.toPx()), 1.dp.toPx())
        }
    }
}
