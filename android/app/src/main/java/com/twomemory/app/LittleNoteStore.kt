package com.twomemory.app

import android.content.Context
import com.twomemory.model.EntryVisibility
import java.util.UUID

internal data class PendingLittleNote(val id: UUID, val text: String = "",
    val visibility: EntryVisibility = EntryVisibility.SHARED,
    val paper: com.twomemory.model.NotePaperAppearance = com.twomemory.model.NotePaperAppearance())

/** Small, separate scratch paper; never takes over a full-editor draft slot. */
internal class LittleNoteStore(context: Context, session: SyncSession.Session) {
    private val prefs = context.getSharedPreferences("little_note_${recordingScopeKey(
        "${session.storageOrigin}|${session.coupleId}|${session.userId}")}", Context.MODE_PRIVATE)
    @Synchronized fun load(): PendingLittleNote {
        val id = prefs.getString("id", null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { commitBinding(prefs, prefs.edit().putString("id", it.toString())) }
        return PendingLittleNote(id, prefs.getString("text", "").orEmpty(),
            runCatching { EntryVisibility.valueOf(prefs.getString("visibility", "SHARED")!!) }.getOrDefault(EntryVisibility.SHARED),
            notePaperFromPayload(prefs.getString("paper", null)))
    }
    @Synchronized fun keep(text: String, visibility: EntryVisibility = load().visibility,
        paper: com.twomemory.model.NotePaperAppearance = load().paper) {
        val current = load()
        commitBinding(prefs, prefs.edit().putString("id", current.id.toString()).putString("text", text)
            .putString("visibility", visibility.name)
            .putString("paper", org.json.JSONObject().put("notePaper", notePaperJson(paper)).toString()))
    }
    @Synchronized fun clearIfSame(submitted: PendingLittleNote) {
        if (load() == submitted) commitBinding(prefs, prefs.edit().clear())
    }
    /** A committed id is never reused for later writing, even if its record was deleted. */
    @Synchronized fun retireCommitted(id: UUID, acknowledged: PendingLittleNote?): PendingLittleNote {
        val current = load()
        if (current.id != id) return current
        val next = if (current == acknowledged) PendingLittleNote(UUID.randomUUID())
            else current.copy(id = UUID.randomUUID())
        commitBinding(prefs, prefs.edit().putString("id", next.id.toString()).putString("text", next.text)
            .putString("visibility", next.visibility.name)
            .putString("paper", org.json.JSONObject().put("notePaper", notePaperJson(next.paper)).toString()))
        return next
    }
}
