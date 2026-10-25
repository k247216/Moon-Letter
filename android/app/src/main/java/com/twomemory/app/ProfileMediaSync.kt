package com.twomemory.app

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.twomemory.network.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Uses the same authenticated media service as records; preferences are a local cache. */
object ProfileMediaSync {
    suspend fun uploadOwn(context: Context, session: SyncSession.Session) {
        val prefs = context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE)
        val uri = prefs.getString("ownAvatar", null) ?: return
        val owner = session.coupleId.toString() + ":" + session.userId
        if (prefs.getString("uploadedAvatar", null) == owner + ":" + uri) return
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } ?: return
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val api = RetrofitMediaApi.create()
        val ticket = api.createUpload(session.baseUrl, session.token, MediaCreateCommand(session.userId, session.coupleId,
            UUID.nameUUIDFromBytes((owner + checksum).toByteArray()), "IMAGE", "image/png", bytes.size.toLong(),
            bounds.outWidth.coerceAtLeast(1), bounds.outHeight.coerceAtLeast(1), checksum, purpose = "AVATAR"))
        if (ticket.url.startsWith("local://")) api.uploadData(session.baseUrl, session.token, ticket.assetId, bytes, "image/png")
        else {
            api.uploadToUrl(ticket.url, bytes, "image/png")
            api.completeUpload(session.baseUrl, session.token, ticket.assetId, checksum)
        }
        RetrofitSessionApi.create().updateOwnAvatar(session.baseUrl, session.token, session.coupleId, session.userId, ticket.assetId)
        prefs.edit().putString("uploadedAvatar", owner + ":" + uri).apply()
    }

    suspend fun refresh(context: Context, session: SyncSession.Session, space: SpaceViewDto) {
        currentCoroutineContext().ensureActive()
        val binding = SyncSession.captureBinding(context) ?: return
        if (binding.session != session) return
        refresh(context, binding, space)
    }

    internal suspend fun refresh(context: Context, binding: SyncSession.BindingSnapshot, space: SpaceViewDto) {
        val session = binding.session
        val job = currentCoroutineContext()
        val prefs = context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE)
        val member = space.members.firstOrNull { it.userId != session.userId }
        val asset = member?.profile?.avatarAssetId
        synchronized(SyncSession.bindingLock) {
            job.ensureActive()
            if (!SyncSession.isCurrentBinding(context, binding)) return
            if (asset == null) { prefs.edit().remove("partnerAvatar").remove("partnerAvatarAsset").apply(); return }
        }
        if (asset == null) return
        val identity = session.coupleId.toString() + ":" + asset
        val oldFile = prefs.getString("partnerAvatar", null)?.let { File(Uri.parse(it).path.orEmpty()) }
        if (prefs.getString("partnerAvatarAsset", null) == identity && oldFile?.isFile == true) return
        try {
            val bytes = RetrofitMediaApi.create().download(session.baseUrl, session.token, asset)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Invalid avatar")
            decoded.recycle()
            val directory = File(context.filesDir, "profile-avatars").apply { mkdirs() }
            val temporary = File.createTempFile("avatar-", ".part", directory)
            try {
                // Slow storage must not hold the lock used by UI session reads.
                java.io.FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
                synchronized(SyncSession.bindingLock) {
                    job.ensureActive()
                    if (!SyncSession.isCurrentBinding(context, binding)) return
                    val file = File(directory, "partner-$asset.png")
                    java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    prefs.edit().putString("partnerAvatar", Uri.fromFile(file).toString()).putString("partnerAvatarAsset", identity).apply()
                }
            } finally { temporary.delete() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Retry at the next foreground cycle. */ }
    }
}
