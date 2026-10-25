package com.twomemory.app

import android.content.Context
import com.twomemory.network.*
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock

data class OccasionCard(val id: String, val coupleId: String, val authorId: String, val kind: String,
    val title: String, val body: String, val date: String, val repeatsYearly: Boolean,
    val recipientId: String?, val photoAssetId: String?, val updatedAt: String, val visibility: String = "SHARED") {
    fun isDue(today: LocalDate): Boolean = runCatching {
        val start = LocalDate.parse(date)
        !today.isBefore(start) && (if (repeatsYearly) start.withYear(today.year) == today else start == today)
    }.getOrDefault(false)
    fun opensFor(userId: String) = if (visibility == "PRIVATE") authorId == userId else kind != "SURPRISE" || recipientId == userId
    fun request() = OccasionRequest(kind, title, body, date, repeatsYearly, recipientId, photoAssetId, visibility)
}

/** Account/space/origin scoped cache; never equates a local edit with cloud delivery. */
class OccasionStore(context: Context, scope: String) {
    val preferences = context.getSharedPreferences("moon_letter_occasions_" +
        java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }, Context.MODE_PRIVATE)
    fun cards(): List<OccasionCard> = runCatching {
        val array = JSONArray(preferences.getString("cards", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            OccasionCard(item.getString("id"), item.getString("coupleId"), item.getString("authorId"), item.getString("kind"),
                item.getString("title"), item.optString("body"), item.getString("date"), item.optBoolean("repeatsYearly"),
                item.optString("recipientId").takeIf { it.isNotBlank() && it != "null" },
                item.optString("photoAssetId").takeIf { it.isNotBlank() && it != "null" }, item.optString("updatedAt"), item.optString("visibility", "SHARED"))
        }
    }.getOrDefault(emptyList())
    fun replace(cards: List<OccasionCard>) {
        val array = JSONArray(cards.map { JSONObject().put("id", it.id).put("coupleId", it.coupleId).put("authorId", it.authorId)
            .put("kind", it.kind).put("title", it.title).put("body", it.body).put("date", it.date)
            .put("repeatsYearly", it.repeatsYearly).put("recipientId", it.recipientId).put("photoAssetId", it.photoAssetId).put("updatedAt", it.updatedAt).put("visibility", it.visibility) })
        preferences.edit().putString("cards", array.toString()).apply()
    }
    fun dismiss(card: OccasionCard, today: LocalDate) { preferences.edit().putString("seen:${card.id}", today.toString()).apply() }
    fun due(user: String, today: LocalDate) = cards().filter { it.opensFor(user) && it.isDue(today) &&
        preferences.getString("seen:${it.id}", null) != today.toString() }
}

object OccasionCloud {
    private val mutations = kotlinx.coroutines.sync.Mutex()
    private val snapshots = kotlinx.coroutines.sync.Mutex()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    fun store(context: Context, session: SyncSession.Session) = OccasionStore(context,
        "${session.storageOrigin}|${session.coupleId}|${session.userId}")
    fun api(session: SyncSession.Session) = OccasionApi(session.baseUrl, session.token, session.coupleId)
    private fun OccasionDto.card() = OccasionCard(id, coupleId, authorId, kind, title, body, date, repeatsYearly, recipientId, photoAssetId, updatedAt, visibility ?: "SHARED")
    /** True only when a validated server snapshot was committed for this exact binding. */
    suspend fun refresh(context: Context): Boolean {
        val session = SyncSession.load(context) ?: return false
        return try {
            snapshots.withLock {
                val started = mutations.withLock { generation.get() }
                val response = api(session).read()
                require(response.all { it.coupleId == session.coupleId.toString() }) { "wrong occasion space" }
                require(response.all { it.visibility != "PRIVATE" || it.authorId == session.userId.toString() }) { "private partner occasion" }
                val cards = response.map { it.card() }
                mutations.withLock {
                    val job = kotlinx.coroutines.currentCoroutineContext()
                    synchronized(SyncSession.bindingLock) {
                        job.ensureActive()
                        if (generation.get() != started || SyncSession.load(context) != session) false
                        else { store(context, session).replace(cards); true }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false /* Old backend or offline: retain last confirmed snapshot. */ }
    }
    suspend fun save(context: Context, card: OccasionCard): OccasionCard = mutations.withLock {
        val session = requireNotNull(SyncSession.load(context))
        require(card.coupleId == session.coupleId.toString())
        if (card.visibility == "PRIVATE") com.twomemory.network.PrivacyApi(session.baseUrl, session.token).requireSupport()
        generation.incrementAndGet()
        val saved = api(session).save(card.id, card.request()).card()
        require(saved.visibility == card.visibility) { "服务端没有确认可见范围" }
        require(saved.coupleId == session.coupleId.toString())
        check(SyncSession.load(context) == session) { "空间已切换，请重新打开重要日子" }
        val cache = store(context, session)
        cache.replace(cache.cards().filterNot { it.id == saved.id } + saved)
        runCatching { com.twomemory.app.notifications.CloudKeepsakeReminders.schedule(context) }
        saved
    }
    suspend fun delete(context: Context, card: OccasionCard) = mutations.withLock {
        val session = requireNotNull(SyncSession.load(context))
        require(card.coupleId == session.coupleId.toString())
        generation.incrementAndGet()
        api(session).delete(card.id)
        check(SyncSession.load(context) == session)
        val cache = store(context, session); cache.replace(cache.cards().filterNot { it.id == card.id })
    }
    suspend fun uploadPhoto(context: Context, path: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val session = requireNotNull(SyncSession.load(context))
        val file = java.io.File(path)
        require(file.isFile && file.length() in 1..(20L * 1024 * 1024)) { "请选20 MB以内的照片" }
        val bytes = file.readBytes()
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "照片无法读取，请重新选择" }
        val mime = requireNotNull(bounds.outMimeType)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val api = RetrofitMediaApi.create()
        val ticket = api.createUpload(session.baseUrl, session.token, MediaCreateCommand(session.userId, session.coupleId,
            UUID.nameUUIDFromBytes(("occasion:${session.coupleId}:${session.userId}:$sha").toByteArray()), "IMAGE", mime,
            bytes.size.toLong(), bounds.outWidth, bounds.outHeight, sha, purpose = "RECORD"))
        if (ticket.url.startsWith("local://")) api.uploadData(session.baseUrl, session.token, ticket.assetId, bytes, mime)
        else { api.uploadToUrl(ticket.url, bytes, mime); api.completeUpload(session.baseUrl, session.token, ticket.assetId, sha) }
        check(SyncSession.load(context) == session) { "空间已切换，请重新选择照片" }
        ticket.assetId.toString()
    }
}
