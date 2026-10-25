package com.twomemory.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize

/** Viewer-only gestures: image transforms are clipped so controls remain stable and tappable. */
@Composable
fun ZoomablePhoto(photoKey: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var transform by remember(photoKey) { mutableStateOf(PhotoTransform()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun toggleZoom() {
        transform = if (transform.scale > 1f) PhotoTransform()
        else transform.change(2.5f, 0f, 0f, viewport.width.toFloat(), viewport.height.toFloat())
    }
    Box(modifier.clipToBounds().onSizeChanged {
        viewport = it
        transform = transform.change(1f, 0f, 0f, it.width.toFloat(), it.height.toFloat())
    }.semantics {
        stateDescription = if (transform.scale > 1f) "已放大" else "原始大小"
        customActions = listOf(CustomAccessibilityAction(if (transform.scale > 1f) "还原图片" else "放大图片") {
            toggleZoom(); true
        })
    }.pointerInput(photoKey) {
        detectTapGestures(onDoubleTap = { toggleZoom() })
    }.pointerInput(photoKey) {
        detectTransformGestures { _, pan, zoom, _ ->
            transform = transform.change(zoom, pan.x, pan.y, viewport.width.toFloat(), viewport.height.toFloat())
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.x
            translationY = transform.y
        }) { content() }
    }
}
