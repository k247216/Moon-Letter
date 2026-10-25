package com.twomemory.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.EntryMode
import com.twomemory.timeline.TimelineScreen
import com.twomemory.timeline.TimelineEntryUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecordingExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickRecordingDraftsAndUnreadNavigationAreSeparateActions() {
        var action = ""
        val entries = mutableStateOf(listOf(TimelineEntryUi("other", "2026年9月1日", "12:00", "伴侣", null, body = "老日子的新回忆", unread = true)))
        compose.setContent { TwoMemoryTheme {
            TimelineScreen(entries.value, onCreate = { action = "new" }, onOpenDrafts = { action = "drafts" }, onOpen = {
                action = it; entries.value = entries.value.map { record -> record.copy(unread = false) }
            })
        } }
        compose.onNodeWithText("记下今天").performClick(); assertEquals("new", action)
        compose.onNodeWithText("草稿箱").performClick(); assertEquals("drafts", action)
        compose.onNodeWithText("看看这封小笺").performClick(); assertEquals("other", action)
        compose.onNodeWithText("看看这封小笺").assertDoesNotExist()
    }

    @Test fun savedDraftCanBeContinuedOrDeletedWithoutStartingAnUnrelatedRecord() {
        var resumed = ""; var removed = ""
        val items = listOf(SavedDraft("first", EntryMode.PERSONAL, DraftStore.Draft("雨天", "还没写完"), 100))
        compose.setContent { TwoMemoryTheme {
            DraftLibraryScreen(items, onBack = {}, onResume = { resumed = it.id }, onDelete = { removed = it.id })
        } }
        compose.onNodeWithText("仅保存在这台设备，暂不分享给对方").assertExists()
        compose.onNodeWithText("雨天").performClick(); assertEquals("first", resumed)
        compose.onNodeWithContentDescription("删除草稿雨天").performClick()
        compose.onNodeWithText("删除草稿？").assertExists()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick(); assertEquals("first", removed)
    }

    @Test fun realNavigationKeepsTwoDifferentDraftsAndRestoresTheChosenOne() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        EntryMode.entries.forEach { DraftStore.clear(context, it) }
        SyncSession.save(context, "isolated-native-test", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "http://127.0.0.1:1")
        compose.setContent { TwoMemoryTheme { AppNavigation() } }
        compose.onNodeWithContentDescription("新建记录").performClick()
        compose.onNodeWithContentDescription("个人记录正文").performTextInput("第一篇还没写完")
        val session = SyncSession.load(context)!!
        val scope = "${session.baseUrl.trimEnd('/')}|${session.coupleId}|${session.userId}"
        compose.waitUntil(5_000) { DraftLibrary(context, scope).list().any { it.draft.body == "第一篇还没写完" } }
        org.junit.Assert.assertNull("Private draft must not remain in a global cross-account slot", DraftStore.load(context, EntryMode.PERSONAL))
        compose.onNodeWithContentDescription("关闭编辑器").performClick()
        compose.onNodeWithContentDescription("新建记录").performClick()
        compose.onNodeWithContentDescription("个人记录正文").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("个人记录正文").performTextInput("第二篇也先留着")
        compose.onNodeWithContentDescription("关闭编辑器").performClick()
        compose.onNodeWithText("草稿箱").performClick()
        compose.onAllNodesWithText("第一篇还没写完").onFirst().assertExists()
        compose.onAllNodesWithText("第二篇也先留着").onFirst().assertExists()
        compose.onAllNodesWithText("第一篇还没写完").onFirst().performClick()
        compose.onNodeWithContentDescription("个人记录正文").assertTextEquals("第一篇还没写完")
        compose.onNodeWithContentDescription("关闭编辑器").performClick()
        compose.onNodeWithText("草稿箱").performClick()
        compose.onAllNodesWithText("第二篇也先留着").onFirst().assertExists()
    }
}
