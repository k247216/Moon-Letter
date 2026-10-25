package com.twomemory.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import com.twomemory.timeline.EntryBlockUi
import com.twomemory.timeline.EntryCommentUi
import com.twomemory.timeline.EntryDetailScreen
import com.twomemory.timeline.EntryDetailUi
import com.twomemory.timeline.TimelineEntryUi
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedJournalReadingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val own = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val partner = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun phoneReadsSameNameContributionsInOriginalOrderAndCapturesRealPage() =
        verifyReadingPage("shared-reading-phone.png", tablet = false)

    @Test @Config(qualifiers = "w960dp-h720dp-mdpi")
    fun tabletReadsSameNameContributionsInOriginalOrderAndCapturesRealPage() =
        verifyReadingPage("shared-reading-tablet.png", tablet = true)

    @Test fun unknownAuthorAndPartnerCommentDoNotInventASecondSignature() {
        show(page(listOf(text("first", own, "我的正文"),
            EntryBlockUi("legacy", BlockType.TEXT, "来源未知的旧文字", null, null)),
            comments = listOf(EntryCommentUi("comment", "同名", "回应不是正文贡献", "10月1日 08:30", false))))
        compose.onNodeWithText("已留下一个人的视角").assertIsDisplayed()
        compose.onNodeWithText("已留下两个人的视角").assertDoesNotExist()
        compose.onNodeWithContentDescription("共同创作签名：我").assertIsDisplayed()
        compose.onNodeWithContentDescription("共同创作签名：对方").assertDoesNotExist()
    }

    private fun verifyReadingPage(name: String, tablet: Boolean) {
        val bitmap = requireNotNull(BitmapFactory.decodeResource(compose.activity.resources,
            com.twomemory.designsystem.R.drawable.moonletter_cover_puppy))
        val photo = File(compose.activity.filesDir, "shared-reading-$name")
        try { photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val state = page(listOf(
            text("first", own, "晚风很轻，我记住了你停下脚步的那一刻。"),
            EntryBlockUi("photo", BlockType.IMAGE, null, photo.absolutePath, null,
                author = "同名", mine = false, authorId = partner),
            text("second", partner, "我记住的是，你把这只小狗也留在了我们的回忆里。"),
            text("third", own, "于是把平凡的一天，继续写在同一页。"),
        ))
        assertEquals(setOf(own, partner), state.contributorIds)
        show(state)
        compose.onNodeWithText("已留下两个人的视角").assertIsDisplayed()
        val ownSignature = compose.onNodeWithContentDescription("共同创作签名：我")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val partnerSignature = compose.onNodeWithContentDescription("共同创作签名：对方")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(ownSignature.right <= partnerSignature.left)
        compose.onAllNodesWithText("同名的视角").assertCountEquals(3)
        compose.onAllNodesWithText("把晚风写给你").assertCountEquals(1)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("记录里的照片", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        if (tablet) {
            val title = compose.onNodeWithText("把晚风写给你").fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(title.width <= 720f)
            assertTrue(title.left > root.left + 24f)
        }
        capture(name)
        // Fetch every position at the same scroll offset, including offscreen content.
        compose.onNodeWithText(state.blocks.first().text!!).performScrollTo()
        val positions = listOf(
            compose.onNodeWithText(state.blocks[0].text!!).fetchSemanticsNode().positionInRoot.y,
            compose.onNodeWithContentDescription("查看第1张图片").fetchSemanticsNode().positionInRoot.y,
            compose.onNodeWithText(state.blocks[2].text!!).fetchSemanticsNode().positionInRoot.y,
            compose.onNodeWithText(state.blocks[3].text!!).fetchSemanticsNode().positionInRoot.y,
        )
        positions.zipWithNext().forEach { (before, after) -> assertTrue(before < after) }
        state.blocks.filter { it.type == BlockType.TEXT }.forEach {
            compose.onNodeWithText(it.text!!).assert(!hasSetTextAction())
        }
        listOf("双方已确认", "对方正在输入", "对方在线").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    private fun text(id: String, authorId: UUID, body: String) = EntryBlockUi(id, BlockType.TEXT, body,
        null, null, author = "同名", mine = authorId == own, authorId = authorId)

    private fun page(blocks: List<EntryBlockUi>, comments: List<EntryCommentUi> = emptyList()) = EntryDetailUi(
        header = TimelineEntryUi("shared-entry", "2026年10月1日", "07:30", "同名", "把晚风写给你",
            shared = true, mine = true), blocks = blocks, comments = comments)

    private fun show(state: EntryDetailUi) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            EntryDetailScreen(state, onBack = {}, onDraftChange = {}, onSend = {}, onAddPerspective = {})
        } } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File("../../releases/v35/$name")
        check(output.parentFile!!.isDirectory || output.parentFile!!.mkdirs())
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
