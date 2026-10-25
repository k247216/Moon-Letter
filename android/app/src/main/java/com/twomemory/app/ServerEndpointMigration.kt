package com.twomemory.app

import android.content.Context
import com.twomemory.network.SessionApi
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.sync.SyncEngineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Verified transport change, NOT rebinding. Local storage identity never changes. */
object ServerEndpointMigration {
    val PUBLIC_ORIGIN: String = BuildConfig.PUBLIC_API_ORIGIN
    const val USB_ORIGIN = "http://127.0.0.1:18088"

    suspend fun migrate(context: Context, destination: String, api: SessionApi = RetrofitSessionApi.create(),
        commit: (android.content.SharedPreferences.Editor) -> Boolean = { it.commit() }) =
        withContext(Dispatchers.IO) {
            require(destination.trim().trimEnd('/') == PUBLIC_ORIGIN) { "只允许切换到月笺的正式HTTPS域名" }
            val before = requireNotNull(SyncSession.load(context)) { "请先保留现有空间连接" }
            require(before.baseUrl == USB_ORIGIN || before.baseUrl == PUBLIC_ORIGIN) { "当前连接不是已验证的月笺云端测试地址" }
            // Foreground -> record cycle -> albums -> independent record media.
            SyncSession.withPausedForeground {
                SyncEngineRegistry.cycleMutex.withLock {
                    AlbumCloudSync.withPaused {
                      RecordMediaSync.withPaused {
                        check(SyncSession.load(context) == before) { "空间已变化，请重试" }
                        val remote = api.readSpace(PUBLIC_ORIGIN, before.token, before.coupleId)
                        require(remote.id == before.coupleId && remote.members.any { it.userId == before.userId }) {
                            "新地址不是原来的空间或成员，未切换"
                        }
                        synchronized(SyncSession.bindingLock) {
                        check(SyncSession.load(context) == before) { "空间已变化，请重试" }
                        if (before.baseUrl != PUBLIC_ORIGIN) {
                            // One atomic preferences commit; no files/rows move, so process death
                            // sees either complete old or complete new binding, never a half migration.
                            val prefs = SyncSession.sessionPrefs(context)
                            val oldAlias = prefs.getString("storageOrigin", null)
                            val saved = commit(prefs.edit()
                                .putString("storageOrigin", before.storageOrigin)
                                .putString("baseUrl", PUBLIC_ORIGIN))
                            if (!saved) {
                                // commit(false) can still change Android's in-memory map.
                                // Restore it before allowing sync or another transition.
                                val rollback = prefs.edit().putString("baseUrl", before.baseUrl)
                                if (oldAlias == null) rollback.remove("storageOrigin") else rollback.putString("storageOrigin", oldAlias)
                                rollback.commit()
                                error("地址未能保存，请重试")
                            }
                        }
                        SyncSession.installPhotoSource(context, requireNotNull(SyncSession.load(context)))
                        }
                      }
                    }
                }
            }
            // Scheduling is repairable at every process start; it must not turn a
            // committed transition into a false 'not switched' UI error.
            runCatching { AlbumCloudSync.enqueue(context) }
            runCatching { RecordMediaSync.enqueue(context) }
        }
}
