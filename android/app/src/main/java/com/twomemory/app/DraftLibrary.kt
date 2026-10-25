package com.twomemory.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.twomemory.model.EntryMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class SavedDraft(val id: String, val mode: EntryMode, val draft: DraftStore.Draft, val updatedAt: Long)

/** Unshared on-device drafts. Slots reuse the legacy media serializer without touching originals. */
internal class DraftLibrary(private val context: Context, scope: String) {
    private val key = recordingScopeKey(scope)
    private val prefs = context.getSharedPreferences("moon_letter_draft_library_$key", Context.MODE_PRIVATE)
    // Activity metadata is separate from the existing index and serialized draft slots.
    private val activePrefs = context.getSharedPreferences("moon_letter_draft_active_$key", Context.MODE_PRIVATE)

    private fun slot(id: String): Context {
        require(runCatching { UUID.fromString(id) }.isSuccess)
        return object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(if (name == "moon_letter_draft") "$name-library-$key-$id" else name, mode)
        }
    }
    fun list(): List<SavedDraft> = synchronized(lock) {
        val array = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrElse { JSONArray() }
        (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            val mode = EntryMode.valueOf(item.getString("mode"))
            DraftStore.load(slot(id), mode)?.let { SavedDraft(id, mode, it, item.getLong("updatedAt")) }
        }.getOrNull() }.sortedByDescending { it.updatedAt }
    }
    fun load(id: String): SavedDraft? = list().firstOrNull { it.id == id }
    /** Only an explicitly active slot resumes; legacy/manual library entries stay in the box. */
    fun activeDraft(mode: EntryMode): SavedDraft? = synchronized(lock) {
        val field = "active:${mode.name}"
        val id = activePrefs.getString(field, null) ?: return@synchronized null
        val saved = load(id)?.takeIf { it.mode == mode }
        if (saved == null) commitBinding(activePrefs, activePrefs.edit().remove(field))
        saved
    }
    /** Manual resume selects an existing slot without rewriting its body, media or timestamp. */
    fun activate(id: String, mode: EntryMode) = synchronized(lock) {
        val field = "active:${mode.name}"
        if (activePrefs.getString(field, null) != id && load(id)?.mode == mode)
            commitBinding(activePrefs, activePrefs.edit().putString(field, id))
    }
    /** Finish only this id; never close another page that became active in the meantime. */
    fun markManualSaved(id: String) = synchronized(lock) {
        val fields = EntryMode.entries.map { "active:${it.name}" }.filter { activePrefs.getString(it, null) == id }
        if (fields.isNotEmpty()) {
            val editor = activePrefs.edit()
            fields.forEach { editor.remove(it) }
            commitBinding(activePrefs, editor)
        }
    }
    /** Existing callers autosave by default; imports can preserve an inactive library entry. */
    fun save(id: String, mode: EntryMode, draft: DraftStore.Draft, activate: Boolean = true) = synchronized(lock) {
        val others = list().filterNot { it.id == id }
        DraftStore.save(slot(id), mode, draft.title, draft.body, draft.photos, draft.attachments, draft.occurrence, draft.visibility)
        val saved = DraftStore.load(slot(id), mode)
        write(others + listOfNotNull(saved?.let { SavedDraft(id, mode, it, System.currentTimeMillis()) }))
        if (saved == null) markManualSaved(id)
        else if (activate) this.activate(id, mode)
    }
    fun remove(id: String) = synchronized(lock) {
        markManualSaved(id)
        val existing = load(id) ?: return@synchronized
        DraftStore.clear(slot(id), existing.mode)
        write(list().filterNot { it.id == id })
    }
    fun removeIfUnchanged(id: String, submitted: DraftStore.Draft) = synchronized(lock) {
        if (load(id)?.draft == submitted) remove(id)
    }
    /** Preserve legacy drafts once; their manual/automatic status cannot be inferred. */
    fun importLegacyDrafts() = synchronized(lock) {
        val legacy = context.getSharedPreferences("moon_letter_draft", Context.MODE_PRIVATE)
        if (!legacy.getBoolean("libraryImported", false)) {
            EntryMode.entries.forEach { mode -> DraftStore.load(context, mode)?.let {
                save(UUID.randomUUID().toString(), mode, it, activate = false)
                DraftStore.clear(context, mode)
            } }
            legacy.edit().putBoolean("libraryImported", true).apply()
        }
    }
    private fun write(items: List<SavedDraft>) {
        prefs.edit().putString("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("id", it.id).put("mode", it.mode.name).put("updatedAt", it.updatedAt)) }
        }.toString()).apply()
    }
    companion object { private val lock = Any() }
}
