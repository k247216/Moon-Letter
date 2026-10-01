package com.twomemory.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TwoMemoryApp() {
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
    LaunchedEffect(Unit) { SyncSession.triggerSync(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                CoroutineScope(Dispatchers.Default).launch {
                    SyncSession.triggerSync(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TwoMemoryTheme(theme = theme) {
        AppNavigation(onThemeChange = { theme = it })
    }
}
