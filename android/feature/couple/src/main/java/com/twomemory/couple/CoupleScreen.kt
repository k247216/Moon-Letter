package com.twomemory.couple

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.R
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import kotlinx.coroutines.launch

@Composable
fun CoupleRoute(
    viewModel: CoupleViewModel,
    onThemeChange: (MoonLetterTheme) -> Unit = {},
    serverOwnName: String = "",
    serverPartnerName: String = "",
    onSaveName: suspend (String) -> String = { it },
    onGenerateCode: suspend () -> PairingCode,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moon_letter_profile", android.content.Context.MODE_PRIVATE) }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val restoredTheme = runCatching {
            MoonLetterTheme.valueOf(prefs.getString("theme", MoonLetterTheme.WARM_BEIGE.name).orEmpty())
        }.getOrDefault(MoonLetterTheme.WARM_BEIGE)
        viewModel.restore(
            ownAvatar = prefs.getString("ownAvatar", null),
            theme = restoredTheme,
        )
        onThemeChange(restoredTheme)
    }
    LaunchedEffect(serverOwnName, serverPartnerName) {
        viewModel.restoreNames(own = serverOwnName, partner = serverPartnerName)
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit().putString("ownAvatar", uri.toString()).apply()
            viewModel.updateOwnAvatar(uri.toString())
        }
    }
    val ownAvatarBitmap = remember(state.ownAvatar) {
        state.ownAvatar?.let { value ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    CoupleScreen(
        state = state,
        ownAvatarBitmap = ownAvatarBitmap,
        onOwnNameChange = viewModel::updateOwnName,
        onOwnAvatarChange = { avatarPicker.launch(arrayOf("image/*")) },
        onSaveName = { scope.launch { viewModel.saveOwnName(onSaveName) } },
        onGenerateCode = { viewModel.generatePairingCode { onGenerateCode() } },
        onHideCode = viewModel::hidePairingCode,
        onThemeChange = {
            viewModel.updateTheme(it)
            prefs.edit().putString("theme", it.name).apply()
            onThemeChange(it)
        },
    )
}

@Composable
fun CoupleScreen(
    state: CoupleUiState,
    onOwnNameChange: (String) -> Unit,
    onThemeChange: (MoonLetterTheme) -> Unit,
    onSaveName: () -> Unit = {},
    ownAvatarBitmap: ImageBitmap? = null,
    onOwnAvatarChange: () -> Unit = {},
    onGenerateCode: () -> Unit = {},
    onHideCode: () -> Unit = {},
) {
    var editingName by remember { mutableStateOf(false) }
    val draft = state.draftName.trim()
    val canSave = draft.isNotEmpty() && draft != state.ownName && !state.savingName
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 20.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我们", style = TwoMemoryTypography.display, modifier = Modifier.weight(1f))
            IconButton(onClick = {}, enabled = false) {
                Icon(TwoMemoryIcons.Settings, contentDescription = "设置")
            }
        }
        Image(
            painter = painterResource(R.drawable.moonletter_cover_default),
            contentDescription = "我们的封面",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(190.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            AvatarBadge(
                name = state.ownName.ifBlank { "取个名字" },
                fallback = R.drawable.moonletter_avatar_xiaoman,
                bitmap = ownAvatarBitmap,
                accent = MaterialTheme.colorScheme.primary,
                editable = true,
                onClick = onOwnAvatarChange,
                onEditName = { editingName = !editingName },
            )
            AvatarBadge(
                name = state.partnerName.ifBlank { "伴侣" },
                fallback = R.drawable.moonletter_avatar_ayu,
                accent = Color(0xFF6F8268),
            )
        }
        if (editingName) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.draftName,
                    onValueChange = onOwnNameChange,
                    label = { Text("我的名字" + if (state.ownName.isBlank()) "" else "（现在叫 ${state.ownName}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "这个名字会同时出现在你们两个人的手机上。",
                    style = TwoMemoryTypography.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                )
                state.nameError?.let {
                    Text(it, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSaveName, enabled = canSave) {
                        Text(if (state.savingName) "保存中…" else "保存")
                    }
                    TextButton(onClick = { editingName = false }) { Text("取消") }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {
            Column {
                SettingsRow(TwoMemoryIcons.Calendar, "纪念日与倒计时", "管理属于我们的重要日子")
                SettingsRow(TwoMemoryIcons.Capsule, "时间胶囊", "给未来的我们留下一段此刻的心情")
                SettingsRow(TwoMemoryIcons.Export, "数据与导出", "备份我们的回忆")
                Text(
                    "显示主题",
                    style = TwoMemoryTypography.body,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilterChip(
                        selected = state.theme == MoonLetterTheme.WARM_BEIGE,
                        onClick = { onThemeChange(MoonLetterTheme.WARM_BEIGE) },
                        label = { Text("暖米色") },
                    )
                    FilterChip(
                        selected = state.theme == MoonLetterTheme.PURE_WHITE,
                        onClick = { onThemeChange(MoonLetterTheme.PURE_WHITE) },
                        label = { Text("纯白") },
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {
            Column {
                Text(
                    "换手机或重新配对",
                    style = TwoMemoryTypography.body,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                )
                Text(
                    "把生成的码发到另一台手机，它在「我是伴侣」里粘贴就能进空间。每次新生成都会让上一个码作废。",
                    style = TwoMemoryTypography.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                )
                state.codeError?.let {
                    Text(
                        it,
                        style = TwoMemoryTypography.caption,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    )
                }
                state.pairingCode?.let { code ->
                    SelectionContainer {
                        Text(
                            code,
                            style = TwoMemoryTypography.caption,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    Text(
                        if (state.pairingCodeKind == "REJOIN") {
                            "一次有效，15 分钟内。空间里两个位置都已经被占，所以这个码认的是另一位成员自己的位置——只有丢手机的 TA 能用它回来，陌生人进不来，以前的记录也还是 TA 的。"
                        } else {
                            "一次有效，15 分钟内。"
                        },
                        style = TwoMemoryTypography.caption,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onGenerateCode, enabled = !state.generatingCode) {
                        Text(
                            when {
                                state.generatingCode -> "正在生成…"
                                state.pairingCode == null -> "生成配对码"
                                else -> "换一个新的码"
                            }
                        )
                    }
                    if (state.pairingCode != null) {
                        TextButton(onClick = onHideCode) { Text("藏起来") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TwoMemoryTypography.body)
            Text(subtitle, style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        Text(
            "还没开放",
            style = TwoMemoryTypography.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
        )
    }
}

@Composable
private fun AvatarBadge(
    name: String,
    fallback: Int,
    accent: Color,
    bitmap: ImageBitmap? = null,
    editable: Boolean = false,
    onClick: () -> Unit = {},
    onEditName: () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "$name 头像", contentScale = ContentScale.Crop,
                modifier = Modifier.size(78.dp).clip(CircleShape).clickable(enabled = editable, onClick = onClick))
        } else {
            Image(painter = painterResource(fallback), contentDescription = "$name 头像", contentScale = ContentScale.Crop,
                modifier = Modifier.size(78.dp).clip(CircleShape).clickable(enabled = editable, onClick = onClick))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = TwoMemoryTypography.title, color = accent)
            if (editable) {
                IconButton(onClick = onEditName, modifier = Modifier.size(36.dp)) {
                    Icon(TwoMemoryIcons.Edit, contentDescription = "修改名字", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
