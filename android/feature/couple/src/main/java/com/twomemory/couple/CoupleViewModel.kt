package com.twomemory.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.designsystem.MoonLetterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A pairing code the server just issued, and the slot it opens. */
data class PairingCode(val kind: String, val token: String)

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
    val pairingCode: String? = null,
    val pairingCodeKind: String? = null,
    val generatingCode: Boolean = false,
    val codeError: String? = null,
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

    /**
     * Asks [remote] for a fresh pairing code. The server retires the outstanding
     * code as soon as a new one is minted, so a failed mint drops the code that
     * was on screen too: keeping it would show a token that can no longer pair.
     */
    fun generatePairingCode(remote: suspend () -> PairingCode) {
        if (mutableState.value.generatingCode) return
        mutableState.value = mutableState.value.copy(generatingCode = true, codeError = null)
        viewModelScope.launch {
            try {
                val code = remote()
                mutableState.value = mutableState.value.copy(
                    generatingCode = false,
                    pairingCode = code.token,
                    pairingCodeKind = code.kind,
                    codeError = null,
                )
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    generatingCode = false,
                    pairingCode = null,
                    pairingCodeKind = null,
                    codeError = "配对码没拿到：${exception.message ?: "网络异常"}",
                )
            }
        }
    }

    /** Local-only: the code stays valid on the server until a new one replaces it. */
    fun hidePairingCode() {
        mutableState.value = mutableState.value.copy(pairingCode = null, pairingCodeKind = null, codeError = null)
    }
}
