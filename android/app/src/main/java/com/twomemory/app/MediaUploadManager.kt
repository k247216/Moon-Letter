package com.twomemory.app

import android.content.Context
import android.graphics.BitmapFactory
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.network.MediaCreateCommand
import com.twomemory.network.RetrofitMediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Uploads local media independently of text synchronization. A failed block keeps its whole entry
 * out of the outbox queue, so the record stays an unsent draft here instead of
 * becoming a published entry with a missing picture on both phones.
 */
object MediaUploadManager {

    private val mediaApi = RetrofitMediaApi.create()

    suspend fun uploadPendingImages(context: Context) {
        val session = SyncSession.load(context) ?: return
        uploadPendingImages(session, AppDatabase.build(context, session.databaseName))
    }

    internal suspend fun uploadPendingImages(
        session: SyncSession.Session,
        database: AppDatabase,
        api: com.twomemory.network.MediaApi = mediaApi,
    ) {
        val writer = LocalEntryWriter(database)
        for (entity in database.entryDao().mediaBlocksWithoutAsset()) {
            currentCoroutineContext().ensureActive()
            if (entity.authorId != session.userId.toString()) continue
            val entry = database.entryDao().findEntry(entity.entryId) ?: continue
            if (entry.deleted || entry.coupleId != session.coupleId.toString()) continue
            try {
                val payload = org.json.JSONObject(entity.payload)
                val file = File(payload.optString("localPath"))
                if (!file.isFile) continue
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (entity.type == "IMAGE") BitmapFactory.decodeFile(file.absolutePath, bounds)
                val checksum = fileSha256(file)
                val ticket = api.createUpload(
                    session.baseUrl,
                    session.token,
                    MediaCreateCommand(
                        ownerId = session.userId,
                        coupleId = session.coupleId,
                        operationId = UUID.fromString(entity.id),
                        kind = entity.type,
                        mimeType = payload.optString("mime", "image/jpeg"),
                        byteSize = file.length(),
                        width = if (entity.type == "IMAGE") bounds.outWidth.coerceAtLeast(1) else 1,
                        height = if (entity.type == "IMAGE") bounds.outHeight.coerceAtLeast(1) else 1,
                        sha256 = checksum,
                    ),
                )
                val mimeType = payload.optString("mime", "image/jpeg")
                if (ticket.url.startsWith("local://")) {
                    api.uploadFileData(session.baseUrl, session.token, ticket.assetId, file, mimeType)
                } else {
                    api.uploadFileToUrl(ticket.url, file, mimeType)
                    api.completeUpload(session.baseUrl, session.token, ticket.assetId, checksum)
                }
                writer.attachAsset(UUID.fromString(entity.id), ticket.assetId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                // The entry stays gated and this block is retried on the next cycle.
                continue
            }
        }
    }

}

internal suspend fun fileSha256(file: File): String = withContext(Dispatchers.IO) {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}
