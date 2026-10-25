package com.twomemory.app

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.*
import com.twomemory.timeline.*
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test

class MapJournalPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cityDataLivesInSettingsNotTheMapOrProfileLanding() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val own = java.util.UUID.randomUUID(); val couple = java.util.UUID.randomUUID()
        SyncSession.save(context, "isolated-style-fixture", couple, own, "http://127.0.0.1:1")
        val db = com.twomemory.database.AppDatabase.build(context)
        kotlinx.coroutines.runBlocking {
            listOf("杭州", "杭州市", "武汉").forEachIndexed { index, city ->
                val entry = java.util.UUID.randomUUID().toString()
                db.entryDao().insertEntry(com.twomemory.database.EntryEntity(entry, couple.toString(), own.toString(), "PERSONAL", "PUBLISHED", 100L + index, "UTC", "回忆"))
                db.entryDao().insertBlocks(listOf(com.twomemory.database.EntryBlockEntity(java.util.UUID.randomUUID().toString(), entry,
                    "LOCATION", 0, own.toString(), "{\"city\":\"$city\"}", null)))
            }
            val oldEntry = java.util.UUID.randomUUID().toString()
            db.entryDao().insertEntry(com.twomemory.database.EntryEntity(oldEntry, java.util.UUID.randomUUID().toString(),
                own.toString(), "PERSONAL", "PUBLISHED", 500L, "UTC", "旧空间"))
            db.entryDao().insertBlocks(listOf(com.twomemory.database.EntryBlockEntity(java.util.UUID.randomUUID().toString(),
                oldEntry, "LOCATION", 0, own.toString(), "{\"city\":\"成都\"}", null)))
        }
        compose.setContent { TwoMemoryTheme { AppNavigation() } }
        compose.onNodeWithContentDescription("我们").performClick()
        compose.onNodeWithText("回忆数据").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("回忆数据").assertExists()
        compose.onNodeWithText("2 座有故事的城市 · 2 个省级地区 · 3 篇城市记录").assertExists()
        capture("settings-v24")
    }

    @Test fun storyAndSelectionRemainDifferentWhenSelectionMoves() {
        val selected = mutableStateOf("武汉")
        compose.setContent { TwoMemoryTheme {
            CitySelectionMap(selected.value, mapOf("武汉" to 1, "宜昌" to 2), { selected.value = it.name }, Modifier.height(420.dp))
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("武汉，1篇记录，已选择").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("武汉，1篇记录，已选择").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "当前选中的城市，有故事"))
        compose.onNodeWithContentDescription("宜昌，2篇记录").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "有故事的城市"))
        compose.onNodeWithContentDescription("宜昌，2篇记录").performClick()
        compose.onNodeWithContentDescription("武汉，1篇记录").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "有故事的城市"))
        capture("map-v24")
    }

    @Test fun unreadIsASubtleVisualLetterRatherThanAnExtraTextLabel() {
        compose.setContent { TwoMemoryTheme {
            JournalEntryCard(TimelineEntryUi("new", "10月4日", "18:30", "小满", null, body = "今天的晚风", unread = true), {})
        } }
        compose.onNodeWithText("新回忆").assertDoesNotExist()
        compose.onNodeWithContentDescription("这封小笺还没看过").assertExists()
        capture("letter-v24")
    }

    @Test fun curvedJournalKeepsVisibleRecordsAndActions() {
        compose.setContent { TwoMemoryTheme {
            TimelineScreen(listOf(
                TimelineEntryUi("one", "2026年10月4日", "18:30", "小满", null, body = "今天沿着江边走了很久，晚风刚刚好。", unread = true),
                TimelineEntryUi("two", "2026年10月4日", "09:15", "阿屿", null, body = "早起买了一束小花，想把这一天留给你。", mine = true)
            ), onCreate = {}, onOpen = {})
        } }
        compose.onNodeWithText("看看这封小笺").assertExists()
        compose.onNodeWithText("今天沿着江边走了很久，晚风刚刚好。").assertExists()
        capture("timeline-v24")
    }

    private fun capture(name: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val folder = java.io.File(context.getExternalFilesDir(null), "visual-qa").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage()
        java.io.File(folder, "$name.png").outputStream().use {
            bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
