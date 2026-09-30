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
)

class EditorViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    fun updateBody(body: String) {
        mutableState.value = mutableState.value.copy(body = body, saved = false)
    }

    fun updateTitle(title: String) {
        mutableState.value = mutableState.value.copy(title = title, saved = false)
    }

    fun publish(save: suspend (EditorUiState) -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true)
            save(mutableState.value)
            mutableState.value = mutableState.value.copy(saving = false, saved = true)
        }
    }
}
