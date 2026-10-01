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

@Composable
fun TwoMemoryApp() {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(MoonLetterTheme.WARM_BEIGE) }

    // Sync on app start and every foreground return (best effort; the
    // WorkManager path stays the background fallback).
    LaunchedEffect(Unit) { SyncSession.triggerSync(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                SyncSession.triggerSync(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TwoMemoryTheme(theme = theme) {
        AppNavigation(onThemeChange = { theme = it })
    }
}
