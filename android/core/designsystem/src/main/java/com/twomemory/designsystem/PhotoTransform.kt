package com.twomemory.designsystem

internal data class PhotoTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun change(zoom: Float, panX: Float, panY: Float, width: Float, height: Float): PhotoTransform {
        val nextScale = (scale * zoom.takeIf { it.isFinite() && it > 0f }.let { it ?: 1f }).coerceIn(1f, 5f)
        if (nextScale == 1f) return PhotoTransform()
        val limitX = width.coerceAtLeast(0f) * (nextScale - 1f) / 2f
        val limitY = height.coerceAtLeast(0f) * (nextScale - 1f) / 2f
        fun offset(current: Float, delta: Float, limit: Float): Float =
            (current * nextScale / scale + if (delta.isFinite()) delta else 0f).coerceIn(-limit, limit)
        return PhotoTransform(nextScale, offset(x, panX, limitX), offset(y, panY, limitY))
    }
}
