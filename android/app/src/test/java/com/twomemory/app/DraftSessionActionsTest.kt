package com.twomemory.app

import com.twomemory.editor.EditorUiState
import org.junit.Assert.*
import org.junit.Test

class DraftSessionActionsTest {
    @Test fun aRetainedOldActionCannotWriteTheNewPagesBodyOrCloseItsEditor() {
        var written: String? = null; var closed = false
        val result = finishOwnedDraft({ null }, { written = it.body }, { closed = true })
        assertEquals(false, result.getOrThrow()); assertNull(written); assertFalse(closed)
    }
    @Test fun manualFinishPersistsTheActualOwnedStateBeforeClosing() {
        var written: String? = null; var closed = false
        val result = finishOwnedDraft({ EditorUiState(body = "最新正文") }, { written = it.body }, {
            assertEquals("最新正文", written); closed = true
        })
        assertTrue(result.getOrThrow()); assertTrue(closed)
    }
    @Test fun failedAutosaveIsReportedAndDoesNotCloseThePage() {
        var closed = false
        val result = finishOwnedDraft({ EditorUiState(body = "仍在编辑") }, { error("synthetic disk failure") }, { closed = true })
        assertTrue(result.isFailure); assertFalse(closed)
    }
    @Test fun changingOwnerDuringPersistDoesNotFinishTheNewerPage() {
        var own = true; var closed = false
        val result = finishOwnedDraft({ if (own) EditorUiState(body = "旧页") else null }, { own = false }, { closed = true })
        assertFalse(result.getOrThrow()); assertFalse(closed)
    }
}
