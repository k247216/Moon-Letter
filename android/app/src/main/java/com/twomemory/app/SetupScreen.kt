package com.twomemory.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twomemory.designsystem.PaperSurface
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.paperTexture
import com.twomemory.couple.storeProfileAvatar
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.network.SessionApi
import com.twomemory.network.SetupHttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val mode: Mode = Mode.PARTNER_DEVICE,
        val phase: Phase = Phase.FORM,
        val serverUrl: String = ServerEndpointMigration.PUBLIC_ORIGIN,
        val bootstrapSecret: String = "",
        val displayName: String = "",
        val pairingTokenInput: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val shownPairingToken: String? = null,
        val shownPairingTokenKind: String = "INVITE",
        val boundSession: BoundSession? = null,
        val sessionSaved: Boolean = false,
    ) {
        val canSubmit: Boolean
            get() = !busy && serverUrl.isNotBlank() && displayName.isNotBlank() && when (mode) {
                Mode.FIRST_DEVICE -> bootstrapSecret.isNotBlank()
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
                        shownPairingTokenKind = tokenInfo.pairingTokenKind,
                        boundSession = BoundSession(s.serverUrl, result.token, result.coupleId, result.userId),
                    )
                }
            } catch (e: SetupHttpException) {
                val reason = when (e.code) {
                    403 -> "初始化密钥不对，请核对后重试"
                    409 -> "这个空间里已经没有可以找回的成员了：请先让另一位还在的手机重新生成配对码"
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
                val result = api.pair(s.serverUrl, s.pairingTokenInput, s.displayName)
                mutate {
                    it.copy(
                        busy = false,
                        phase = Phase.BOUND,
                        boundSession = BoundSession(s.serverUrl, result.deviceToken, result.couple.id, result.userId),
                    )
                }
            } catch (e: SetupHttpException) {
                val reason = when (e.code) {
                    409 -> "配对码已用过或已过期：请在另一台手机的「我们」里重新生成一个"
                    403 -> "配对码不对，请核对后重试"
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
    if (!bindingStorageAvailable()) return
    val context = LocalContext.current
    var storageError by remember { mutableStateOf<String?>(null) }
    var storageRetry by remember { mutableStateOf(0) }
    var expectedBinding by remember { mutableStateOf(SyncSession.load(context)) }
    val state by viewModel.state.collectAsState()
    val profilePrefs = remember(context) { context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE) }
    var ownAvatar by remember { mutableStateOf(profilePrefs.getString("ownAvatar", null)) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    var savingAvatar by remember { mutableStateOf(false) }
    var connectionDetails by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                savingAvatar = true
                avatarError = null
                try {
                    ownAvatar = withContext(Dispatchers.IO) { storeProfileAvatar(context, uri) }
                } catch (error: Exception) {
                    avatarError = error.message ?: "头像没有保存，请重试"
                } finally {
                    savingAvatar = false
                }
            }
        }
    }
    val avatarBitmap = remember(ownAvatar) {
        ownAvatar?.let { value ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value)).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }

    // Persist the issued session exactly once, then proceed when bound.
    LaunchedEffect(state.boundSession, state.sessionSaved, storageRetry) {
        val session = state.boundSession
        if (session != null && !state.sessionSaved) {
            try {
                if (!SyncSession.saveIfCurrent(context, expectedBinding, session.token, session.coupleId, session.userId, session.baseUrl)) return@LaunchedEffect
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                storageError="连接已签发，但安全存储暂不可用。请解锁后重试保存，不要重复初始化或配对。"
                return@LaunchedEffect
            }
            storageError=null
            expectedBinding = SyncSession.load(context)
            // The server just accepted this identity; do not wait for a second
            // network request before showing the chosen name on the first page.
            SyncSession.sessionPrefs(context).edit().putString("ownName", state.displayName).apply()
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
            .paperTexture(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.CenterStart) {
            Image(painterResource(com.twomemory.designsystem.R.drawable.moonletter_cover_puppy),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("月笺", style = TwoMemoryTypography.display, color = com.twomemory.designsystem.TwoMemoryColors.WarmBeigeInk)
                Text(if (state.mode == SetupViewModel.Mode.FIRST_DEVICE) BuildConfig.FIRST_MEMBER_WELCOME else "记录彼此想念的每一天",
                    style = TwoMemoryTypography.caption, color = com.twomemory.designsystem.TwoMemoryColors.WarmBeigeInk)
            }
        }
        storageError?.let { message ->
            Text(message, color=MaterialTheme.colorScheme.error)
            if (state.boundSession!=null) TextButton(onClick={ storageRetry++ }) { Text("重试保存原连接") }
        }

        if (state.phase == SetupViewModel.Phase.FORM) {
            PaperSurface(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("我的资料", style = TwoMemoryTypography.title)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f))
                                .clickable(enabled = !savingAvatar && !state.busy) { avatarPicker.launch(arrayOf("image/*")) }
                                .semantics { contentDescription = "选择头像" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarBitmap != null) {
                                Image(avatarBitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(state.displayName.trim().let { if (it.isEmpty()) "我" else String(Character.toChars(it.codePointAt(0))) },
                                    style = TwoMemoryTypography.title, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column {
                            TextButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }, enabled = !savingAvatar && !state.busy) {
                                Text(if (savingAvatar) "保存头像中…" else "选择照片")
                            }
                            TextButton(onClick = {
                                profilePrefs.edit().remove("ownAvatar").apply()
                                ownAvatar = null
                                avatarError = null
                            }, enabled = !savingAvatar && !state.busy) { Text("使用默认头像") }
                        }
                    }
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::updateDisplayName,
                        label = { Text("你的称呼") },
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("选一个喜欢的头像，也可以以后再换。",
                        style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    avatarError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = TwoMemoryTypography.caption) }
                }
            }
        }
        PaperSurface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.phase == SetupViewModel.Phase.WAITING_PARTNER) {
                    Text("给你的小邀请", style = TwoMemoryTypography.title)
                    Text(
                        "把配对码发给对方，一起打开这本手账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.shownPairingTokenKind == "REJOIN") {
                        Text(
                            "这是你自己的换机码，15 分钟内一次有效。在新设备使用后，旧设备会退出；不要把它当作给对方的邀请。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SelectionContainer {
                        Text(
                            state.shownPairingToken.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(onClick = onBound, enabled=state.sessionSaved, modifier = Modifier.fillMaxWidth()) {
                        Text("进入月笺")
                    }
                } else {
                    Text("两个人，一本手账", style = TwoMemoryTypography.title)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == SetupViewModel.Mode.FIRST_DEVICE,
                            onClick = { viewModel.updateMode(SetupViewModel.Mode.FIRST_DEVICE) },
                            label = { Text("创建手账") },
                        )
                        FilterChip(
                            selected = state.mode == SetupViewModel.Mode.PARTNER_DEVICE,
                            onClick = { viewModel.updateMode(SetupViewModel.Mode.PARTNER_DEVICE) },
                            label = { Text("接受邀请") },
                        )
                    }

                    if (connectionDetails) OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = viewModel::updateServerUrl,
                        label = { Text("服务器地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when (state.mode) {
                        SetupViewModel.Mode.FIRST_DEVICE -> {
                            OutlinedTextField(
                                value = state.bootstrapSecret,
                                onValueChange = viewModel::updateBootstrapSecret,
                                label = { Text("空间钥匙") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        SetupViewModel.Mode.PARTNER_DEVICE -> {
                            OutlinedTextField(
                                value = state.pairingTokenInput,
                                onValueChange = viewModel::updatePairingToken,
                                label = { Text("配对码") },
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
                            if (runCatching { TokenCipher.seal("storage-readiness-check") }.isFailure) {
                                storageError="请先解锁设备并恢复安全存储；尚未向服务器提交初始化或配对。"
                                return@Button
                            }
                            storageError=null
                            when (state.mode) {
                                SetupViewModel.Mode.FIRST_DEVICE -> viewModel.bootstrap()
                                SetupViewModel.Mode.PARTNER_DEVICE -> viewModel.pair()
                            }
                        },
                        enabled = state.canSubmit && !savingAvatar,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                state.busy -> "请稍候…"
                                state.mode == SetupViewModel.Mode.FIRST_DEVICE -> "创建我们的手账"
                                else -> "打开我们的手账"
                            }
                        )
                    }
                }
            }
        }

        if (state.phase == SetupViewModel.Phase.FORM) TextButton(onClick = { connectionDetails = !connectionDetails }, enabled = !state.busy) {
            Text(if (connectionDetails) "收起设置" else "连接设置", style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
