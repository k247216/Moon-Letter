package com.twomemory.app

import android.content.Context
import android.content.SharedPreferences
import com.twomemory.network.CapsuleApi
import com.twomemory.network.CapsuleDto
import com.twomemory.network.CapsuleRequest
import com.twomemory.network.CapsuleValidation
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CapsuleCard(val id: String, val coupleId: String, val authorId: String,
    val title: String, val body: String?, val unlockDate: String, val visibility: String,
    val unlocked: Boolean, val updatedAt: String) {
    fun readableBody(userId: String): String? = dto().readableBody(userId)
    fun request() = CapsuleRequest(title, requireNotNull(body), unlockDate, visibility)
    internal fun dto() = CapsuleDto(id, coupleId, authorId, title, body, unlockDate, visibility, unlocked, updatedAt)
}

private fun CapsuleDto.card() = CapsuleCard(id, coupleId, authorId, title, body, unlockDate, visibility, unlocked, updatedAt)

/** Only confirmed server data. Separate from all legacy local capsule/draft preferences. */
class CapsuleStore(context: Context, private val session: SyncSession.Session) {
    private val scope = "${session.storageOrigin.trimEnd('/')}|${session.coupleId}|${session.userId}"
    val preferences: SharedPreferences = context.getSharedPreferences("moon_letter_capsules_" +
        MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }, Context.MODE_PRIVATE)
    fun cards(): List<CapsuleCard> = runCatching {
        val items = CapsuleValidation.parseSnapshot(preferences.getString("cards", "[]") ?: "[]",
            session.coupleId.toString(), session.userId.toString())
        items.map { it.card().copy(body = it.readableBody(session.userId.toString())) }
    }.getOrDefault(emptyList())
    fun replace(cards: List<CapsuleCard>) {
        val json = CapsuleValidation.serializeSnapshot(cards.map { it.dto() }, session.coupleId.toString(), session.userId.toString())
        check(preferences.edit().putString("cards", json).commit()) { "信件已在云端确认，但本机未能保存，请刷新重试" }
    }
    /** Reminders are based on confirmed unlock state, never the phone's clock. */
    fun due(userId: String): List<CapsuleCard> = if (userId != session.userId.toString()) emptyList() else cards().filter {
        it.unlocked && it.readableBody(userId) != null && preferences.getString("seen:${it.id}", null) != it.updatedAt
    }
    fun dismiss(card: CapsuleCard) {
        require(cards().any { it.id == card.id && it.updatedAt == card.updatedAt && it.unlocked })
        preferences.edit().putString("seen:${card.id}", card.updatedAt).apply()
    }
}

object CapsuleCloud {
    private val mutations = Mutex()
    private val snapshots = Mutex()
    private val generation = AtomicLong()
    private val bindingObservers = java.util.IdentityHashMap<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>()
    private val bindingKeys = setOf("token", "coupleId", "userId", "baseUrl", "storageOrigin", "databaseName")
    private fun observeBinding(context: Context) = synchronized(SyncSession.bindingLock) {
        val prefs = SyncSession.sessionPrefs(context)
        if (!bindingObservers.containsKey(prefs)) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key in bindingKeys) generation.incrementAndGet()
            }
            bindingObservers[prefs] = listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
    }
    fun store(context: Context, session: SyncSession.Session) = CapsuleStore(context.applicationContext, session)
    fun api(session: SyncSession.Session) = CapsuleApi(session.baseUrl, session.token, session.coupleId)
    /** False means offline, rejected data or an obsolete binding; old confirmed snapshot survives. */
    suspend fun refresh(context: Context): Boolean {
        observeBinding(context)
        val session = SyncSession.load(context) ?: return false
        return try {
            snapshots.withLock {
                val started = mutations.withLock { generation.get() }
                val response = api(session).read(session.userId.toString())
                mutations.withLock {
                    val job = currentCoroutineContext()
                    synchronized(SyncSession.bindingLock) {
                        job.ensureActive()
                        if (generation.get() != started || SyncSession.load(context) != session) false
                        else { store(context, session).replace(response.map { it.card() }); true }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
    suspend fun save(context: Context, card: CapsuleCard, expectedSession: SyncSession.Session? = null): CapsuleCard {
        observeBinding(context)
        val session = requireNotNull(SyncSession.load(context)) { "请先连接共同空间" }
        check(expectedSession == null || expectedSession == session) { "空间已切换，请重新打开信箱" }
        require(card.coupleId == session.coupleId.toString() && card.authorId == session.userId.toString()) { "只能编辑自己写的信" }
        return mutations.withLock {
            check(SyncSession.load(context) == session) { "空间已切换，请重新打开信箱" }
            val started = generation.incrementAndGet()
            val saved = api(session).save(card.id, card.request(), session.userId.toString()).card()
            val job = currentCoroutineContext()
            synchronized(SyncSession.bindingLock) {
                job.ensureActive()
                check(SyncSession.load(context) == session && generation.get() == started) { "空间已切换，未将结果写入当前信箱；回到原空间刷新可确认云端状态" }
                val cache = store(context, session)
                cache.replace(cache.cards().filterNot { it.id == saved.id } + saved)
            }
            // Scheduling failure must not misreport a server-confirmed save as unsuccessful.
            runCatching { com.twomemory.app.notifications.CloudKeepsakeReminders.schedule(context) }
            saved
        }
    }
    suspend fun delete(context: Context, card: CapsuleCard, expectedSession: SyncSession.Session? = null) {
        observeBinding(context)
        val session = requireNotNull(SyncSession.load(context)) { "请先连接共同空间" }
        check(expectedSession == null || expectedSession == session) { "空间已切换，请重新打开信箱" }
        require(card.coupleId == session.coupleId.toString() && card.authorId == session.userId.toString()) { "只能删除自己写的信" }
        mutations.withLock {
            check(SyncSession.load(context) == session) { "空间已切换，请重新打开信箱" }
            val started = generation.incrementAndGet()
            api(session).delete(card.id)
            val job = currentCoroutineContext()
            synchronized(SyncSession.bindingLock) {
                job.ensureActive()
                check(SyncSession.load(context) == session && generation.get() == started) { "空间已切换，请回到原空间刷新确认删除结果" }
                val cache = store(context, session)
                cache.replace(cache.cards().filterNot { it.id == card.id })
            }
            runCatching { com.twomemory.app.notifications.CloudKeepsakeReminders.schedule(context) }
        }
    }
}
