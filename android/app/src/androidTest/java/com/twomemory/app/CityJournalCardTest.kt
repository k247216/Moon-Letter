package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CityJournalCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cityStoryUsesJournalRecordAndOpensTheOriginalEntry() {
        var opened: String? = null
        compose.setContent { TwoMemoryTheme {
            CityMapPreviewScreen(listOf(CityStoryUi("location", "original-entry", "杭州", "10月3日",
                "湖边晚风", "把照片和文字放在同一篇回忆。", "星星")), onOpenEntry = { opened = it })
        } }
        compose.onNodeWithContentDescription("打开记录").assertExists().performClick()
        assertEquals("original-entry", opened)
        compose.onNodeWithText("回应这一刻").assertExists()
    }
}
