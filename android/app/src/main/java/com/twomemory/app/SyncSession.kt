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
 * Session state persisted in SharedPreferences: the device token, couple id
 * and server base URL. Populated by bootstrap/pairing (Task 11), read here
 * to build the real sync pipeline.
 */
/**
 * Default points at the development machine's LAN address so a real phone on
 * the same network reaches the server without typing an URL. Emulator builds
 * override this to 10.0.2.2 when needed.
 */
const val DEFAULT_DEV_BASE_URL = "http://10.138.79.194:8080"

object SyncSession {

    const val DEFAULT_BASE_URL = DEFAULT_DEV_BASE_URL

    private const val PREFS = "moon_letter_session"

    fun save(context: Context, token: String, coupleId: UUID, userId: UUID, baseUrl: String = DEFAULT_BASE_URL) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .putString("coupleId", coupleId.toString())
            .putString("userId", userId.toString())
            .putString("baseUrl", baseUrl)
            .apply()
    }

    data class Session(val token: String, val coupleId: UUID, val userId: UUID, val baseUrl: String)

    fun load(context: Context): Session? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return null
        val coupleId = prefs.getString("coupleId", null) ?: return null
        val userId = prefs.getString("userId", null) ?: return null
        return Session(
            token = token,
            coupleId = UUID.fromString(coupleId),
            userId = UUID.fromString(userId),
            baseUrl = prefs.getString("baseUrl", null) ?: DEFAULT_BASE_URL,
        )
    }

    /** Builds the real pipeline: RoomSyncStore -> Retrofit -> SyncEngine. */
    fun engine(context: Context, session: Session): SyncEngine {
        val database = AppDatabase.build(context)
        val api = RetrofitCoupleDiaryApi.create(session.baseUrl) { session.token }
        return SyncEngine(api, RoomSyncStore(database), session.coupleId)
    }

    /** Saves a personal draft through Room + outbox, then triggers a sync. */
    suspend fun saveDraft(
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
        )
        triggerSync(context)
    }

    /** One-shot sync from app start / foreground return / manual refresh. */
    fun triggerSync(context: Context) {
        val session = load(context) ?: return
        SyncEngineRegistry.factory = { coupleId ->
            engine(context, session.copy(coupleId = coupleId))
        }
        SyncWorker.enqueue(context, session.coupleId)
    }
}
