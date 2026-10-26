package com.twomemory.app

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.twomemory.app.notifications.MoonLetterNotifier
import com.twomemory.app.notifications.NewEntryNotice
import com.twomemory.app.notifications.WeeklyReviewWorker
import com.twomemory.database.AppDatabase
import com.twomemory.database.RoomSyncStore
import com.twomemory.model.EntrySyncPhase
import com.twomemory.model.QueuedOperation
import com.twomemory.model.entrySyncPhase
import com.twomemory.network.RetrofitCoupleDiaryApi
import com.twomemory.sync.SyncEngine
import com.twomemory.sync.SyncEngineRegistry
import com.twomemory.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.twomemory.sync.runSyncCycle
import com.twomemory.sync.SyncOutcome

/**
 * Session state persisted in SharedPreferences: the device token, couple id
 * and server base URL. Populated by the setup screen, read here to build the
 * real sync pipeline. The token is sealed with an AndroidKeyStore key on
 * devices that provide one (see [TokenCipher]).
 */
object SyncSession {
    private val foregroundMutex = Mutex()
    internal val bindingLock = Any()
    private val bindingGeneration = java.util.concurrent.atomic.AtomicLong()
    private val mutableBindingRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    internal val bindingRevision: kotlinx.coroutines.flow.StateFlow<Long> = mutableBindingRevision
    private fun advanceBindingGeneration() = synchronized(bindingLock) {
        bindingGeneration.incrementAndGet().also { mutableBindingRevision.value = it }
    }
    private val bindingObservers = java.util.IdentityHashMap<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>()
    private val bindingKeys = setOf("token", "coupleId", "userId", "baseUrl", "storageOrigin", "databaseName")

    internal data class BindingSnapshot(val session: Session, val generation: Long)

