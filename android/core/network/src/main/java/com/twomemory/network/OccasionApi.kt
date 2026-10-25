package com.twomemory.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

data class OccasionDto(val id: String, val coupleId: String, val authorId: String, val kind: String,
    val title: String, val body: String, val date: String, val repeatsYearly: Boolean,
    val recipientId: String?, val photoAssetId: String?, val updatedAt: String, val visibility: String? = "SHARED")
data class OccasionRequest(val kind: String, val title: String, val body: String, val date: String,
    val repeatsYearly: Boolean, val recipientId: String?, val photoAssetId: String?, val visibility: String = "SHARED")

class OccasionApi(baseUrl: String, private val token: String, coupleId: UUID) {
    private val root = "${baseUrl.trimEnd('/')}/api/v1/couple/$coupleId/occasions"
    suspend fun read(): List<OccasionDto> = withContext(Dispatchers.IO) {
        val items = gson.fromJson<List<OccasionDto>>(call("GET", ""), object : TypeToken<List<OccasionDto>>() {}.type)
            ?: throw IOException("empty occasion snapshot")
        items.forEach(::validate)
        require(items.map { it.id }.distinct().size == items.size) { "duplicate occasion IDs" }
        items
    }
    suspend fun save(id: String, body: OccasionRequest): OccasionDto = withContext(Dispatchers.IO) {
        val result = gson.fromJson(call("PUT", "/${UUID.fromString(id)}", body), OccasionDto::class.java)
            ?: throw IOException("empty occasion result")
        validate(result)
        require(result.id == id) { "wrong occasion result" }
        result
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { call("DELETE", "/${UUID.fromString(id)}"); Unit }
    private suspend fun call(method: String, suffix: String, value: Any? = null): String = suspendCancellableCoroutine { continuation ->
        val body = value?.let { gson.toJson(it).toRequestBody("application/json".toMediaType()) }
        val call = client.newCall(Request.Builder().url(root + suffix).header("Authorization", "Bearer $token")
            .method(method, body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("occasion request failed: HTTP ${it.code}")
                        it.body?.string().orEmpty()
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
    companion object {
        private fun validate(item: OccasionDto) {
            UUID.fromString(requireNotNull(item.id)); UUID.fromString(requireNotNull(item.coupleId)); UUID.fromString(requireNotNull(item.authorId))
            require(item.kind in setOf("BIRTHDAY", "ANNIVERSARY", "SURPRISE"))
            require(item.visibility == null || item.visibility in setOf("SHARED", "PRIVATE"))
            val title = requireNotNull(item.title); val body = requireNotNull(item.body)
            require(title.isNotBlank() && title.codePointCount(0, title.length) <= 80 && body.codePointCount(0, body.length) <= 4000)
            java.time.LocalDate.parse(requireNotNull(item.date)); java.time.Instant.parse(requireNotNull(item.updatedAt))
            item.photoAssetId?.let(UUID::fromString)
            if (item.kind == "SURPRISE") {
                if (item.visibility != "PRIVATE") UUID.fromString(requireNotNull(item.recipientId))
                item.recipientId?.let(UUID::fromString)
                require(item.recipientId == null || item.recipientId != item.authorId)
            }
            else require(item.recipientId == null)
        }
        private val gson = Gson()
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    }
}
