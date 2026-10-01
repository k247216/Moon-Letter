package com.twomemory.app

import android.content.Context
import android.graphics.BitmapFactory
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.network.MediaCreateCommand
import com.twomemory.network.RetrofitMediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Uploads every local IMAGE block that still lacks an asset (Task 13), before
 * the sync engine gets a turn. A block whose upload failed keeps its whole entry
 * out of the outbox queue, so the record stays an unsent draft here instead of
 * becoming a published entry with a missing picture on both phones.
 */
object MediaUploadManager {

    private val mediaApi = RetrofitMediaApi.create()

    suspend fun uploadPendingImages(context: Context) {
        val session = SyncSession.load(context) ?: return
        uploadPendingImages(session, AppDatabase.build(context))
    }

    suspend fun uploadPendingImages(session: SyncSession.Session, database: AppDatabase) {
        val writer = LocalEntryWriter(database)
        for (entity in database.entryDao().imageBlocksWithoutAsset()) {
            try {
                val payload = org.json.JSONObject(entity.payload)
                val file = File(payload.optString("localPath"))
                if (!file.isFile) continue
                val bytes = withContext(Dispatchers.IO) { file.readBytes() }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val ticket = mediaApi.createUpload(
                    session.baseUrl,
                    session.token,
                    MediaCreateCommand(
                        ownerId = session.userId,
                        coupleId = session.coupleId,
                        operationId = UUID.fromString(entity.id),
                        kind = "IMAGE",
                        mimeType = payload.optString("mime", "image/jpeg"),
                        byteSize = bytes.size.toLong(),
                        width = bounds.outWidth.coerceAtLeast(1),
                        height = bounds.outHeight.coerceAtLeast(1),
                        sha256 = sha256(bytes),
                    ),
                )
                mediaApi.uploadData(
                    session.baseUrl,
                    session.token,
                    ticket.assetId,
                    bytes,
                    payload.optString("mime", "image/jpeg"),
                )
                writer.attachAsset(UUID.fromString(entity.id), ticket.assetId)
            } catch (expected: Exception) {
                // The entry stays gated and this block is retried on the next cycle.
                continue
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
