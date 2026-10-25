package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import com.twomemory.timeline.EntryBlockUi
import com.twomemory.timeline.EntryDetailScreen
import com.twomemory.timeline.EntryDetailUi
import com.twomemory.timeline.TimelineEntryUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceTitleReaderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun readerUsesTheSavedVoiceTitleRatherThanTheGenericPlaybackHeading() {
        val page = EntryDetailUi(TimelineEntryUi("synthetic", "2026年10月13日", "20:00", "我", "声音礼物"),
            listOf(EntryBlockUi("voice", BlockType.AUDIO, null, null, null,
                payload = """{"title":"那天的晚安","mime":"audio/mp4"}""")), emptyList())
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            EntryDetailScreen(page, {}, {}, {})
        } } }
        compose.onNodeWithText("那天的晚安").assertIsDisplayed()
        compose.onNodeWithText("留给你的声音").assertDoesNotExist()
    }
}
