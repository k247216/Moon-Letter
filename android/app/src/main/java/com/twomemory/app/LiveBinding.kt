package com.twomemory.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Refresh only request ownership, not caller-owned route, reading or draft state. */
@Composable
internal fun rememberLiveBinding(context: Context): SyncSession.BindingSnapshot? {
    // Preference listener order is unspecified. Observe the security epoch itself
    // so a later invalidation always refreshes only the binding-owned Store.
    val revision by SyncSession.bindingRevision.collectAsState()
    return remember(context, revision) { SyncSession.captureBinding(context) }
}