    /** Capture before a request; generation also rejects an A -> B -> A result. */
    internal fun captureBinding(context: Context): BindingSnapshot? = synchronized(bindingLock) {
        val prefs = sessionPrefs(context)
        if (!bindingObservers.containsKey(prefs)) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key in bindingKeys) advanceBindingGeneration()
            }
            bindingObservers[prefs] = listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
        val session = load(context) ?: return@synchronized null
        BindingSnapshot(session, bindingGeneration.get())
    }

    /** Call inside bindingLock together with ensureActive and the cache write. */
    internal fun isCurrentBinding(context: Context, snapshot: BindingSnapshot): Boolean = synchronized(bindingLock) {
        val session = load(context)
        session == snapshot.session && bindingGeneration.get() == snapshot.generation
    }

    /** Prefills the setup field on a debug build; blank on a release build. */
    val DEFAULT_BASE_URL: String
        get() = ServerAddress.resolve(null, BuildConfig.DEBUG).orEmpty()

    private const val PREFS = "moon_letter_session"

    fun save(context: Context, token: String, coupleId: UUID, userId: UUID, baseUrl: String = DEFAULT_BASE_URL) = synchronized(bindingLock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val origin = baseUrl.trimEnd('/')
        val sameSpace = prefs.getString("baseUrl", null)?.trimEnd('/') == origin &&
            prefs.getString("coupleId", null) == coupleId.toString()
        val editor = prefs.edit()
        // Register the previous owner BEFORE replacing the binding, including recovery
        // with an unreadable token. Never hand their legacy private cache to a new member.
        val previousScope = storedDatabaseScope(prefs)
        val previousDatabase = previousScope?.let {
            databaseForScope(prefs, editor, it, prefs.getString("databaseName", "moon-letter.db"))
        }
        val storageOrigin = if (sameSpace) prefs.getString("storageOrigin", origin) ?: origin else origin
        val scope = databaseScope(storageOrigin, coupleId.toString(), userId.toString())
        val databaseName = if (scope == previousScope) previousDatabase!!
            else databaseForScope(prefs, editor, scope)
        editor
            .putString("token", TokenCipher.seal(token))
            .putString("coupleId", coupleId.toString())
            .putString("userId", userId.toString())
            .putString("baseUrl", origin)
            .putString("storageOrigin", storageOrigin)
            .putString("databaseName", databaseName)
            .remove("partnerName")
        // Invalidate synchronously: preference listeners may be posted to the
        // main thread, after a background A -> B -> A transition has finished.
        advanceBindingGeneration()
        commitBinding(prefs, editor)
    }

    private fun databaseScope(origin: String, couple: String, user: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("${origin.trimEnd('/')}|$couple|$user".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun storedDatabaseScope(prefs: SharedPreferences): String? {
        val origin = prefs.getString("storageOrigin", null) ?: prefs.getString("baseUrl", null) ?: return null
        val couple = prefs.getString("coupleId", null) ?: return null
        val user = prefs.getString("userId", null) ?: return null
        if (runCatching { UUID.fromString(couple); UUID.fromString(user) }.isFailure) return null
        return databaseScope(origin, couple, user)
    }

    /** Persistent aliases migrate ownership without copying/deleting an open SQLite
     * file or its WAL. Records, outbox, cursor and media paths stay intact for A;
     * B gets a user-scoped database. An already-owned file cannot be reassigned. */
    private fun databaseForScope(prefs: SharedPreferences, editor: SharedPreferences.Editor,
        scope: String, legacy: String? = null): String {
        val candidate = prefs.getString("databaseScope:$scope", null) ?: legacy
        val name = candidate?.takeIf {
            it.matches(Regex("[a-zA-Z0-9_-]+\\.db")) &&
                prefs.getString("databaseOwner:$it", null).let { owner -> owner == null || owner == scope }
        // Keep filesystem paths short (notably SQLite WAL paths in Windows tests).
        // Ownership remains checked against the complete SHA-256 account scope.
        } ?: "moon-letter-${scope.take(32)}.db"
        editor.putString("databaseScope:$scope", name).putString("databaseOwner:$name", scope)
        return name
    }

    /** A setup request started before an endpoint change must not replace its result. */
    internal fun saveIfCurrent(context: Context, expected: Session?, token: String, coupleId: UUID, userId: UUID, baseUrl: String): Boolean = synchronized(bindingLock) {
        if (load(context) != expected) return@synchronized false
        save(context, token, coupleId, userId, baseUrl)
        true
    }

    data class Session(val token: String, val coupleId: UUID, val userId: UUID, val baseUrl: String,
        val databaseName: String = "moon-letter.db", val storageOrigin: String = baseUrl.trimEnd('/'))

    internal suspend fun <T> withPausedForeground(block: suspend () -> T): T = foregroundMutex.withLock { block() }
    internal suspend fun <T> withCurrentForegroundSession(context: Context, block: suspend (Session) -> T): T? = foregroundMutex.withLock {
        load(context)?.let { block(it) }
    }

    data class Names(val own: String, val partner: String, val coralSignerId: UUID? = null)

    private fun signerPreferenceKey(prefs: SharedPreferences) = "coralSigner_" + recordingScopeKey(
        "${prefs.getString("storageOrigin", "")}|${prefs.getString("coupleId", "")}")

    /** The file [loadNames] reads: a page showing those names listens here. */
    fun sessionPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Persisted display names; blank when never fetched. */
    fun loadNames(context: Context): Names {
        val prefs = sessionPrefs(context)
        return Names(
            own = prefs.getString("ownName", null).orEmpty(),
            partner = prefs.getString("partnerName", null).orEmpty(),
            coralSignerId = prefs.getString(signerPreferenceKey(prefs), null)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
        )
    }

    /** Best-effort fetch of real display names from the space profile. */
    suspend fun refreshNames(context: Context) {
        currentCoroutineContext().ensureActive()
        val binding = captureBinding(context) ?: return
        val session = binding.session
        try {
            val space = com.twomemory.network.RetrofitSessionApi.create()
                .readSpace(session.baseUrl, session.token, session.coupleId)
            val own = space.members.firstOrNull { it.userId == session.userId }?.profile?.displayName.orEmpty()
            val partner = space.members.firstOrNull { it.userId != session.userId }?.profile?.displayName.orEmpty()
            val coralSigner = space.members.map { it.userId }.distinct().takeIf { it.size == 2 }?.minByOrNull { it.toString() }
            // A name the space does not have yet stays unknown rather than blanking
            // the one this device already shows.
            val job = currentCoroutineContext()
            synchronized(bindingLock) {
                job.ensureActive()
                if (!isCurrentBinding(context, binding)) return
                if (own.isBlank() && partner.isBlank()) return
                val editor = sessionPrefs(context).edit()
                if (own.isNotBlank()) editor.putString("ownName", own)
                if (partner.isNotBlank()) editor.putString("partnerName", partner)
                coralSigner?.let { editor.putString(signerPreferenceKey(sessionPrefs(context)), it.toString()) }
                editor.apply()
            }
            ProfileMediaSync.refresh(context, binding, space)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Retain confirmed profile data; retry next refresh. */ }
    }

    /**
     * Stores [displayName] as this device owner's name in the space, then
     * refreshes the cached names. Throws when the server refuses, so the screen
     * can say the name was not saved instead of showing a local-only rename.
     */
    suspend fun renameOwn(context: Context, displayName: String): String {
        val session = load(context) ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
        val profile = com.twomemory.network.RetrofitSessionApi.create()
            .updateOwnProfile(session.baseUrl, session.token, session.coupleId, session.userId, displayName)
        refreshNames(context)
        return profile.displayName.ifBlank { displayName }
    }

    /** Reads the couple-scoped anniversary; a missing record is a normal empty state. */
    suspend fun readAnniversary(context: Context): com.twomemory.network.AnniversaryViewDto? {
        val session = load(context) ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
        return com.twomemory.network.RetrofitSessionApi.create()
            .readAnniversary(session.baseUrl, session.token, session.coupleId)
    }

    /** Saves an ISO Gregorian anniversary in the shared space, not only in local preferences. */
    suspend fun saveAnniversary(
        context: Context,
        title: String,
        date: String,
        repeatsYearly: Boolean,
    ): com.twomemory.network.AnniversaryViewDto {
        val session = load(context) ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
        val parsed = java.time.LocalDate.parse(date.trim())
        return com.twomemory.network.RetrofitSessionApi.create()
            .saveAnniversary(
                session.baseUrl,
                session.token,
                session.coupleId,
                com.twomemory.network.SaveAnniversaryRequestDto(
                    title = title.trim(),
                    calendarType = "GREGORIAN",
                    month = parsed.monthValue,
                    day = parsed.dayOfMonth,
                    startYear = parsed.year,
                    repeatsYearly = repeatsYearly,
                ),
            )
    }

    /**
     * Mints a fresh pairing code for this space. The server decides what the code
     * opens: a free member slot makes it an invitation, a full space binds it to
     * the other member's own slot, so a lost or reinstalled phone comes back as
     * the same person instead of a third member. Throws when the server refuses,
     * so the screen never shows a code that was never issued.
     */
    suspend fun mintPairingCode(context: Context): com.twomemory.network.PairingTokenResultDto {
        val session = load(context) ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
        return com.twomemory.network.RetrofitSessionApi.create()
            .pairingToken(session.baseUrl, session.token, session.coupleId)
    }

    /**
     * Missing/unavailable credentials return null without clearing the original
     * binding. Keystore lock/corruption must not silently erase space identity.
     */
    fun load(context: Context): Session? = synchronized(bindingLock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString("token", null)
        val coupleId = prefs.getString("coupleId", null)
        val userId = prefs.getString("userId", null)
        if (stored.isNullOrBlank() || coupleId.isNullOrBlank() || userId.isNullOrBlank()) return@synchronized null
        val baseUrl = ServerAddress.resolve(prefs.getString("baseUrl", null), BuildConfig.DEBUG)
            ?: return@synchronized null
        runCatching {
            val token = TokenCipher.unseal(stored)
            val scope = databaseScope(
                prefs.getString("storageOrigin", baseUrl) ?: baseUrl, coupleId, userId)
            val databaseEditor = prefs.edit()
            val databaseName = databaseForScope(prefs, databaseEditor, scope,
                prefs.getString("databaseName", "moon-letter.db"))
            if (prefs.getString("databaseScope:$scope", null) != databaseName ||
                prefs.getString("databaseOwner:$databaseName", null) != scope ||
                prefs.getString("databaseName", null) != databaseName) {
                commitBinding(prefs, databaseEditor.putString("databaseName", databaseName))
            }
            val session = Session(
                token = token,
                coupleId = UUID.fromString(coupleId),
                userId = UUID.fromString(userId),
                baseUrl = baseUrl,
                databaseName = databaseName,
                storageOrigin = prefs.getString("storageOrigin", baseUrl)?.trimEnd('/') ?: baseUrl.trimEnd('/'),
            )
            if (!stored.startsWith("enc:")) {
                val encrypted = TokenCipher.seal(token)
                if (!prefs.edit().putString("token",encrypted).commit()) {
                    prefs.edit().putString("token",stored).commit()
                    error("secure token could not be persisted")
                }
            }
            session
        }.getOrElse {
            null
        }
    }

    /** Builds the real pipeline: RoomSyncStore -> Retrofit -> SyncEngine. */
    fun engine(context: Context, session: Session): SyncEngine {
        val database = AppDatabase.build(context, session.databaseName)
        val api = RetrofitCoupleDiaryApi.create(session.baseUrl) { session.token }
        return SyncEngine(api, RoomSyncStore(database), session.coupleId)
    }

    /** Lets any page fetch a picture that only the server holds. */
    fun installPhotoSource(session: Session) {
        val api = com.twomemory.network.RetrofitMediaApi.create()
        com.twomemory.designsystem.EntryPhotos.installSource(
        scope = "${session.storageOrigin}|${session.coupleId}|${session.userId}", bytes = { assetId ->
            api.download(session.baseUrl, session.token, java.util.UUID.fromString(assetId))
        }, file = { assetId, file ->
            api.downloadToFile(session.baseUrl, session.token, java.util.UUID.fromString(assetId), file)
        }, preview = { assetId ->
            api.downloadPreview(session.baseUrl, session.token, java.util.UUID.fromString(assetId))
        }, reading = { assetId ->
            api.downloadReadingPreview(session.baseUrl, session.token, java.util.UUID.fromString(assetId))
        })
    }

    fun installPhotoSource(context: Context, session: Session) = synchronized(bindingLock) {
        if (load(context) == session) installPhotoSource(session)
    }

    /**
     * Writes the record through Room + outbox and publishes it in the same batch,
     * then triggers a sync. Offline this leaves a private draft plus two pending
     * operations; the record reaches the partner as soon as the device reconnects.
     */
    suspend fun publish(
        context: Context,
        state: com.twomemory.editor.EditorUiState,
        mode: com.twomemory.model.EntryMode = com.twomemory.model.EntryMode.PERSONAL,
    ): UUID {
        val session = load(context) ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
        if (state.visibility == com.twomemory.model.EntryVisibility.PRIVATE) {
            com.twomemory.network.PrivacyApi(session.baseUrl, session.token).requireSupport()
            check(load(context) == session) { "空间已切换，请重新保存" }
        }
        val entryId = UUID.randomUUID()
        com.twomemory.database.LocalEntryWriter(AppDatabase.build(context)).save(
            com.twomemory.model.LocalEntryCommand(
                coupleId = session.coupleId,
                authorId = session.userId,
                mode = mode,
                occurredAt = state.occurrenceTime,
                occurredTimezone = state.timezone,
                title = state.title.ifBlank { null },
                blocks = state.blocks(session.userId),
                entryId = entryId,
                visibility = if (mode == com.twomemory.model.EntryMode.PERSONAL) state.visibility else com.twomemory.model.EntryVisibility.SHARED,
            ),
            publish = true,
        )
        triggerSync(context)
        return entryId
    }

    /**
     * Live delivery state for one record, read from this device's own queue: an
     * operation leaves the outbox only when the server has taken it, so an empty
     * queue is the only honest basis for claiming 已同步.
     */
    fun observeSyncPhase(context: Context, entryId: UUID): Flow<EntrySyncPhase> =
        AppDatabase.build(context).outboxDao().observeForEntity(entryId.toString())
            .map { rows -> entrySyncPhase(rows.map { QueuedOperation(it.state, it.attemptCount) }) }
            .flowOn(Dispatchers.IO)

    /**
     * A rejected operation is parked for good by the engine, so this is the only
     * way such a record ever moves again. It re-queues and immediately syncs.
     */
    suspend fun retrySync(context: Context, entryId: UUID) {
        AppDatabase.build(context).outboxDao()
            .requeueRejected(entryId.toString(), System.currentTimeMillis())
        triggerSync(context)
    }

    /** Append an authored perspective atomically; never create another entry or replace a partner's block. */
    suspend fun appendPerspective(context: Context, entryId: UUID, state: com.twomemory.editor.EditorUiState): UUID {
        val session = load(context) ?: error("设备尚未绑定空间")
        val blocks = state.blocks(session.userId)
        require(blocks.isNotEmpty()) { "请先写下你的视角或添加内容" }
        val database = AppDatabase.build(context)
        database.withTransaction {
            val entry = requireNotNull(database.entryDao().findEntry(entryId.toString())) { "记录不存在" }
            require(entry.coupleId == session.coupleId.toString() && !entry.deleted && entry.mode == "COLLABORATIVE") { "这不是当前空间的共同记录" }
            require(entry.state == "PUBLISHED" || entry.authorId == session.userId.toString()) { "记录尚未发布" }
            val last = database.entryDao().blocks(entryId.toString()).maxOfOrNull { it.orderKey } ?: -1L
            require(last <= Long.MAX_VALUE - blocks.size) { "内容过多" }
            blocks.forEachIndexed { index, block ->
                com.twomemory.database.LocalEntryWriter(database).appendBlock(session.coupleId, entryId,
                    block.copy(orderKey = last + index + 1))
            }
        }
        triggerSync(context)
        return entryId
    }

    /** Text first, then the pictures in the order they were chosen. */
    private fun com.twomemory.editor.EditorUiState.blocks(authorId: UUID) =
        mutableListOf<com.twomemory.model.LocalBlockCommand>().apply {
            if (body.isNotBlank()) {
                add(
                    com.twomemory.model.LocalBlockCommand(
                        type = com.twomemory.model.BlockType.TEXT,
                        orderKey = 0,
                        payload = org.json.JSONObject().put("text", body).toString(),
                        authorId = authorId,
                    ),
                )
            }
            photos.forEachIndexed { index, photo ->
                add(
                    com.twomemory.model.LocalBlockCommand(
                        id = photo.id,
                        type = com.twomemory.model.BlockType.IMAGE,
                        orderKey = (index + 1).toLong(),
                        payload = org.json.JSONObject()
                            .put("localPath", photo.localPath)
                            .put("mime", photo.mimeType)
                            .toString(),
                        authorId = authorId,
                    ),
                )
            }
            attachments.forEachIndexed { index, attachment ->
                val payload = runCatching { org.json.JSONObject(attachment.payload) }.getOrDefault(org.json.JSONObject())
                    .apply {
                        attachment.localPath?.let { put("localPath", it) }
                        attachment.mimeType?.let { put("mime", it) }
                    }
                add(
                    com.twomemory.model.LocalBlockCommand(
                        id = attachment.id,
                        type = attachment.type,
                        orderKey = (photos.size + index + 1).toLong(),
                        payload = payload.toString(),
                        authorId = authorId,
                    ),
                )
            }
        }

    /**
     * Wires what the sync worker and the notices need. Runs from
     * [MoonLetterApplication] too, because a background sync can start a process
     * that never showed a screen — and then the engine factory and the notice hook
     * have to already be in place.
     */
    fun installProcessHooks(context: Context) {
        val session = load(context) ?: return
        SyncEngineRegistry.factory = { coupleId ->
            val current = requireNotNull(load(context)) { "设备未绑定" }
            require(current.coupleId == coupleId) { "空间已经切换" }
            engine(context, current)
        }
        SyncEngineRegistry.onCycleCompleted = {
            runCycleSideEffects(context)
            RecordMediaSync.schedule(context)
        }
        MoonLetterNotifier.ensureChannels(context)
        WeeklyReviewWorker.schedule(context)
        // Recover album-only work even if a process died immediately after migration commit.
        runCatching { AlbumCloudSync.enqueue(context) }
        runCatching { RecordMediaSync.enqueue(context) }
    }

    /**
     * What a finished cycle owes the app beyond the rows it applied: the display
     * names converge from the space read, because a rename travels on no channel
     * of its own — the change feed carries records and comments only.
     */
    suspend fun runCycleSideEffects(context: Context, scheduleAlbums: Boolean = true) {
        // Notices need only confirmed local rows: unrelated network refreshes must not delay them.
        NewEntryNotice.announceIfNeeded(context)
        refreshNames(context)
        OccasionCloud.refresh(context)
        CapsuleCloud.refresh(context)
        com.twomemory.app.notifications.CloudKeepsakeReminders.schedule(context)
        try {
            currentCoroutineContext().ensureActive()
            val binding = captureBinding(context) ?: return
            val session = binding.session
            val anniversaryRevision = IndependentAnniversaryStore.currentRevision(context, binding)
            val remote = com.twomemory.network.RetrofitSessionApi.create()
                .readAnniversary(session.baseUrl, session.token, session.coupleId)
            val job = currentCoroutineContext()
            synchronized(bindingLock) {
                job.ensureActive()
                if (isCurrentBinding(context, binding))
                    IndependentAnniversaryStore.acceptConfirmed(context, binding, remote, anniversaryRevision)
                if (isCurrentBinding(context, binding) && remote?.calendarType == "GREGORIAN" && remote.startYear != null) {
                    context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE).edit()
                        .putString("anniversaryName", remote.title)
                        .putString("anniversaryDate", "%04d-%02d-%02d".format(java.util.Locale.ROOT, remote.startYear, remote.month, remote.day))
                        .putBoolean("anniversaryRepeats", remote.repeatsYearly).apply()
                }
            }
            com.twomemory.app.notifications.IndependentAnniversaryReminder.schedule(context, binding)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Retain the confirmed/local date; retry next refresh. */ }
        if (scheduleAlbums) AlbumCloudSync.schedule(context)
    }

    /** One-shot sync from app start / foreground return / manual refresh. */
    suspend fun triggerSync(context: Context, includeSideEffects: Boolean = true) {
        installProcessHooks(context)
        RecordMediaSync.schedule(context)
        withContext(Dispatchers.IO) {
            // Confirmed local rules must recover their device queue even offline.
            // A scheduler failure must not block record synchronization.
            captureBinding(context)?.let { binding ->
                try { com.twomemory.app.notifications.IndependentAnniversaryReminder.schedule(context, binding) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retry on the next foreground entry. */ }
            }
            withCurrentForegroundSession(context) { session ->
                try {
                    val outcome = runSyncCycle { engine(context, requireNotNull(load(context))) }
                    if (outcome != SyncOutcome.FAILURE) {
                        // Foreground text pulls must post notices even while auxiliary network work is busy.
                        NewEntryNotice.announceIfNeeded(context)
                        if (includeSideEffects) runCycleSideEffects(context)
                    }
                    if (outcome == SyncOutcome.RETRY) SyncWorker.enqueue(context, session.coupleId)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { SyncWorker.enqueue(context, session.coupleId) }
            }
        }
    }
}

/** commit(false) can still mutate the in-memory map: restore it before surfacing failure. */
internal fun commitBinding(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
    val before=prefs.all.toMap()
    if (editor.commit()) return
    val rollback=prefs.edit().clear()
    for ((key,value) in before) when(value) {
        is String -> rollback.putString(key,value)
        is Boolean -> rollback.putBoolean(key,value)
        is Int -> rollback.putInt(key,value)
        is Long -> rollback.putLong(key,value)
        is Float -> rollback.putFloat(key,value)
        is Set<*> -> rollback.putStringSet(key,value.filterIsInstance<String>().toSet())
    }
    rollback.commit()
    error("安全连接尚未保存，请解锁设备后重试；原来的空间仍保留")
}
