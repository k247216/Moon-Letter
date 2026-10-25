package com.twomemory.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Called only while STARTED: runs immediately, retries failures, stops with lifecycle cancellation. */
internal suspend fun foregroundSyncLoop(
    refresh: (suspend () -> Unit)? = null,
    refreshDispatcher: CoroutineDispatcher = Dispatchers.IO,
    sync: suspend () -> Unit,
) = coroutineScope {
    var refreshJob: Job? = null
    while (true) {
        try {
            sync()
            if (refresh != null && refreshJob?.isActive != true) {
                refreshJob = launch(refreshDispatcher) {
                    try { refresh() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Keep confirmed caches and retry on a later cycle. */ }
                }
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Retry without requiring another user interaction. */ }
        delay(3_000)
    }
}
