package com.twomemory.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pinch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.ZoomablePhoto
import org.junit.Rule
import org.junit.Test

class ZoomablePhotoTest {
    @get:Rule val compose = createComposeRule()
    @Test fun doubleTapMagnifiesAndSecondTapRestoresOriginal() {
        compose.setContent {
            ZoomablePhoto("first", Modifier.size(300.dp).testTag("photo")) {
                Box(Modifier.fillMaxSize().background(Color.Gray))
            }
        }
        compose.onNodeWithTag("photo").performTouchInput { doubleClick() }
        compose.onNodeWithTag("photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已放大"))
        compose.onNodeWithTag("photo").performTouchInput { doubleClick() }
        compose.onNodeWithTag("photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "原始大小"))
    }
    @Test fun switchingPhotoResetsThePreviousPhotosTransform() {
        val photo = mutableStateOf("first")
        compose.setContent {
            ZoomablePhoto(photo.value, Modifier.size(300.dp).testTag("photo")) {
                Box(Modifier.fillMaxSize().background(Color.Gray))
            }
        }
        compose.onNodeWithTag("photo").performTouchInput { doubleClick() }
        compose.runOnIdle { photo.value = "second" }
        compose.onNodeWithTag("photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "原始大小"))
    }
    @Test fun twoFingerPinchActuallyMagnifiesThePhoto() {
        compose.setContent {
            ZoomablePhoto("pinch", Modifier.size(300.dp).testTag("photo")) {
                Box(Modifier.fillMaxSize().background(Color.Gray))
            }
        }
        compose.onNodeWithTag("photo").performTouchInput {
            pinch(Offset(width * .4f, height * .5f), Offset(width * .6f, height * .5f),
                Offset(width * .1f, height * .5f), Offset(width * .9f, height * .5f))
        }
        compose.onNodeWithTag("photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已放大"))
    }
}
