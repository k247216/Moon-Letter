package com.twomemory.couple

import androidx.lifecycle.ViewModel
import com.twomemory.designsystem.MoonLetterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CoupleUiState(
    val ownName: String = "小满",
    val partnerName: String = "阿屿",
    val ownAvatar: String? = null,
    val partnerAvatar: String? = null,
    val theme: MoonLetterTheme = MoonLetterTheme.WARM_BEIGE,
    val anniversaryLabel: String = "中秋节 · 农历八月十五",
    val coverUri: String? = null,
)

class CoupleViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(CoupleUiState())
    val state: StateFlow<CoupleUiState> = mutableState.asStateFlow()

    fun updateOwnName(value: String) { mutableState.value = mutableState.value.copy(ownName = value) }
    fun updateOwnAvatar(uri: String) { mutableState.value = mutableState.value.copy(ownAvatar = uri) }
    fun updateTheme(theme: MoonLetterTheme) { mutableState.value = mutableState.value.copy(theme = theme) }
    fun restore(ownName: String, ownAvatar: String?, theme: MoonLetterTheme) {
        mutableState.value = mutableState.value.copy(ownName = ownName, ownAvatar = ownAvatar, theme = theme)
    }
    fun keepOldCoverUntilUploadSuccess(newUri: String?, uploadSucceeded: Boolean) {
        if (uploadSucceeded) mutableState.value = mutableState.value.copy(coverUri = newUri)
    }
}
