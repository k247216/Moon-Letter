package com.twomemory.designsystem

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/** Shared across albums/records: each feature must not quietly add another image budget. */
object PhotoPixelCache {
    enum class Bucket { PREVIEW, READING, DETAIL }
    data class Key(val source: String, val localPath: String?, val assetId: String? = null)
    data class Pixels(val version: String, val bitmap: ImageBitmap)
    private fun cache(bytes: Int) = object : android.util.LruCache<Key, Pixels>(bytes) {
        override fun sizeOf(key: Key, value: Pixels) = value.bitmap.asAndroidBitmap().allocationByteCount.coerceAtLeast(1)
    }
    // 32MiB of retained decoded pixels in total; visible images are additional.
    private val preview = cache(8 * 1024 * 1024)
    private val reading = cache(8 * 1024 * 1024)
    private val detail = cache(16 * 1024 * 1024)
    private fun pool(bucket: Bucket) = when (bucket) {
        Bucket.PREVIEW -> preview
        Bucket.READING -> reading
        Bucket.DETAIL -> detail
    }
    fun get(bucket: Bucket, key: Key): Pixels? = pool(bucket).get(key)
    fun put(bucket: Bucket, key: Key, version: String, bitmap: ImageBitmap) {
        // Never recycle on eviction: a page may still display the same pixels.
        pool(bucket).put(key, Pixels(version, bitmap))
    }
    fun remove(bucket: Bucket, key: Key) { pool(bucket).remove(key) }
}
