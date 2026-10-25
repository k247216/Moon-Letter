package com.twomemory.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.EntryPhoto
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoFitBackgroundTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun aLoadedPortraitDoesNotPaintGrayBarsAroundItsFitImage() {
        val file = File(compose.activity.filesDir, "photos/${UUID.randomUUID()}.png")
        file.parentFile!!.mkdirs()
        val image = Bitmap.createBitmap(60, 180, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.RED)
        try {
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
                Box(Modifier.size(300.dp).background(Color.White).testTag("photo-slot")) {
                    EntryPhoto(file.absolutePath, null, Modifier.size(300.dp), "竖版照片",
                        contentScale = ContentScale.Fit, cornerRadius = 0.dp, useNaturalAspectRatio = false)
                }
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("竖版照片").fetchSemanticsNodes().isNotEmpty() }
            val pixels = compose.onNodeWithTag("photo-slot").captureToImage().asAndroidBitmap()
            assertEquals("Only the photo should paint; the page remains white beside it", android.graphics.Color.WHITE,
                pixels.getPixel(20, 150))
            assertEquals(android.graphics.Color.RED, pixels.getPixel(150, 150))
        } finally { image.recycle(); file.delete() }
    }

    @Test fun aWidePhotoUsesItsActualAspectInsteadOfClampingItIntoATallerFrame() {
        val file = File(compose.activity.filesDir, "photos/${UUID.randomUUID()}.png")
        file.parentFile!!.mkdirs()
        val image = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
                EntryPhoto(file.absolutePath, null, Modifier.width(300.dp).heightIn(max = 220.dp),
                    "横版照片", contentScale = ContentScale.Fit)
            } } }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("横版照片").fetchSemanticsNodes().isNotEmpty() }
            val bounds = compose.onNodeWithContentDescription("横版照片").fetchSemanticsNode().boundsInRoot
            assertEquals("Natural 3:1 photo should be 100px tall in a 300px slot", 100f, bounds.height, 0.5f)
        } finally { image.recycle(); file.delete() }
    }
}
