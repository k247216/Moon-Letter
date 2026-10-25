package com.twomemory.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.twomemory.app.notifications.NotificationPreferences
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun TwoMemoryApp(
    initialEntryId: String? = null,
    initialEditorMode: String? = null,
    initialEditorRequest: Int = 0,
    onInitialContentReady: () -> Unit = {},
    initialReminder: String? = null,
    initialNoticeScope: String? = null,
    initialNoticeRequest: Int = 0,
) {
    val context = LocalContext.current
    val profilePrefs = remember {
        context.getSharedPreferences("moon_letter_profile", android.content.Context.MODE_PRIVATE)
    }
    var theme by remember {
        mutableStateOf(
            runCatching {
                MoonLetterTheme.valueOf(
                    profilePrefs.getString("theme", MoonLetterTheme.WARM_BEIGE.name).orEmpty(),
                )
            }.getOrDefault(MoonLetterTheme.WARM_BEIGE),
        )
    }

    // Sync on app start and every foreground return (best effort; the
    // WorkManager path stays the background fallback).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            foregroundSyncLoop(
                sync = { SyncSession.triggerSync(context, includeSideEffects = false) },
                refresh = { SyncSession.runCycleSideEffects(context) },
            )
        }
    }

    // Both hooks are on by default, so the system permission is asked for once the
    // space is bound — never before, and never twice.
    val notificationPrefs = remember { NotificationPreferences(context) }
    val askNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val bound = SyncSession.load(context) != null
        val denied = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bound && denied &&
            !notificationPrefs.notificationPermissionAsked
        ) {
            notificationPrefs.notificationPermissionAsked = true
            askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    TwoMemoryTheme(theme = theme) {
        SessionScopedContent { AppNavigation(
            onThemeChange = { theme = it },
            initialEntryId = initialEntryId,
            initialEditorMode = initialEditorMode,
            initialEditorRequest = initialEditorRequest,
            onInitialContentReady = onInitialContentReady,
            initialReminder = initialReminder,
            initialNoticeScope = initialNoticeScope,
            initialNoticeRequest = initialNoticeRequest,
        ) }
    }
}

/** Dispose every remembered screen/DAO/detail on identity changes, not token refresh. */
@Composable
internal fun SessionScopedContent(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun accountScope(): String? = SyncSession.load(context)?.let {
        "${it.storageOrigin}|${it.coupleId}|${it.userId}|${it.databaseName}"
    }
    var account by remember(context) { mutableStateOf(accountScope()) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        val prefs = SyncSession.sessionPrefs(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            scope.launch { account = accountScope() }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        account = accountScope()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    key(account) { content() }
}
