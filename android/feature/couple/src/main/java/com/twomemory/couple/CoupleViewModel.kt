package com.twomemory.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.designsystem.MoonLetterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ownName/partnerName are the names the server holds; draftName is what she is
 * typing right now and never leaves the device until she presses 保存.
 */
data class CoupleUiState(
    val ownName: String = "",
    val partnerName: String = "",
    val draftName: String = "",
    val savingName: Boolean = false,
    val nameError: String? = null,
    val ownAvatar: String? = null,
    val theme: MoonLetterTheme = MoonLetterTheme.WARM_BEIGE,
)

class CoupleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(CoupleUiState())
    val state: StateFlow<CoupleUiState> = mutableState.asStateFlow()

    fun updateOwnName(value: String) {
        mutableState.value = mutableState.value.copy(draftName = value, nameError = null)
    }
    fun updateOwnAvatar(uri: String) { mutableState.value = mutableState.value.copy(ownAvatar = uri) }
    fun updateTheme(theme: MoonLetterTheme) { mutableState.value = mutableState.value.copy(theme = theme) }

    fun restore(ownAvatar: String?, theme: MoonLetterTheme) {
        mutableState.value = mutableState.value.copy(ownAvatar = ownAvatar, theme = theme)
    }

    /** Seeds the names fetched from the space; a rename in flight is left alone. */
    fun restoreNames(own: String, partner: String) {
        val current = mutableState.value
        mutableState.value = current.copy(
            ownName = own,
            partnerName = partner,
            draftName = if (current.draftName.isBlank()) own else current.draftName,
        )
    }

    /**
     * Pushes the typed name through [remote], which returns the name the server
     * stored. A rejected or failed save keeps her text and surfaces the reason.
     */
    fun saveOwnName(remote: suspend (String) -> String) {
        val current = mutableState.value
        val candidate = current.draftName.trim()
        if (current.savingName || candidate.isBlank() || candidate == current.ownName) return
        mutableState.value = current.copy(savingName = true, nameError = null)
        viewModelScope.launch {
            try {
                val saved = remote(candidate)
                mutableState.value = mutableState.value.copy(
                    ownName = saved, draftName = saved, savingName = false, nameError = null,
                )
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    savingName = false,
                    nameError = "这个名字还没存到服务器上：${exception.message ?: "网络异常"}",
                )
            }
        }
    }
}
