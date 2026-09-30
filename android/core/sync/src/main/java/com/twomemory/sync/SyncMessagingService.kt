package com.twomemory.sync

import android.content.Context
import java.util.UUID

class SyncMessagingService(private val context: Context) {
    fun onSpaceChanged(coupleId: String) {
        val id = runCatching { UUID.fromString(coupleId) }.getOrNull() ?: return
        SyncWorker.enqueue(context, id)
    }
}
