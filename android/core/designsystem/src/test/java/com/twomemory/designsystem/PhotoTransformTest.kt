package com.twomemory.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoTransformTest {
    @Test fun draggingAtOriginalSizeCannotLoseThePhoto() {
        val next = PhotoTransform().change(1f, 500f, -500f, 300f, 200f)
        assertEquals(0f, next.x)
        assertEquals(0f, next.y)
    }
    @Test fun enlargedPhotoCannotBeDraggedBeyondViewportEdges() {
        val next = PhotoTransform().change(2f, 500f, -500f, 300f, 200f)
        assertEquals(2f, next.scale)
        assertEquals(150f, next.x)
        assertEquals(-100f, next.y)
    }
    @Test fun zoomingBackToOriginalResetsBothOffsets() {
        val next = PhotoTransform(3f, 200f, 100f).change(.1f, 0f, 0f, 300f, 200f)
        assertEquals(PhotoTransform(), next)
    }
    @Test fun excessiveZoomAndInvalidGesturesStayFinite() {
        val next = PhotoTransform().change(100f, Float.NaN, Float.POSITIVE_INFINITY, 300f, 200f)
        assertTrue(next.scale in 1f..5f)
        assertTrue(next.x.isFinite() && next.y.isFinite())
    }
}
