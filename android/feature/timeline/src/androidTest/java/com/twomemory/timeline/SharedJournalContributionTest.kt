package com.twomemory.timeline

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedJournalContributionTest {
    @get:Rule val rule = createComposeRule()
    private val own = UUID.randomUUID()
    private val partner = UUID.randomUUID()

    @Test fun twoActualAuthorsRemainTwoContributorsWhenTheirNamesAreIdentical() {
        render(listOf(block(0, own, "我的开头"), block(1, partner, "TA 的补写")), ownName = "小满", partnerName = "小满")
        rule.onNodeWithText("已留下两个人的视角").assertIsDisplayed()
        rule.onNodeWithContentDescription("共同创作签名：我").assertIsDisplayed()
        rule.onNodeWithContentDescription("共同创作签名：对方").assertIsDisplayed()
        rule.onAllNodesWithText("小满的视角").assertCountEquals(2)
    }

    @Test fun identityChangesSignContinuousRunsWithoutRegroupingTheReadingOrder() {
        render(listOf(block(3, own, "第四段"), block(1, own, "第二段"),
            block(2, partner, "第三段"), block(0, own, "第一段")), ownName = "同名", partnerName = "同名")
        rule.onAllNodesWithText("同名的视角").assertCountEquals(3)
        // Read all positions at the same scroll offset. positionInRoot keeps
        // offscreen coordinates, unlike clipped bounds that can become empty.
        rule.onNodeWithText("第一段").performScrollTo()
        val positions = listOf("第一段", "第二段", "第三段", "第四段").map {
            rule.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y
        }
        positions.zipWithNext().forEach { (before, after) -> assertTrue(before < after) }
    }

    @Test fun severalBlocksFromOneAuthorAndPartnerCommentDoNotClaimTwoContributors() {
        render(listOf(block(0, own, "第一笔"), block(1, own, "第二笔")),
            comments = listOf(EntryComment(UUID.randomUUID(), UUID.randomUUID(), partner, "回应不算正文贡献", Instant.now())))
        rule.onNodeWithText("已留下一个人的视角").assertIsDisplayed()
        rule.onNodeWithText("已留下两个人的视角").assertDoesNotExist()
    }

    @Test fun unknownBlockAuthorDoesNotInventPartnerContribution() {
        render(listOf(block(0, own, "我的正文"), block(1, null, "历史来源未知")))
        rule.onNodeWithText("已留下两个人的视角").assertDoesNotExist()
        rule.onNodeWithContentDescription("共同创作签名：对方").assertDoesNotExist()
    }

    @Test fun imageOnlyContributionCountsFromAuthorIdEvenWithoutATextBody() {
        render(listOf(block(0, own, "我写文字"),
            EntryBlock(UUID.randomUUID(), BlockType.IMAGE, 1, authorId = partner)))
        rule.onNodeWithText("已留下两个人的视角").assertIsDisplayed()
        rule.onNodeWithContentDescription("共同创作签名：对方").assertIsDisplayed()
    }

    @Test fun draftKeepsUnpublishedActionsAndNeverClaimsMutualConfirmation() {
        render(listOf(block(0, own, "尚未发布")), state = EntryState.DRAFT)
        rule.onNodeWithText("补写我的视角").assertDoesNotExist()
        rule.onNodeWithText("发送").assertDoesNotExist()
        listOf("双方已确认", "对方正在输入", "对方在线").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }

    private fun block(order: Long, author: UUID?, text: String) =
        EntryBlock(UUID.randomUUID(), BlockType.TEXT, order, text = text, authorId = author)

    private fun render(blocks: List<EntryBlock>, ownName: String = "我", partnerName: String = "对方",
        comments: List<EntryComment> = emptyList(), state: EntryState = EntryState.PUBLISHED) {
        val entryId = UUID.randomUUID()
        val detail = EntryDetail(TimelineItem(entryId, UUID.randomUUID(), EntryMode.COLLABORATIVE, state,
            Instant.parse("2026-09-30T12:18:00Z"), "Asia/Shanghai", "月亮升起来的时候", own),
            blocks, comments.map { it.copy(entryId = entryId) })
        rule.setContent {
            val vm = remember { EntryDetailViewModel({ MutableStateFlow(detail) }, { _, _ -> }, own).apply {
                updateNames(ownName, partnerName)
            } }
            TwoMemoryTheme { EntryDetailRoute(vm, onBack = {}, onAddPerspective = {}) }
        }
    }
}
