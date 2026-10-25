package com.twomemory.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.TimelinePhoto
import com.twomemory.timeline.TimelineEntryUi
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoryBookmarksDialogTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val saved = entry("一起看月亮", "2026年10月13日")
    private val unmarked = entry("没有加入书签的记录", "2026年10月12日")
    private val invisibleId = UUID.randomUUID().toString()

    @Test fun capturesPaperBookmarksPhone() = capturePaper("bookmarks-phone.png")

    @Test @Config(qualifiers = "w800dp-h1100dp-mdpi")
    fun capturesPaperBookmarksTablet() = capturePaper("bookmarks-tablet.png")

    @Test @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun capturesPaperBookmarksLandscape() = capturePaper("bookmarks-landscape.png")

    private fun capturePaper(name: String) {
        val memory = saved.copy(body = "月亮替我们记住了，那天一起走过的小路。")
        show(listOf(memory, unmarked.copy(body = "把晚风留在这里，改天再一起翻一翻。")), setOf(memory.id, unmarked.id))
        compose.onNodeWithText(memory.title!!).assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        compose.onNodeWithContentDescription("移除书签：${memory.title}").assertIsDisplayed()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("../../releases/v43").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun paperBookmarkShowsBodyExcerptWithoutAnExtraRemovalLabel() {
        val memory = saved.copy(body = "那天晚风很轻，我们一起走了很远。")
        show(listOf(memory), setOf(memory.id))
        compose.onNodeWithText(memory.body).assertIsDisplayed()
        compose.onNodeWithText("移除").assertDoesNotExist()
        compose.onNodeWithContentDescription("移除书签：${saved.title}").performClick()
    }

    @Test fun longBodyStaysAnExcerptAndOpeningKeepsOriginalIdentity() {
        var opened: String? = null
        val body = "留在这一页的晚风。".repeat(40)
        val memory = saved.copy(body = body)
        show(listOf(memory), setOf(memory.id), onOpen = { opened = it })
        compose.onNodeWithText(body.take(90)).assertExists()
        compose.onNodeWithText(saved.title!!).performClick()
        compose.runOnIdle { assertEquals(saved.id, opened) }
    }

    @Test fun showsOnlyBookmarkedVisibleRecordsWithTitleAndDate() {
        show(listOf(saved, unmarked), setOf(saved.id, invisibleId))
        compose.onNodeWithText(saved.title!!).assertIsDisplayed()
        compose.onNodeWithText(saved.dateLabel).assertIsDisplayed()
        compose.onNodeWithText(unmarked.title!!).assertDoesNotExist()
    }

    @Test fun bookmarkOpensOriginalDetailRatherThanCreatingACopy() {
        var opened: String? = null
        show(listOf(saved), setOf(saved.id), onOpen = { opened = it })
        compose.onNodeWithText(saved.title!!).performClick()
        compose.runOnIdle { assertEquals(saved.id, opened) }
    }

    @Test fun removingBookmarkUpdatesDialogWithoutRemovingVisibleRecord() {
        val visible = mutableStateOf(listOf(saved))
        val ids = mutableStateOf(setOf(saved.id, invisibleId))
        var removed: String? = null
        var opened = false
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryBookmarksDialog(visible.value, ids.value, { opened = true }, { id ->
                removed = id
                ids.value = ids.value - id
            }, {})
        } } }
        compose.onNodeWithContentDescription("移除书签：${saved.title}").performClick()
        compose.onNodeWithText(saved.title!!).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(saved.id, removed)
            assertFalse("Removing a marker must not open the record", opened)
            assertEquals(listOf(saved), visible.value)
            assertEquals(setOf(invisibleId), ids.value)
        }
    }

    @Test fun temporarilyInvisibleBookmarkReturnsWhenRecordBecomesVisibleAgain() {
        val visible = mutableStateOf(emptyList<TimelineEntryUi>())
        val ids = setOf(saved.id)
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryBookmarksDialog(visible.value, ids, {}, {}, {})
        } } }
        compose.onNodeWithText("还没有可见的书签记录").assertIsDisplayed()
        compose.runOnIdle { visible.value = listOf(saved) }
        compose.onNodeWithText(saved.title!!).assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf(saved.id), ids) }
    }

    @Test fun closeButtonDismissesWithoutOpeningOrRemovingRecords() {
        var dismissed = false
        var opened = false
        var removed = false
        show(listOf(saved), setOf(saved.id), { opened = true }, { removed = true }, { dismissed = true })
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(opened); assertFalse(removed) }
    }

    @Test fun photoBookmarkUsesSmallRealEntryPhotoWithoutAlteringItsOriginal() {
        val original = File(compose.activity.cacheDir, "bookmark-test-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.RED)
            original.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val before = original.readBytes()
            val photo = saved.copy(photo = TimelinePhoto(original.absolutePath, null))
            show(listOf(photo), setOf(photo.id))
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("书签照片：${saved.title}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("书签照片：${saved.title}").assertIsDisplayed()
            val bounds = compose.onNodeWithContentDescription("书签照片：${saved.title}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            // This fixture explicitly runs at mdpi, so 48dp is 48 pixels.
            assertTrue("Bookmark preview height must stay capped", bounds.height <= 48f)
            assertEquals("Small preview must preserve the 80:60 original aspect ratio",
                bounds.width * .75f, bounds.height, 1f)
            assertArrayEquals(before, original.readBytes())
        } finally {
            compose.activity.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            bitmap.recycle()
            original.delete()
        }
    }

    private fun show(entries: List<TimelineEntryUi>, ids: Set<String>, onOpen: (String) -> Unit = {},
                     onRemove: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            MemoryBookmarksDialog(entries, ids, onOpen, onRemove, onDismiss)
        } } }
    }

    private fun entry(title: String, date: String) = TimelineEntryUi(
        id = UUID.randomUUID().toString(), dateLabel = date, timeLabel = "20:18", author = "我", title = title)
}
