package com.twomemory.network

import com.google.gson.JsonParser
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class PrivacyApi(private val origin: String, private val token: String) {
    suspend fun requireSupport() = withContext(Dispatchers.IO) {
        val value = JsonParser.parseString(call("GET", "/api/v1/privacy/capabilities")).asJsonObject
        if (value.get("privateEntries")?.asBoolean != true || value.get("privateOccasions")?.asBoolean != true)
            throw IOException("服务端尚未支持私密保存，请先更新服务；内容仍保留在这里")
    }

    suspend fun change(id: UUID, visibility: String, baseVersion: Long): String = withContext(Dispatchers.IO) {
        require(visibility in setOf("SHARED", "PRIVATE"))
        call("PUT", "/api/v1/entries/$id/visibility", "{\"visibility\":\"$visibility\",\"baseVersion\":$baseVersion}")
    }

    private fun call(method: String, path: String, body: String? = null): String =
        client.newCall(Request.Builder().url(origin.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .method(method, body?.toRequestBody("application/json".toMediaType())).build()).execute().use {
            if (!it.isSuccessful) throw IOException("尚未更改可见范围，请检查连接或服务版本（${it.code}）")
            it.body?.string() ?: throw IOException("服务回复为空，可见范围未确认")
        }
    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).build()
    }
}
