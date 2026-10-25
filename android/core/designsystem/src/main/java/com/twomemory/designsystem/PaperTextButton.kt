package com.twomemory.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/** The original button contract and ripple, with a local press/release instead of layout animation. */
@Composable
fun PaperTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val amount by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(120), label = "paper-press")
    TextButton(onClick = onClick, modifier = modifier.graphicsLayer {
        scaleX = 1f - amount * .025f; scaleY = scaleX; translationY = amount * .7f
    }, enabled = enabled, shape = shape, colors = colors, border = border,
        contentPadding = contentPadding, interactionSource = source, content = content)
}
