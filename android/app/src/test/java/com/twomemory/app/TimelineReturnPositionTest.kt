package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryEntity
import com.twomemory.designsystem.TwoMemoryTheme
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineReturnPositionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun readingAnOldRecordAndSwitchingTabsKeepsMyPlaceInTheTimeline() {
        val context = compose.activity
        val couple = UUID.randomUUID()
        val user = UUID.randomUUID()
        SyncSession.save(context, "synthetic-reader", user, couple, "http://127.0.0.1:9")
        val database = AppDatabase.build(context)
        runBlocking {
            repeat(30) { index ->
                database.entryDao().insertEntry(EntryEntity(UUID.randomUUID().toString(),
                    couple.toString(), user.toString(), "PERSONAL", "PUBLISHED",
                    Instant.parse("2026-10-12T12:00:00Z").minusSeconds(index * 3600L).toEpochMilli(),
                    "Asia/Shanghai", "第${index + 1}页旧笺"))
            }
        }
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme { AppNavigation() } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("第1页旧笺").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("第20页旧笺"))
        compose.onNodeWithText("第20页旧笺").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("返回时间线").performClick()
        compose.onNodeWithText("第20页旧笺").assertIsDisplayed()
        compose.onNodeWithContentDescription("我们").performClick()
        compose.onNodeWithContentDescription("时光").performClick()
        compose.onNodeWithText("第20页旧笺").assertIsDisplayed()
        compose.onNodeWithText("记下今天").assertDoesNotExist()
    }
}
