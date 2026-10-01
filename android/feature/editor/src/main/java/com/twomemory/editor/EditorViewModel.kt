package com.twomemory.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.model.LocalEntryCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class EditorUiState(
    val title: String = "",
    val body: String = "",
    val occurrenceTime: Instant = Instant.now(),
    val timezone: String = "Asia/Shanghai",
    val authors: List<String> = listOf("我"),
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Set when persisting the entry failed; the screen shows it inline. */
    val error: String? = null,
) {
    val hasContent: Boolean get() = title.isNotBlank() || body.isNotBlank()
}

class EditorViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    fun updateBody(body: String) {
        mutableState.value = mutableState.value.copy(body = body, saved = false, error = null)
    }

    fun updateTitle(title: String) {
        mutableState.value = mutableState.value.copy(title = title, saved = false, error = null)
    }

    /** Restores an interrupted session's text so a killed process loses nothing. */
    fun restore(savedTitle: String, savedBody: String) {
        if (mutableState.value.hasContent) return
        if (savedTitle.isBlank() && savedBody.isBlank()) return
        mutableState.value = mutableState.value.copy(title = savedTitle, body = savedBody)
    }

    /** Clears a completed editing session before a fresh one begins. */
    fun reset() {
        mutableState.value = EditorUiState()
    }

    /**
     * Persisting runs in the caller-supplied suspend block; failures land in
     * [EditorUiState.error] instead of crashing the process, so the text
     * stays on screen and can be retried.
     */
    fun publish(save: suspend (EditorUiState) -> Unit) {
        val current = mutableState.value
        if (current.saving || !current.hasContent) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true, error = null)
            try {
                save(mutableState.value)
                mutableState.value = mutableState.value.copy(saving = false, saved = true)
            } catch (expected: Exception) {
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    saved = false,
                    error = expected.message ?: "保存失败，请重试",
                )
            }
        }
    }
}
