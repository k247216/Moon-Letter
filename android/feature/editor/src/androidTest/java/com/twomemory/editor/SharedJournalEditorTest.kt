package com.twomemory.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SharedJournalEditorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun narrowSharedEditorKeepsBothRealRolesDistinctEvenWithIdenticalDisplayNames() {
        rule.setContent {
            TwoMemoryTheme { Box(Modifier.width(320.dp)) {
                SharedEditorRoute(EditorUiState(title = "月亮升起来的时候"), onPublish = {},
                    ownName = "小满", partnerName = "小满")
            } }
        }
        val own = rule.onNodeWithContentDescription("共同创作签名：我").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val partner = rule.onNodeWithContentDescription("共同创作签名：对方").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(own.right <= partner.left)
        rule.onNodeWithText("已留下两个人的视角").assertDoesNotExist()
    }

    @Test fun appendingKeepsOneSharedTitleAndPartnerContextReadOnlyWhileOwnDraftRemainsEditable() {
        rule.setContent {
            TwoMemoryTheme { SharedEditorRoute(
                EditorUiState(title = "共同回忆", body = "我的未发送正文", appendingPerspective = true,
                    occurrenceTime = Instant.parse("2026-09-30T12:18:00Z"), timezone = "Asia/Shanghai",
                    partnerBody = "TA 原来的正文", ownPublishedBody = "我原来的正文"),
                onPublish = {}, ownName = "小满", partnerName = "阿屿",
            ) }
        }
        rule.onAllNodesWithText("共同回忆").assertCountEquals(1)
        rule.onNodeWithContentDescription("记录标题").assert(!hasSetTextAction())
        rule.onNodeWithText("TA 原来的正文").assert(!hasSetTextAction())
        rule.onNodeWithText("我原来的正文").assert(!hasSetTextAction())
        rule.onNodeWithContentDescription("共同记录补写正文").assert(hasSetTextAction())
        rule.onNodeWithText("我的未发送正文").assertExists()
    }

    @Test fun namesAndAnUnsubmittedDraftNeverClaimPartnerHasContributedOrIsOnline() {
        rule.setContent {
            TwoMemoryTheme { SharedEditorRoute(EditorUiState(body = "还没发送的一笔"), onPublish = {},
                ownName = "小满", partnerName = "阿屿") }
        }
        rule.onNodeWithText("已留下两个人的视角").assertDoesNotExist()
        rule.onNodeWithText("已留下视角").assertDoesNotExist()
        listOf("双方已确认", "对方正在输入", "对方在线").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }
}
