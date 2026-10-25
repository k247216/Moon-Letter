package com.twomemory.app

import androidx.compose.runtime.*
import com.twomemory.editor.EditorUiState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Flush the captured owner's input before disposal cancels the debounce job. */
@Composable
internal fun EditorDraftGuard(state: EditorUiState, ownerKey: String,
    latest: () -> EditorUiState?, persist: (EditorUiState) -> Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    // These callbacks belong to ownerKey; do not replace them with a later
    // identity's callbacks during the old owner's onDispose.
    DisposableEffect(lifecycle,ownerKey) {
        fun flush() { latest()?.takeUnless {it.saved}?.let(persist) }
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) flush()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer); flush()}
    }
    LaunchedEffect(ownerKey,state) {
        if(!state.saved && !state.saving) {delay(300); latest()?.takeUnless {it.saved || it.saving}?.let(persist)}
    }
}
