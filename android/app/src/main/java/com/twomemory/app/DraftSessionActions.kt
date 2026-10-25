package com.twomemory.app

import com.twomemory.editor.EditorUiState

internal fun finishOwnedDraft(latest: () -> EditorUiState?, persist: (EditorUiState) -> Unit,
    finish: () -> Unit): Result<Boolean> = runCatching {
    val current = latest() ?: return@runCatching false
    if (current.saved) return@runCatching false
    persist(current)
    if (latest() == null) return@runCatching false
    finish()
    true
}
