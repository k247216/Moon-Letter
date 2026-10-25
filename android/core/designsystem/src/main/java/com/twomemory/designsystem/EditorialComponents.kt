package com.twomemory.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PaperSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier) {
        Text(title, style = TwoMemoryTypography.display, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(5.dp))
        PaperUnderline(Modifier.width(54.dp))
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f),
            )
        }
    }
}

@Composable
fun PaperStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = TwoMemoryColors.WarmBeigeAccent,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = .11f),
        contentColor = accent,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = TwoMemoryTypography.metadata,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PolaroidFrame(
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    photo: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.rotate(rotation).shadow(4.dp, RoundedCornerShape(2.dp)),
        color = Color.White,
        border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeLine.copy(alpha = .55f)),
        shape = RoundedCornerShape(2.dp),
    ) {
        Box(Modifier.padding(5.dp), contentAlignment = Alignment.Center, content = photo)
    }
}

@Composable
fun PaperUnderline(
    modifier: Modifier = Modifier,
    color: Color = TwoMemoryColors.WarmBeigeAccent,
    thickness: Dp = 1.dp,
) {
    Canvas(modifier.height(7.dp)) {
        val y = size.height / 2
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y - 1.5f),
            strokeWidth = thickness.toPx(),
            pathEffect = PathEffect.cornerPathEffect(2.dp.toPx()),
        )
    }
}

@Composable
fun PaperRule(
    modifier: Modifier = Modifier,
    color: Color = TwoMemoryColors.WarmBeigeLine,
) {
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
        )
    }
}
