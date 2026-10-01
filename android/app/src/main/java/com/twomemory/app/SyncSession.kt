package com.twomemory.app

import android.content.Context
import com.twomemory.database.AppDatabase
import com.twomemory.database.RoomSyncStore
import com.twomemory.network.RetrofitCoupleDiaryApi
import com.twomemory.sync.SyncEngine
import com.twomemory.sync.SyncEngineRegistry
import com.twomemory.sync.SyncWorker
import java.util.UUID

/**
 * Default points at the development machine's LAN address so a real phone on
 * the same network reaches the server without typing an URL. Emulator builds
 * override this to 10.0.2.2 when needed.
 */
const val DEFAULT_DEV_BASE_URL = "http://10.138.79.194:8080"

/**
 * Session state persisted in SharedPreferences: the device token, couple id
 * and server base URL. Populated by the setup screen, read here to build the
 * real sync pipeline. The token is sealed with an AndroidKeyStore key on
 * devices that provide one (see [TokenCipher]).
 */
object SyncSession {

    const val DEFAULT_BASE_URL = DEFAULT_DEV_BASE_URL

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

    /** Persisted display names; blank when never fetched. */
    fun loadNames(context: Context): Names {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
            if (own.isNotBlank() || partner.isNotBlank()) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("ownName", own)
                    .putString("partnerName", partner)
                    .apply()
            }
        }
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
        return runCatching {
            Session(
                token = TokenCipher.unseal(stored),
                coupleId = UUID.fromString(coupleId),
                userId = UUID.fromString(userId),
                baseUrl = prefs.getString("baseUrl", null) ?: DEFAULT_BASE_URL,
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

    /**
     * Writes the record through Room + outbox and publishes it in the same batch,
     * then triggers a sync. Offline this leaves a private draft plus two pending
     * operations; the record reaches the partner as soon as the device reconnects.
     */
    suspend fun publish(
        context: Context,
        state: com.twomemory.editor.EditorUiState,
        mode: com.twomemory.model.EntryMode = com.twomemory.model.EntryMode.PERSONAL,
    ) {
        val session = load(context) ?: error("设备尚未绑定：请先完成 bootstrap 与配对")
        val payload = org.json.JSONObject().put("text", state.body).toString()
        com.twomemory.database.LocalEntryWriter(AppDatabase.build(context)).save(
            com.twomemory.model.LocalEntryCommand(
                coupleId = session.coupleId,
                authorId = session.userId,
                mode = mode,
                occurredAt = state.occurrenceTime,
                occurredTimezone = state.timezone,
                title = state.title.ifBlank { null },
                blocks = listOf(
                    com.twomemory.model.LocalBlockCommand(
                        type = com.twomemory.model.BlockType.TEXT,
                        orderKey = 0,
                        payload = payload,
                        authorId = session.userId,
                    ),
                ),
            ),
            publish = true,
        )
        triggerSync(context)
    }

    /** One-shot sync from app start / foreground return / manual refresh. */
    suspend fun triggerSync(context: Context) {
        val session = load(context) ?: return
        // Images first (best effort), so the entry pushes with asset ids.
        MediaUploadManager.uploadPendingImages(context)
        SyncEngineRegistry.factory = { coupleId ->
            engine(context, session.copy(coupleId = coupleId))
        }
        SyncWorker.enqueue(context, session.coupleId)
    }
}
