package com.twomemory.designsystem

/** Preview improves first paint, but is never permission to skip the original. */
internal suspend fun <T> loadPhotoStages(preview: suspend () -> T?, original: suspend () -> T?, showPreview: (T) -> Unit): T? {
    try { preview()?.let(showPreview) }
    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { /* Unsupported/failed previews must not block the original. */ }
    return original()
}
