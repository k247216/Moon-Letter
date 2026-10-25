package com.twomemory.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.EntryMode
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DraftShareHandoffTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun sharingInTheSameEventPreservesTheLastOldKeystrokeInADifferentSlot() {
        val context=compose.activity
        SyncSession.save(context,"synthetic-handoff",UUID.randomUUID(),UUID.randomUUID(),"http://127.0.0.1:9")
        val session=requireNotNull(SyncSession.load(context))
        val library=DraftLibrary(context,"${session.storageOrigin}|${session.coupleId}|${session.userId}")
        val request=mutableStateOf(0)
        compose.activity.runOnUiThread {compose.activity.setContent {TwoMemoryTheme {
            AppNavigation(initialEditorMode=EntryMode.PERSONAL.name,initialEditorRequest=request.value)
        } } }
        compose.onNodeWithContentDescription("新建记录").performClick()
        DraftStore.save(incomingShareDraftContext(context),EntryMode.PERSONAL,"新导入的记录","从分享入口来的内容")
        val replace=compose.onNodeWithContentDescription("个人记录正文").fetchSemanticsNode().config[SemanticsActions.SetText].action!!
        compose.runOnIdle {
            assertTrue(replace(AnnotatedString("还没等自动保存的最后一句")))
            request.value=1
        }
        compose.onNodeWithText("从分享入口来的内容").assertIsDisplayed()
        compose.runOnIdle {
            val drafts=library.list()
            assertTrue("Old slot must keep its very last input before reset",drafts.any {it.draft.body=="还没等自动保存的最后一句"})
            assertTrue(drafts.any {it.draft.body=="从分享入口来的内容"})
            assertEquals(2,drafts.map {it.id}.distinct().size)
        }
    }
}
