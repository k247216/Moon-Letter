package com.twomemory.app

import android.content.Context
import android.content.SharedPreferences
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

/**
 * Session state persisted in SharedPreferences: the device token, couple id
 * and server base URL. Populated by the setup screen, read here to build the
 * real sync pipeline. The token is sealed with an AndroidKeyStore key on
 * devices that provide one (see [TokenCipher]).
 */
object SyncSession {

    /** Prefills the setup field on a debug build; blank on a release build. */
    val DEFAULT_BASE_URL: String
        get() = ServerAddress.resolve(null, BuildConfig.DEBUG).orEmpty()

    private const val PREFS = "moon_letter_session"

    fun save(context: Context, token: String, coupleId: UUID, userId: UUID, baseUrl: String = DEFAULT_BASE_URL) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", TokenCipher.seal(token))
            .putString("coupleId", coupleId.toString())
            .putString("userId", userId.toString())
            .putString("baseUrl", baseUrl)
            .apply()
    }

    data class Session(val token: String, val coupleId: UUID, val userId: UUID, val baseUrl: String)

    data class Names(val own: String, val partner: String)

    /** The file [loadNames] reads: a page showing those names listens here. */
    fun sessionPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Persisted display names; blank when never fetched. */
    fun loadNames(context: Context): Names {
        val prefs = sessionPrefs(context)
        return Names(
            own = prefs.getString("ownName", null).orEmpty(),
            partner = prefs.getString("partnerName", null).orEmpty(),
        )
    }

    /** Best-effort fetch of real display names from the space profile. */
    suspend fun refreshNames(context: Context) {
        val session = load(context) ?: return
        runCatching {
            val space = com.twomemory.network.RetrofitSessionApi.create()
                .readSpace(session.baseUrl, session.token, session.coupleId)
            val own = space.members.firstOrNull { it.userId == session.userId }?.profile?.displayName.orEmpty()
            val partner = space.members.firstOrNull { it.userId != session.userId }?.profile?.displayName.orEmpty()
            // A name the space does not have yet stays unknown rather than blanking
            // the one this device already shows.
            if (own.isBlank() && partner.isBlank()) return@runCatching
            val editor = sessionPrefs(context).edit()
            if (own.isNotBlank()) editor.putString("ownName", own)
            if (partner.isNotBlank()) editor.putString("partnerName", partner)
            editor.apply()
        }
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
     * Returns null (and clears the damaged record) when anything is missing
     * or corrupt, so the app falls back to the binding screen instead of
     * crashing on startup.
     */
    fun load(context: Context): Session? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString("token", null)
        val coupleId = prefs.getString("coupleId", null)
        val userId = prefs.getString("userId", null)
        if (stored.isNullOrBlank() || coupleId.isNullOrBlank() || userId.isNullOrBlank()) return null
        val baseUrl = ServerAddress.resolve(prefs.getString("baseUrl", null), BuildConfig.DEBUG)
            ?: return null
        return runCatching {
            Session(
                token = TokenCipher.unseal(stored),
                coupleId = UUID.fromString(coupleId),
                userId = UUID.fromString(userId),
                baseUrl = baseUrl,
            )
        }.getOrElse {
            prefs.edit().clear().apply()
            null
        }
    }

    /** Builds the real pipeline: RoomSyncStore -> Retrofit -> SyncEngine. */
    fun engine(context: Context, session: Session): SyncEngine {
        val database = AppDatabase.build(context)
        val api = RetrofitCoupleDiaryApi.create(session.baseUrl) { session.token }
        return SyncEngine(api, RoomSyncStore(database), session.coupleId)
    }

    /** Lets any page fetch a picture that only the server holds. */
    fun installPhotoSource(session: Session) {
        val api = com.twomemory.network.RetrofitMediaApi.create()
        com.twomemory.designsystem.EntryPhotos.remoteBytes = { assetId ->
            api.download(session.baseUrl, session.token, java.util.UUID.fromString(assetId))
        }
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
            engine(context, session.copy(coupleId = coupleId))
        }
        SyncEngineRegistry.onCycleCompleted = { runCycleSideEffects(context) }
        MoonLetterNotifier.ensureChannels(context)
        WeeklyReviewWorker.schedule(context)
    }

    /**
     * What a finished cycle owes the app beyond the rows it applied: the display
     * names converge from the space read, because a rename travels on no channel
     * of its own — the change feed carries records and comments only.
     */
    suspend fun runCycleSideEffects(context: Context) {
        refreshNames(context)
        NewEntryNotice.announceIfNeeded(context)
    }

    /** One-shot sync from app start / foreground return / manual refresh. */
    suspend fun triggerSync(context: Context) {
        val session = load(context) ?: return
        installProcessHooks(context)
        // Images first (best effort), so the entry pushes with asset ids.
        MediaUploadManager.uploadPendingImages(context)
        SyncWorker.enqueue(context, session.coupleId)
    }
}
