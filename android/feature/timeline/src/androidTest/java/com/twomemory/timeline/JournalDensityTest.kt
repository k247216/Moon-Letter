package com.twomemory.timeline

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalDensityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactCoverAndPhotoPreviewExposeTheNextRecord() {
        compose.setContent { TwoMemoryTheme { TimelineScreen(listOf(
            TimelineEntryUi("first", "今天", "12:00", "星星", "一起散步",
                body = "路上有很长一段晚风。我们把今天想说的话写在这里，和拍下来的照片留在同一页。".repeat(8),
                photo = com.twomemory.model.TimelinePhoto(null, "fixture"), mine = true),
            TimelineEntryUi("second", "今天", "13:00", "月亮", "下一条回忆", body = "同一天的另一刻。"),
        )) } }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithContentDescription("首页半屏背景图").fetchSemanticsNode().boundsInRoot
        assertTrue("Cover must no longer dominate the first screen", cover.height <= root.height * .27f)
        val second = compose.onNodeWithText("下一条回忆").fetchSemanticsNode().boundsInRoot
        assertTrue("Long photo preview must leave the next record discoverable", second.top < root.bottom)
    }

    @Test fun coverLeavesRoomForTheJournalOnFirstScreen() {
        compose.setContent { TwoMemoryTheme { TimelineScreen(listOf(
            TimelineEntryUi("density", "今天", "12:00", "星星", null, body = "今天一起散步。", mine = true),
        )) } }
        val cover = compose.onNodeWithContentDescription("首页半屏背景图").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Cover should leave >=64% of the viewport for the journal", cover.height <= root.height * .36f)
        compose.onNodeWithText("今天一起散步。").assertExists()
    }

    @Test fun missingAvatarUsesTheAuthorsInitialNotAssignedGender() {
        compose.setContent { TwoMemoryTheme { TimelineScreen(listOf(
            TimelineEntryUi("initial", "今天", "12:00", "星星", null, body = "测试", mine = true),
        )) } }
        compose.onNodeWithText("星").assertExists()
    }

    @Test fun largeFontCoverCopyDoesNotOverlapChangeCoverAction() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.5f, 2f)) {
                TwoMemoryTheme { Box(Modifier.width(360.dp)) { TimelineScreen(emptyList()) } }
            }
        }
        val copy = compose.onNodeWithText("故事，从每一个今天开始").fetchSemanticsNode().boundsInRoot
        val change = compose.onNodeWithText("换一张").fetchSemanticsNode().boundsInRoot
        assertFalse("Large-font cover copy overlaps its action", copy.overlaps(change))
    }
}
