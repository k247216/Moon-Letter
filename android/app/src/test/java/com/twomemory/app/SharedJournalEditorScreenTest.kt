package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorReferenceBlock
import com.twomemory.editor.EditorUiState
import com.twomemory.editor.SharedEditorRoute
import com.twomemory.model.BlockType
import java.time.Instant
import java.util.UUID
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
class SharedJournalEditorScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val own = UUID.randomUUID()
    private val partner = UUID.randomUUID()

    @Test fun withdrawingLastLiveReferenceDoesNotResurrectCachedBodiesOrLoseOwnDraft() {
        val references = mutableStateOf(listOf(EditorReferenceBlock("published", partner, BlockType.TEXT,
            0, text = "当前可见的正文")))
        val draft = mutableStateOf("我的未发送正文")
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            SharedEditorRoute(
                state = EditorUiState(title = "共同回忆", body = draft.value, appendingPerspective = true,
                    partnerBody = "缓存的对方旧正文", ownPublishedBody = "缓存的本人旧正文"),
                onPublish = {}, onBodyChange = { draft.value = it }, ownName = "我", partnerName = "TA",
                currentUserId = own, referenceBlocks = references.value,
            )
        } } }
        compose.onNodeWithText("当前可见的正文").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("缓存的对方旧正文").assertDoesNotExist()
        compose.onNodeWithText("缓存的本人旧正文").assertDoesNotExist()
        compose.onNodeWithContentDescription("共同记录补写正文").performScrollTo()
            .performTextReplacement("我的补写仍未发送")
        compose.runOnIdle { references.value = emptyList() }
        compose.onNodeWithText("当前可见的正文").assertDoesNotExist()
        compose.onNodeWithText("缓存的对方旧正文").assertDoesNotExist()
        compose.onNodeWithText("缓存的本人旧正文").assertDoesNotExist()
        compose.onNodeWithContentDescription("共同记录补写正文").assert(hasSetTextAction())
        compose.onNodeWithText("我的补写仍未发送").assertExists()
    }

    @Test fun omittedReferenceApiStillDisplaysLegacyReadOnlyContext() {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            SharedEditorRoute(EditorUiState(title = "旧调用", appendingPerspective = true,
                partnerBody = "旧调用的对方正文", ownPublishedBody = "旧调用的本人正文"),
                onPublish = {})
        } } }
        compose.onNodeWithText("旧调用的对方正文").assertExists()
        compose.onNodeWithText("旧调用的本人正文").assertExists()
    }

    @Test fun actualContributorIdsKeepTwoSignaturesEvenWhenNamesMatch() {
        show(contributors = setOf(own, partner))
        compose.onNodeWithText("已留下两个人的视角").assertIsDisplayed()
        compose.onNodeWithContentDescription("共同创作签名：我").assertIsDisplayed()
        compose.onNodeWithContentDescription("共同创作签名：对方").assertIsDisplayed()
    }

    @Test fun namesAndUnsubmittedBodyCannotInventASecondContributor() {
        show(contributors = setOf(own))
        compose.onNodeWithText("已留下一个人的视角").assertIsDisplayed()
        compose.onNodeWithText("已留下两个人的视角").assertDoesNotExist()
        compose.onNodeWithContentDescription("共同记录补写正文").assert(hasSetTextAction())
        compose.onNodeWithText("我的未发送正文").assertExists()
    }

    @Test fun partnerImageReferenceIsVisibleReadOnlyAndDoesNotBecomeAnEditableAttachment() {
        val imageId = UUID.randomUUID().toString()
        show(contributors = setOf(partner), references = listOf(EditorReferenceBlock(
            id = imageId, authorId = partner, type = BlockType.IMAGE, orderKey = 0,
        )))
        compose.onNodeWithContentDescription("共同记录参考块：$imageId").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("共同记录参考块：$imageId").assert(!hasSetTextAction())
        compose.onNodeWithContentDescription("移除图片").assertDoesNotExist()
        compose.onNodeWithText("已留下一个人的视角").assertExists()
    }

    @Test fun referencesRetainCrossAuthorOrderAndSharedTitleStaysReadOnly() {
        val first = EditorReferenceBlock(UUID.randomUUID().toString(), own, BlockType.TEXT, 0, text = "我的原文")
        val second = EditorReferenceBlock(UUID.randomUUID().toString(), partner, BlockType.TEXT, 1, text = "TA 的原文")
        val third = EditorReferenceBlock(UUID.randomUUID().toString(), own, BlockType.TEXT, 2, text = "我的后续原文")
        show(setOf(own, partner), listOf(third, first, second))
        compose.onAllNodesWithText("共同回忆").assertCountEquals(1)
        compose.onNodeWithContentDescription("记录标题").assert(!hasSetTextAction())
        compose.onNodeWithText("我的原文").performScrollTo().assert(!hasSetTextAction())
        val positions = listOf(first, second, third).map {
            compose.onNodeWithContentDescription("共同记录参考块：${it.id}").fetchSemanticsNode().positionInRoot.y
        }
        positions.zipWithNext().forEach { (before, after) -> assertTrue(before < after) }
        compose.onNodeWithContentDescription("共同记录补写正文").assert(hasSetTextAction())
    }

    private fun show(contributors: Set<UUID>, references: List<EditorReferenceBlock> = emptyList()) {
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            SharedEditorRoute(
                state = EditorUiState(title = "共同回忆", body = "我的未发送正文", appendingPerspective = true,
                    occurrenceTime = Instant.parse("2026-09-30T12:18:00Z"), timezone = "Asia/Shanghai"),
                onPublish = {}, ownName = "同名", partnerName = "同名", currentUserId = own,
                contributorIds = contributors, referenceBlocks = references,
            )
        } } }
    }
}
