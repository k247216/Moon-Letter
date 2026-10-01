package com.twomemory.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.designsystem.PaperSurface
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.network.SessionApi
import com.twomemory.network.SetupHttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One-time device binding: the first device bootstraps the installation,
 * the partner device pairs with the one-time token. Runs before any diary
 * surface; a session must exist for writes or sync to work.
 */
class SetupViewModel(private val api: SessionApi = RetrofitSessionApi.create()) : ViewModel() {

    enum class Mode { FIRST_DEVICE, PARTNER_DEVICE }

    enum class Phase { FORM, WAITING_PARTNER, BOUND }

    /** A session the server has issued; the screen persists it via [SyncSession]. */
    data class BoundSession(
        val baseUrl: String,
        val token: String,
        val coupleId: UUID,
        val userId: UUID,
    )

    data class UiState(
        val mode: Mode = Mode.FIRST_DEVICE,
        val phase: Phase = Phase.FORM,
        val serverUrl: String = SyncSession.DEFAULT_BASE_URL,
        val bootstrapSecret: String = "",
        val displayName: String = "",
        val pairingTokenInput: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val shownPairingToken: String? = null,
        val boundSession: BoundSession? = null,
        val sessionSaved: Boolean = false,
    ) {
        val canSubmit: Boolean
            get() = !busy && serverUrl.isNotBlank() && when (mode) {
                Mode.FIRST_DEVICE -> bootstrapSecret.isNotBlank() && displayName.isNotBlank()
                Mode.PARTNER_DEVICE -> pairingTokenInput.isNotBlank()
            }
    }

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    fun updateMode(mode: Mode) = mutate { it.copy(mode = mode, error = null) }
    fun updateServerUrl(value: String) = mutate { it.copy(serverUrl = value.trim()) }
    fun updateBootstrapSecret(value: String) = mutate { it.copy(bootstrapSecret = value.trim()) }
    fun updateDisplayName(value: String) = mutate { it.copy(displayName = value.trim()) }
    fun updatePairingToken(value: String) = mutate { it.copy(pairingTokenInput = value.trim()) }

    /** Screen persists [UiState.boundSession]; acknowledge so the flow can continue. */
    fun markSessionSaved() = mutate { it.copy(sessionSaved = true) }

    /** First device: bootstrap, surface the session, then show the pairing token. */
    fun bootstrap() {
        val s = mutableState.value
        if (!s.canSubmit) return
        mutate { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = api.bootstrap(s.serverUrl, s.bootstrapSecret, s.displayName)
                val tokenInfo = api.pairingToken(s.serverUrl, result.token, result.coupleId)
                mutate {
                    it.copy(
                        busy = false,
                        phase = Phase.WAITING_PARTNER,
                        shownPairingToken = tokenInfo.pairingToken,
                        boundSession = BoundSession(s.serverUrl, result.token, result.coupleId, result.userId),
                    )
                }
            } catch (e: SetupHttpException) {
                val reason = when (e.code) {
                    403 -> "初始化密钥不对，请核对后重试"
                    409 -> "这个空间已经初始化过了：请在另一台手机上改用「我是伴侣」+ 配对令牌进入"
                    else -> "服务器返回 HTTP ${e.code}"
                }
                mutate { it.copy(busy = false, error = reason) }
            } catch (e: Exception) {
                mutate { it.copy(busy = false, error = "连不上服务器：${e.message ?: "网络错误"}") }
            }
        }
    }

    /** Partner device: pair with the one-time token and surface the session. */
    fun pair() {
        val s = mutableState.value
        if (!s.canSubmit) return
        mutate { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = api.pair(s.serverUrl, s.pairingTokenInput)
                mutate {
                    it.copy(
                        busy = false,
                        phase = Phase.BOUND,
                        boundSession = BoundSession(s.serverUrl, result.deviceToken, result.couple.id, result.userId),
                    )
                }
            } catch (e: SetupHttpException) {
                val reason = when (e.code) {
                    409 -> "配对令牌已用过或已过期：请在第一台手机上重新获取"
                    403 -> "配对令牌无效，请核对后重试"
                    else -> "服务器返回 HTTP ${e.code}"
                }
                mutate { it.copy(busy = false, error = reason) }
            } catch (e: Exception) {
                mutate { it.copy(busy = false, error = "连不上服务器：${e.message ?: "网络错误"}") }
            }
        }
    }

    private fun mutate(reducer: (UiState) -> UiState) {
        mutableState.value = reducer(mutableState.value)
    }
}

@Composable
fun SetupScreen(viewModel: SetupViewModel = remember { SetupViewModel() }, onBound: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Persist the issued session exactly once, then proceed when bound.
    LaunchedEffect(state.boundSession, state.sessionSaved) {
        val session = state.boundSession
        if (session != null && !state.sessionSaved) {
            SyncSession.save(context, session.token, session.coupleId, session.userId, session.baseUrl)
            viewModel.markSessionSaved()
        }
    }
    LaunchedEffect(state.phase, state.sessionSaved) {
        if (state.phase == SetupViewModel.Phase.BOUND && state.sessionSaved) {
            onBound()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("月笺", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "先把这台设备绑到你们的空间",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PaperSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.phase == SetupViewModel.Phase.WAITING_PARTNER) {
                    Text("空间已建好", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "把下面这段配对令牌发到伴侣设备，在对方手机的「我是伴侣」里粘贴。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            state.shownPairingToken.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(onClick = onBound, modifier = Modifier.fillMaxWidth()) {
                        Text("进入月笺")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == SetupViewModel.Mode.FIRST_DEVICE,
                            onClick = { viewModel.updateMode(SetupViewModel.Mode.FIRST_DEVICE) },
                            label = { Text("我是第一位") },
                        )
                        FilterChip(
                            selected = state.mode == SetupViewModel.Mode.PARTNER_DEVICE,
                            onClick = { viewModel.updateMode(SetupViewModel.Mode.PARTNER_DEVICE) },
                            label = { Text("我是伴侣") },
                        )
                    }

                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        label = { Text("服务器地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (state.mode) {
                        SetupViewModel.Mode.FIRST_DEVICE -> {
                            OutlinedTextField(
                                value = state.displayName,
                                onValueChange = viewModel::updateDisplayName,
                                label = { Text("你的称呼") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = state.bootstrapSecret,
                                onValueChange = viewModel::updateBootstrapSecret,
                                label = { Text("初始化密钥（BOOTSTRAP_SECRET）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        SetupViewModel.Mode.PARTNER_DEVICE -> {
                            OutlinedTextField(
                                value = state.pairingTokenInput,
                                onValueChange = viewModel::updatePairingToken,
                                label = { Text("配对令牌") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            when (state.mode) {
                                SetupViewModel.Mode.FIRST_DEVICE -> viewModel.bootstrap()
                                SetupViewModel.Mode.PARTNER_DEVICE -> viewModel.pair()
                            }
                        },
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                state.busy -> "请稍候…"
                                state.mode == SetupViewModel.Mode.FIRST_DEVICE -> "初始化空间"
                                else -> "配对加入"
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = "绑定只需要一次。换手机后在这里重新登录即可。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
