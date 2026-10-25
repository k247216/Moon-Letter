package com.twomemory.editor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class EditorPublicationRaceTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun oldPublicationCompletionCannotMarkNewEditorSaved() {
        val model = EditorViewModel(); val held = CompletableDeferred<Unit>()
        model.updateBody("A")
        model.publish { held.await() }
        model.reset(); model.updateBody("B")
        held.complete(Unit)
        assertEquals("B", model.state.value.body)
        assertFalse(model.state.value.saved)
    }

    @Test fun editsDuringPublicationRemainAnUnpublishedDraft() {
        val model = EditorViewModel(); val held = CompletableDeferred<Unit>()
        var submitted = ""
        model.updateBody("提交版")
        model.publish { state -> submitted = state.body; held.await() }
        model.updateBody("新增的内容")
        held.complete(Unit)
        assertEquals("提交版", submitted)
        assertEquals("新增的内容", model.state.value.body)
        assertFalse(model.state.value.saved)
        assertFalse(model.state.value.saving)
    }
}
