package com.twomemory.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.twomemory.designsystem.TwoMemoryColors

@Composable
internal fun BookmarkToggle(marked: Boolean, description: String? = null, onToggle: () -> Unit) {
    val drop by animateFloatAsState(if (marked) 3f else 0f, tween(180), label = "bookmark-drop")
    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp).semantics {
        contentDescription = description ?: if (marked) "取下小书签" else "夹一枚小书签"
    }) {
        Canvas(Modifier.size(18.dp, 22.dp).graphicsLayer { translationY = drop.dp.toPx() }) {
            val ribbon = Path().apply {
                moveTo(size.width * .18f, 1f); lineTo(size.width * .82f, 1f)
                lineTo(size.width * .82f, size.height - 1f)
                lineTo(size.width * .5f, size.height * .72f)
                lineTo(size.width * .18f, size.height - 1f); close()
            }
            if (marked) drawPath(ribbon, TwoMemoryColors.WarmBeigeAccent.copy(alpha = .8f))
            else drawPath(ribbon, TwoMemoryColors.WarmBeigeMuted, style = Stroke(1.4.dp.toPx()))
        }
    }
}
