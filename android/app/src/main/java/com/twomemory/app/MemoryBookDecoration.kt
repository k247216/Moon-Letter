package com.twomemory.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryTypography

private val BookPaper=Color(0xFFFFFCF5)
private val BookInk=Color(0xFFB9A391)

/** Static vector decoration: no bitmap decoding, timers, or archive preloading. */
@Composable
internal fun BookPaperEdges(modifier: Modifier=Modifier) {
    Canvas(modifier) {
        val inset=3.dp.toPx()
        drawRoundRect(Color(0xFFE9DDC9),topLeft=Offset(inset,inset),
            size=androidx.compose.ui.geometry.Size(size.width-inset,size.height-inset),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        drawLine(BookInk.copy(alpha=.35f),Offset(size.width-inset,20.dp.toPx()),
            Offset(size.width-inset,size.height-20.dp.toPx()),1.dp.toPx())
    }
}

@Composable
internal fun BookDateStamp(date: String,time: String,shared: Boolean) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(date,style=TwoMemoryTypography.caption,color=TwoMemoryColors.WarmBeigeMuted)
            Text(time,style=TwoMemoryTypography.caption,color=TwoMemoryColors.WarmBeigeMuted.copy(alpha=.75f))
        }
        Box(Modifier.rotate(-4f).border(1.dp,TwoMemoryColors.WarmBeigeAccent.copy(alpha=.45f),RoundedCornerShape(9.dp))
            .padding(horizontal=10.dp,vertical=7.dp)) {
            Text(if(shared) "两个人的日子" else "日常珍藏",style=TwoMemoryTypography.caption,
                color=TwoMemoryColors.WarmBeigeAccent)
        }
    }
}

@Composable
internal fun BookHandDrawnRule() {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val y=size.height*.55f
        val path=Path().apply {
            moveTo(0f,y)
            cubicTo(size.width*.16f,y-3.dp.toPx(),size.width*.30f,y+3.dp.toPx(),size.width*.43f,y)
            cubicTo(size.width*.59f,y-2.dp.toPx(),size.width*.77f,y+2.dp.toPx(),size.width*.86f,y)
        }
        drawPath(path,BookInk.copy(alpha=.55f),style=Stroke(1.dp.toPx()))
        val x=size.width*.94f; val r=3.dp.toPx()
        val heart=Path().apply {
            moveTo(x,y+r)
            cubicTo(x-r*3,y-r,x-r,y-r*2,x,y-r*.5f)
            cubicTo(x+r,y-r*2,x+r*3,y-r,x,y+r)
        }
        drawPath(heart,TwoMemoryColors.WarmBeigeAccent.copy(alpha=.6f),style=Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun BookHandwriting(text: String,mine: Boolean) {
    val color=if(mine) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage
    Text(text,style=TwoMemoryTypography.body,modifier=Modifier.fillMaxWidth().drawBehind {
        // Only a small side annotation, never lines through the letters.
        drawLine(color.copy(alpha=.3f),Offset(0f,3.dp.toPx()),Offset(0f,size.height-3.dp.toPx()),2.dp.toPx())
    }.padding(start=10.dp))
}

@Composable
internal fun BookPhotoFrame(index: Int,content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top=5.dp,bottom=3.dp)) {
        Column(Modifier.fillMaxWidth().border(1.dp,BookInk.copy(alpha=.4f),RoundedCornerShape(5.dp))
            .background(BookPaper,RoundedCornerShape(5.dp)).padding(8.dp)) {content()}
        val tape=if(index%2==0) Color(0xFFD7DED0) else Color(0xFFEBD8BF)
        Box(Modifier.align(Alignment.TopStart).padding(start=15.dp).offset(y=(-4).dp).rotate(-13f)
            .size(38.dp,12.dp).background(tape.copy(alpha=.8f)))
        Box(Modifier.align(Alignment.BottomEnd).padding(end=15.dp).offset(y=4.dp).rotate(-13f)
            .size(32.dp,10.dp).background(tape.copy(alpha=.7f)))
    }
}
