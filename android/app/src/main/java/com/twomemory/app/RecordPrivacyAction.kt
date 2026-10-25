package com.twomemory.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.twomemory.database.EntryEntity
import com.twomemory.database.AppDatabase
import com.twomemory.database.RoomSyncStore
import com.twomemory.network.PrivacyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@Composable
fun RecordPrivacyAction(entry: EntryEntity, database: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember(entry.id) { mutableStateOf(false) }
    var busy by remember(entry.id) { mutableStateOf(false) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    val hide = entry.visibility != "PRIVATE"
    TextButton(enabled = !busy, onClick = { confirm = true }) { Text(if (busy) "收好中…" else if (hide) "可见范围" else "仅自己") }
    if (confirm) AlertDialog(onDismissRequest = { if (!busy) { confirm = false; error = null } },
        title = { Text(if (hide) "先收在心里？" else "把这一页分享给对方？") },
        text = { Text(error ?: if (hide) "云端仍会保存，只对你可见。对方下次同步会撤下这一页；已经另存的文件无法追回。" else "这篇记录和附件将对你们两个人可见。") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    val session = requireNotNull(SyncSession.load(context))
                    check(entry.authorId == session.userId.toString())
                    val value = if (hide) "PRIVATE" else "SHARED"
                    val response = PrivacyApi(session.baseUrl, session.token).change(UUID.fromString(entry.id), value, entry.rowVersion)
                    val json = JSONObject(response)
                    check(json.getString("id") == entry.id && json.getString("coupleId") == session.coupleId.toString() &&
                        json.getString("authorId") == session.userId.toString() && json.getString("visibility") == value)
                    check(SyncSession.load(context) == session)
                    RoomSyncStore(database).mergeConfirmedEntry(response)
                    confirm = false
                    SyncSession.triggerSync(context)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "还没有更改成功，原来的可见范围保持不变，请重试。" }
                finally { busy = false }
            }
        }) { Text(if (busy) "正在收好…" else if (hide) "仅自己可见" else "分享") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false; error = null }) { Text("取消") } })
}
