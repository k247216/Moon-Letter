package com.twomemory.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.twomemory.database.AppDatabase
import com.twomemory.network.MediaCreateCommand
import com.twomemory.network.RetrofitMediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

/**
 * Best-effort image upload (Task 13): before the sync engine pushes, every
 * local IMAGE block without an asset is uploaded, its asset id recorded in
 * the block payload, and the outbox payload is refreshed with the asset id.
 * Failures are silent here — the text always syncs, images retry next cycle.
 */
object MediaUploadManager {

    private val mediaApi = RetrofitMediaApi.create()

    suspend fun uploadPendingImages(context: Context) {
        val session = SyncSession.load(context) ?: return
        val database = AppDatabase.build(context)
        val pending = database.entryDao().imageBlocksWithoutAsset()
        for (entity in pending) {
            try {
                val payload = org.json.JSONObject(entity.payload)
                val localUri = payload.optString("uri", "")
                if (localUri.isBlank()) continue
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(Uri.parse(localUri))?.use {
                        it.readBytes()
                    }
                } ?: continue

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val width = if (options.outWidth > 0) options.outWidth else 1
                val height = if (options.outHeight > 0) options.outHeight else 1

                val ticket = mediaApi.createUpload(
                    session.baseUrl,
                    session.token,
                    MediaCreateCommand(
                        ownerId = session.userId,
                        coupleId = session.coupleId,
                        operationId = UUID.fromString(entity.id),
                        kind = "IMAGE",
                        mimeType = entity.mimeType(),
                        byteSize = bytes.size.toLong(),
                        width = width,
                        height = height,
                        sha256 = sha256(bytes),
                    ),
                )
                mediaApi.uploadData(session.baseUrl, session.token, ticket.assetId, bytes, entity.mimeType())

                // Record the asset in the stored block and its outbox payload.
                payload.put("assetId", ticket.assetId.toString())
                val updated = entity.copy(payload = payload.toString(), assetId = ticket.assetId.toString())
                database.entryDao().insertBlocks(listOf(updated))
                database.outboxDao().replacePayloadForEntity(entity.entryId, payload.toString())
            } catch (expected: Exception) {
                // Best effort: text syncs regardless; images retry next cycle.
                continue
            }
        }
    }

    private fun com.twomemory.database.EntryBlockEntity.mimeType(): String = when {
        payload.contains("png") -> "image/png"
        payload.contains("webp") -> "image/webp"
        else -> "image/jpeg"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
