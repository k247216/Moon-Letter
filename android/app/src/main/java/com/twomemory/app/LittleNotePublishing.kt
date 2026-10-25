package com.twomemory.app

import android.content.Context
import androidx.room.withTransaction
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal suspend fun restoreLittleNote(context: Context, binding: SyncSession.BindingSnapshot): PendingLittleNote =
    withContext(Dispatchers.IO) {
        check(SyncSession.isCurrentBinding(context, binding)) { "空间已切换，请重新打开便签" }
        val store = LittleNoteStore(context, binding.session)
        val pending = store.load()
        val db = AppDatabase.build(context, binding.session.databaseName)
        val entry = db.entryDao().findEntry(pending.id.toString()) ?: return@withContext pending
        val identical = !entry.deleted && entry.coupleId == binding.session.coupleId.toString() &&
            entry.authorId == binding.session.userId.toString() && entry.title == "小便签" &&
            entry.visibility == pending.visibility.name && db.entryDao().blocks(entry.id).singleOrNull()?.let {
                it.type == "TEXT" && runCatching { JSONObject(it.payload).optString("text") }.getOrNull() == pending.text &&
                    notePaperFromPayload(it.payload) == pending.paper
            } == true
        check(SyncSession.isCurrentBinding(context, binding)) { "空间已切换，请重新打开便签" }
        store.retireCommitted(pending.id, pending.takeIf { identical })
    }

/** Success means local transaction committed, not a claim of remote receipt. Stable id makes retry safe. */
internal suspend fun publishLittleNote(context: Context, binding: SyncSession.BindingSnapshot,
    note: PendingLittleNote): UUID = withContext(Dispatchers.IO) {
    require(note.text.isNotBlank()) { "先留下一小句吧" }
    require(note.text.codePointCount(0, note.text.length) <= 500) { "小纸条最多写500字" }
    check(SyncSession.isCurrentBinding(context, binding)) { "空间已切换，这张便签还在原来的空间里" }
    val session = binding.session
    if (note.visibility == EntryVisibility.PRIVATE) {
        com.twomemory.network.PrivacyApi(session.baseUrl, session.token).requireSupport()
        check(SyncSession.isCurrentBinding(context, binding)) { "空间已切换，请重新打开便签" }
    }
    val db = AppDatabase.build(context, session.databaseName)
    db.withTransaction {
        check(SyncSession.isCurrentBinding(context, binding)) { "空间已切换，请重新打开便签" }
        val existing = db.entryDao().findEntry(note.id.toString())
        if (existing != null) {
            check(!existing.deleted && existing.authorId == session.userId.toString() &&
                existing.coupleId == session.coupleId.toString() && existing.title == "小便签" &&
                existing.visibility == note.visibility.name &&
                db.entryDao().blocks(existing.id).singleOrNull()?.let {
                    it.type == "TEXT" && JSONObject(it.payload).optString("text") == note.text &&
                        notePaperFromPayload(it.payload) == note.paper
                } == true) { "这张便签已经收好，请重新打开新的一张" }
        } else {
            LocalEntryWriter(db).save(LocalEntryCommand(session.coupleId, session.userId, EntryMode.PERSONAL,
                Instant.now(), ZoneId.systemDefault().id, title = "小便签", entryId = note.id,
                visibility = note.visibility, blocks = listOf(LocalBlockCommand(type = BlockType.TEXT,
                    orderKey = 0, authorId = session.userId, payload = JSONObject().put("text", note.text)
                        .put("notePaper", notePaperJson(note.paper)).toString()))), publish = true)
        }
    }
    note.id
}
