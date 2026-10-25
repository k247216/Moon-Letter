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
class BookmarkNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun bookmarkReopensOriginal() {
        val context = compose.activity
        val couple = UUID.randomUUID(); val user = UUID.randomUUID(); val id = UUID.randomUUID().toString()
        SyncSession.save(context, "synthetic-bookmark-navigation", couple, user, "http://127.0.0.1:9")
        val database = AppDatabase.build(context)
        context.getDatabasePath(requireNotNull(SyncSession.load(context)).databaseName).parentFile!!.mkdirs()
        runBlocking { database.entryDao().insertEntry(EntryEntity(id, couple.toString(), user.toString(),
            "PERSONAL", "PUBLISHED", Instant.parse("2026-10-13T12:00:00Z").toEpochMilli(), "Asia/Shanghai", "想夹住的那一天")) }
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme { AppNavigation() } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("想夹住的那一天").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("想夹住的那一天").performScrollTo().performClick()
        compose.onNodeWithContentDescription("夹一枚小书签").performClick()
        compose.onNodeWithContentDescription("取下小书签").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回时间线").performClick()
        compose.onNodeWithText("小书签").performScrollTo().performClick()
        compose.onNode(hasText("想夹住的那一天") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("取下小书签").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("夹一枚小书签").assertIsDisplayed()
        runBlocking { org.junit.Assert.assertNotNull(database.entryDao().findEntry(id)) }
    }
}
