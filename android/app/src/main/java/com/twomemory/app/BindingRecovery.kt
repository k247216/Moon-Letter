package com.twomemory.app

import android.content.Context
import com.twomemory.network.SessionApi
import com.twomemory.network.RetrofitSessionApi
import java.util.UUID

/** A verified REJOIN may replace a broken credential, never the original local storage identity. */
internal object BindingRecovery {
    private fun original(context: Context): SyncSession.Session {
        val prefs=SyncSession.sessionPrefs(context)
        val url=requireNotNull(ServerAddress.resolve(prefs.getString("baseUrl",null),BuildConfig.DEBUG))
        return SyncSession.Session("",UUID.fromString(prefs.getString("coupleId",null)),
            UUID.fromString(prefs.getString("userId",null)),url,
            prefs.getString("databaseName","moon-letter.db")!!,
            prefs.getString("storageOrigin",url)!!)
    }
    suspend fun request(context: Context, code: String, api: SessionApi=RetrofitSessionApi.create()): SyncSession.Session {
        val before=original(context)
        require(code.isNotBlank()) { "请让对方在原空间生成找回配对码" }
        val issued=api.pair(before.baseUrl,code.trim(),SyncSession.loadNames(context).own.ifBlank { "我" })
        require(issued.couple.id==before.coupleId && issued.userId==before.userId) { "这不是原来的空间或身份，本机内容没有更改" }
        return before.copy(token=issued.deviceToken)
    }
    fun save(context: Context, recovered: SyncSession.Session) = synchronized(SyncSession.bindingLock) {
        require(original(context)==recovered.copy(token="")) { "原空间已变化，请停止找回并重新核对" }
        require(recovered.token.isNotBlank())
        val sealed=TokenCipher.seal(recovered.token)
        val prefs=SyncSession.sessionPrefs(context)
        commitBinding(prefs,prefs.edit().putString("token",sealed))
    }
}
