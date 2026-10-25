package com.twomemory.couple

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.twomemory.designsystem.PaperTextButton as TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.PaperRule
import com.twomemory.designsystem.PaperSectionHeading
import com.twomemory.designsystem.PaperStatusChip
import com.twomemory.designsystem.TwoMemoryColors
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.paperTexture
import java.time.DayOfWeek
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Store a bounded local image so navigation's content/file URI decoder can reopen it. */
fun storeProfileAvatar(context: Context, source: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "这张照片无法读取，请换一张" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
    val decoded = context.contentResolver.openInputStream(source).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("这张照片无法读取，请换一张")
    val orientation = runCatching {
        context.contentResolver.openInputStream(source).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val transform = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    val bitmap = if (transform.isIdentity) decoded else
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true).also { decoded.recycle() }
    val directory = File(context.filesDir, "profile-avatars").apply { mkdirs() }
    val target = File.createTempFile("avatar-", ".png", directory)
    try {
        target.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        val value = Uri.fromFile(target).toString()
        check(context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE)
            .edit().putString("ownAvatar", value).commit()) { "头像没有保存，请重试" }
        return value
    } catch (error: Exception) {
        target.delete()
        throw error
    } finally {
        bitmap.recycle()
    }
}

@Composable
fun CoupleRoute(
    viewModel: CoupleViewModel,
    onThemeChange: (MoonLetterTheme) -> Unit = {},
    serverOwnName: String = "",
    serverPartnerName: String = "",
    onSaveName: suspend (String) -> String = { it },
    onGenerateCode: suspend () -> PairingCode,
    weeklyReviewEnabled: Boolean = true,
    newEntryNoticeEnabled: Boolean = true,
    reviewDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    reviewHour: Int = 20,
    onWeeklyReviewChange: (Boolean) -> Unit = {},
    onNewEntryNoticeChange: (Boolean) -> Unit = {},
    onReviewTimeChange: (DayOfWeek, Int) -> Unit = { _, _ -> },
    versionLabel: String = "M1 · 0.1.0",
    serverAddress: String = "",
    onOpenConnection: () -> Unit = {},
    onOpenAnniversary: () -> Unit = {},
    onOpenCapsule: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenPastToday: () -> Unit = {},
    onOpenWeeklySummary: () -> Unit = {},
    anniversaryName: String = "",
    anniversaryDate: String = "",
    anniversaryRepeats: Boolean = true,
    onSendTestNotification: () -> String = { "本机通知检查尚未接通" },
    onOpenNotificationSettings: () -> Unit = {},
    cityDataOverview: String? = null,
    onOpenOccasions: () -> Unit = {},
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

    var avatarError by remember { mutableStateOf<String?>(null) }
    var savingAvatar by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                savingAvatar = true
                avatarError = null
                try {
                    val stored = withContext(Dispatchers.IO) { storeProfileAvatar(context, uri) }
                    viewModel.updateOwnAvatar(stored)
                } catch (error: Exception) {
                    avatarError = error.message ?: "头像没有保存，请重试"
                } finally {
                    savingAvatar = false
                }
            }
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

    var partnerUri by remember { mutableStateOf(prefs.getString("partnerAvatar", null)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "partnerAvatar" || key == null) partnerUri = prefs.getString("partnerAvatar", null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val partnerBitmap = remember(partnerUri) {
        partnerUri?.let { value -> runCatching {
            context.contentResolver.openInputStream(Uri.parse(value)).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull() }
    }

    CoupleScreen(
        state = state,
        ownAvatarBitmap = ownAvatarBitmap,
        partnerAvatarBitmap = partnerBitmap,
        onOwnNameChange = viewModel::updateOwnName,
        onOwnAvatarChange = { if (!savingAvatar) avatarPicker.launch(arrayOf("image/*")) },
        onSaveName = { scope.launch { viewModel.saveOwnName(onSaveName) } },
        onGenerateCode = { viewModel.generatePairingCode { onGenerateCode() } },
        onHideCode = viewModel::hidePairingCode,
        weeklyReviewEnabled = weeklyReviewEnabled,
        newEntryNoticeEnabled = newEntryNoticeEnabled,
        reviewDayOfWeek = reviewDayOfWeek,
        reviewHour = reviewHour,
        onWeeklyReviewChange = onWeeklyReviewChange,
        onNewEntryNoticeChange = onNewEntryNoticeChange,
        onReviewTimeChange = onReviewTimeChange,
        onThemeChange = {
            viewModel.updateTheme(it)
            prefs.edit().putString("theme", it.name).apply()
            onThemeChange(it)
        },
        versionLabel = versionLabel,
        serverAddress = serverAddress,
        onOpenConnection = onOpenConnection,
        onOpenAnniversary = onOpenAnniversary,
        onOpenCapsule = onOpenCapsule,
        onOpenExport = onOpenExport,
        onOpenPastToday = onOpenPastToday,
        onOpenWeeklySummary = onOpenWeeklySummary,
        anniversaryName = anniversaryName,
        anniversaryDate = anniversaryDate,
        anniversaryRepeats = anniversaryRepeats,
        onSendTestNotification = onSendTestNotification,
        onOpenNotificationSettings = onOpenNotificationSettings,
        cityDataOverview = cityDataOverview,
        onOpenOccasions = onOpenOccasions,
    )
    if (avatarError != null) {
        AlertDialog(onDismissRequest = { avatarError = null }, title = { Text("头像没有保存") },
            text = { Text(avatarError.orEmpty()) },
            confirmButton = { TextButton(onClick = { avatarError = null }) { Text("知道了") } })
    }
}

@Composable
fun CoupleScreen(
    state: CoupleUiState,
    onOwnNameChange: (String) -> Unit,
    onThemeChange: (MoonLetterTheme) -> Unit,
    onSaveName: () -> Unit = {},
    ownAvatarBitmap: ImageBitmap? = null,
    partnerAvatarBitmap: ImageBitmap? = null,
    onOwnAvatarChange: () -> Unit = {},
    onGenerateCode: () -> Unit = {},
    onHideCode: () -> Unit = {},
    weeklyReviewEnabled: Boolean = true,
    newEntryNoticeEnabled: Boolean = true,
    reviewDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    reviewHour: Int = 20,
    onWeeklyReviewChange: (Boolean) -> Unit = {},
    onNewEntryNoticeChange: (Boolean) -> Unit = {},
    onReviewTimeChange: (DayOfWeek, Int) -> Unit = { _, _ -> },
    versionLabel: String = "M1 · 0.1.0",
    serverAddress: String = "",
    onOpenConnection: () -> Unit = {},
    onOpenAnniversary: () -> Unit = {},
    onOpenCapsule: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenPastToday: () -> Unit = {},
    onOpenWeeklySummary: () -> Unit = {},
    anniversaryName: String = "",
    anniversaryDate: String = "",
    anniversaryRepeats: Boolean = true,
    onSendTestNotification: () -> String = { "本机通知检查尚未接通" },
    onOpenNotificationSettings: () -> Unit = {},
    cityDataOverview: String? = null,
    onOpenOccasions: () -> Unit = {},
) {
    var editingName by remember { mutableStateOf(false) }
    var pickingHour by rememberSaveable { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    var showingReview by remember { mutableStateOf(false) }
    var notificationTestStatus by remember { mutableStateOf<String?>(null) }
    val pageScroll = rememberScrollState()
    var today by remember { mutableStateOf(java.time.LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) { today = java.time.LocalDate.now(); kotlinx.coroutines.delay(60_000) }
    }
    val draft = state.draftName.trim()
    val canSave = draft.isNotEmpty() && draft != state.ownName && !state.savingName
    LaunchedEffect(state.ownName) { editingName = false }
    LaunchedEffect(showingSettings) { pageScroll.scrollTo(0) }
    Column(
        modifier = Modifier.fillMaxSize().paperTexture(MaterialTheme.colorScheme.background)
            .verticalScroll(pageScroll).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 20.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我们", style = TwoMemoryTypography.display, modifier = Modifier.weight(1f))
            IconButton(onClick = { showingSettings = !showingSettings }) {
                Icon(TwoMemoryIcons.Settings, contentDescription = "设置")
            }
        }
        if (!showingSettings) {
        CoupleHero(
            ownName = state.ownName.ifBlank { "我" },
            partnerName = state.partnerName.ifBlank { "待配对" },
            ownAvatarBitmap = ownAvatarBitmap,
            partnerAvatarBitmap = partnerAvatarBitmap,
            onOwnAvatarChange = onOwnAvatarChange,
            onEditName = { onOwnNameChange(state.ownName); editingName = true },
        )
        if (editingName) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.draftName,
                    onValueChange = onOwnNameChange,
                    label = { Text("我的名字") },
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
                    TextButton(onClick = { onOwnNameChange(state.ownName); editingName = false }) { Text("取消") }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f)),
        ) {
            Column {
                SettingsRow(
                    TwoMemoryIcons.Calendar,
                    "纪念日与倒计时",
                    listOfNotNull(
                        anniversaryName.trim().takeIf { it.isNotEmpty() },
                        anniversaryDate.trim().takeIf { it.isNotEmpty() },
                        anniversaryCountdown(anniversaryDate, anniversaryRepeats, today),
                    ).joinToString(" · ").ifBlank { "管理属于我们的重要日子" },
                    onClick = onOpenAnniversary,
                    subtitleMaxLines = 2,
                )
                PaperRule(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                SettingsRow(TwoMemoryIcons.Calendar, "生日与心意卡", "收好生日和想送给你的惊喜", onClick = onOpenOccasions)
                PaperRule(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                SettingsRow(TwoMemoryIcons.Capsule, "时间胶囊", "给未来的我们留下一段此刻的心情", onClick = onOpenCapsule)
                PaperRule(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                SettingsRow(TwoMemoryIcons.Time, "回忆手书", "翻翻我们的日子 · 过去的今天", onClick = { showingReview = true })
                PaperRule(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                SettingsRow(TwoMemoryIcons.Export, "数据与导出", "备份我们的回忆", onClick = onOpenExport)
            }
        }
        }
        if (showingSettings) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                PaperSectionHeading("月笺设置", modifier = Modifier.weight(1f))
                IconButton(onClick = { showingSettings = false }) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "关闭设置")
                }
            }
            cityDataOverview?.let { overview ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 22.dp), shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("回忆数据", style = TwoMemoryTypography.title)
                        Text(overview, style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeMuted)
                        Text("按当前可见的城市记录整理，不代表实际到访。", style = TwoMemoryTypography.caption,
                            color = TwoMemoryColors.WarmBeigeMuted)
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f)),
            ) {
              Column {
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
            color = Color.Transparent,
        ) {
            Column {
                Text(
                    "回看与通知",
                    style = TwoMemoryTypography.body,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                )
                Text("测试只检查本机通知通道；后台提醒可能延迟。", Modifier.padding(horizontal = 20.dp), style = TwoMemoryTypography.caption)
                TextButton(onClick = { notificationTestStatus = onSendTestNotification() }) { Text("发送测试通知") }
                TextButton(onClick = onOpenNotificationSettings) { Text("系统通知设置") }
                notificationTestStatus?.let { Text(it, Modifier.padding(horizontal = 20.dp), style = TwoMemoryTypography.caption) }
                SwitchRow(
                    title = "每周旧笺提醒",
                    subtitle = "每周提醒重看一篇至少八天前的记录，没有旧记录就不打扰",
                    checked = weeklyReviewEnabled,
                    onCheckedChange = onWeeklyReviewChange,
                )
                if (weeklyReviewEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReviewDays.forEach { day ->
                            FilterChip(
                                selected = reviewDayOfWeek == day,
                                onClick = { onReviewTimeChange(day, reviewHour) },
                                label = { Text(dayLabel(day)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "每周${dayLabel(reviewDayOfWeek)} ${"%02d:00".format(reviewHour)}",
                            style = TwoMemoryTypography.caption,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { pickingHour = true }) { Text("改时间") }
                    }
                }
                SwitchRow(
                    title = "对方写了新的",
                    subtitle = "对方写下新记录时，轻轻提醒你",
                    checked = newEntryNoticeEnabled,
                    onCheckedChange = onNewEntryNoticeChange,
                )
            }
        }
        if (pickingHour) {
            ReviewTimeDialog(
                initialHour = reviewHour,
                onDismiss = { pickingHour = false },
                onConfirm = { hour ->
                    onReviewTimeChange(reviewDayOfWeek, hour)
                    pickingHour = false
                },
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
        ) {
            Column {
                Text(
                    "换手机或重新配对",
                    style = TwoMemoryTypography.body,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                )
                Text(
                    "把生成的码发到另一台手机，在「接受邀请」里粘贴就能进空间。每次新生成都会让上一个码作废。",
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
                            "你自己的换机码，15 分钟内一次有效。只能恢复当前身份，不用于邀请对方；在新设备使用后，这台设备会退出。"
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
        if (serverAddress.isNotBlank()) {
            TextButton(onClick = onOpenConnection, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("云端连接与服务管理", style = TwoMemoryTypography.body)
            }
            Text(serverAddress, style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Text(
            "月笺 · $versionLabel",
            style = TwoMemoryTypography.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
        )
    }
    if (showingReview) {
        AlertDialog(
            onDismissRequest = { showingReview = false },
            title = { Text("翻翻我们的日子") },
            text = {
                Column {
                    SettingsRow(TwoMemoryIcons.Calendar, "回忆手书", "按周、按月，翻阅文字和照片", onClick = { showingReview = false; onOpenWeeklySummary() })
                    SettingsRow(TwoMemoryIcons.Time, "过去的今天", "重逢同一天留下的回忆", onClick = { showingReview = false; onOpenPastToday() })
                }
            },
            confirmButton = {
                TextButton(onClick = { showingReview = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun CoupleHero(
    ownName: String,
    partnerName: String,
    ownAvatarBitmap: ImageBitmap?,
    partnerAvatarBitmap: ImageBitmap?,
    onOwnAvatarChange: () -> Unit,
    onEditName: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        shape = RoundedCornerShape(28.dp),
        color = TwoMemoryColors.WarmBeigeSurface.copy(alpha = .72f),
        border = BorderStroke(1.dp, TwoMemoryColors.WarmBeigeLine.copy(alpha = .78f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperStatusChip("你们的共同空间", accent = TwoMemoryColors.WarmBeigeSage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBadge(
                    modifier = Modifier.weight(1f),
                    name = ownName,
                    bitmap = ownAvatarBitmap,
                    accent = MaterialTheme.colorScheme.primary,
                    editable = true,
                    onClick = onOwnAvatarChange,
                    onEditName = onEditName,
                )
                Icon(
                    TwoMemoryIcons.Like,
                    contentDescription = "你们的连接",
                    tint = TwoMemoryColors.WarmBeigeAccent,
                    modifier = Modifier.size(25.dp),
                )
                AvatarBadge(
                    modifier = Modifier.weight(1f),
                    name = partnerName,
                    bitmap = partnerAvatarBitmap,
                    accent = Color(0xFF6F8268),
                )
            }
            Text("和你一起，记录平凡的日子", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TwoMemoryTypography.body)
            Text(subtitle, style = TwoMemoryTypography.caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

/** Only the hour is picked: the re-encounter is a moment in the week, not a deadline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewTimeDialog(
    initialHour: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    com.twomemory.designsystem.PaperTimePickerDialog(java.time.LocalTime.of(initialHour, 0), onDismiss,
        onConfirm = { onConfirm(it.hour) }, hourOnly = true)
}

private val ReviewDays = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private fun dayLabel(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    subtitleMaxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TwoMemoryTypography.body)
            Text(subtitle, style = TwoMemoryTypography.caption,
                maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        Icon(TwoMemoryIcons.Chevron, contentDescription = "打开$title", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
    }
}

@Composable
private fun AvatarBadge(
    name: String,
    accent: Color,
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap? = null,
    editable: Boolean = false,
    onClick: () -> Unit = {},
    onEditName: () -> Unit = {},
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "$name 头像", contentScale = ContentScale.Crop,
                modifier = Modifier.size(58.dp).clip(CircleShape).clickable(enabled = editable, onClick = onClick))
        } else {
            Box(Modifier.size(58.dp).clip(CircleShape).background(accent.copy(alpha = .12f))
                .clickable(enabled = editable, onClick = onClick).semantics { contentDescription = "$name 头像" },
                contentAlignment = Alignment.Center) {
                Text(name.trim().let { if (it.isEmpty()) "我" else String(Character.toChars(it.codePointAt(0))) },
                    style = TwoMemoryTypography.title, color = accent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = TwoMemoryTypography.title, color = accent, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (editable) {
                IconButton(onClick = onEditName, modifier = Modifier.size(36.dp)) {
                    Icon(TwoMemoryIcons.Edit, contentDescription = "修改名字", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
