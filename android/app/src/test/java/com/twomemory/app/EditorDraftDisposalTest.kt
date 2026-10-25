package com.twomemory.app

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.twomemory.editor.EditorViewModel
import com.twomemory.model.EntryMode
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class EditorDraftDisposalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun accountDisposalKeepsTheLastKeystrokeInCapturedOldSlotWithoutWaitingForDebounce() {
        val context=compose.activity
        val space=UUID.randomUUID(); val a=UUID.randomUUID(); val b=UUID.randomUUID(); val entry=UUID.randomUUID()
        val viewModels=mutableMapOf<UUID,EditorViewModel>()
        SyncSession.save(context,"a",space,a,"http://127.0.0.1:9")
        val oldSlot=perspectiveDraftContext(context,entry)
        compose.activity.runOnUiThread { compose.activity.setContent { SessionScopedContent {
            val user=remember {SyncSession.load(context)!!.userId}
            val slot=remember {perspectiveDraftContext(context,entry)}
            val vm=remember {EditorViewModel().also {viewModels[user]=it}}
            val state by vm.state.collectAsState()
            EditorDraftGuard(state,user.toString(),{vm.state.value}) { draft ->
                DraftStore.save(slot,EntryMode.COLLABORATIVE,draft.title,draft.body,draft.photos,draft.attachments,
                    draft.occurrenceTime.takeIf {draft.occurrenceEdited},draft.visibility)
            }
            Text(user.toString())
        } } }
        compose.runOnIdle {
            viewModels.getValue(a).updateBody("最后刚写下的一句话")
            SyncSession.save(context,"b",space,b,"http://127.0.0.1:9")
        }
        compose.waitForIdle()
        assertEquals("最后刚写下的一句话",DraftStore.load(oldSlot,EntryMode.COLLABORATIVE)?.body)
        assertNull(DraftStore.load(perspectiveDraftContext(context,entry),EntryMode.COLLABORATIVE))
        compose.runOnIdle {SyncSession.save(context,"a",space,a,"http://127.0.0.1:9")}
        compose.waitForIdle()
        assertEquals("最后刚写下的一句话",DraftStore.load(perspectiveDraftContext(context,entry),EntryMode.COLLABORATIVE)?.body)
    }
}
