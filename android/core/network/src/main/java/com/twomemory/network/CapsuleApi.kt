package com.twomemory.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CapsuleDto(val id: String, val coupleId: String, val authorId: String,
    val title: String, val body: String?, val unlockDate: String, val visibility: String,
    val unlocked: Boolean, val updatedAt: String) {
    fun readableBody(userId: String): String? = body.takeIf {
        authorId == userId || (visibility == "SHARED" && unlocked)
    }
}

data class CapsuleRequest(val title: String, val body: String, val unlockDate: String, val visibility: String)

/** Strict wire types: Gson's default coercions must never turn a missing unlock flag into false. */
object CapsuleValidation {
    private val cacheGson = com.google.gson.GsonBuilder().serializeNulls().create()
    /** String boundary keeps Gson out of the app module's dependency surface. */
    fun parseSnapshot(raw: String, coupleId: String, userId: String): List<CapsuleDto> {
        val json = JsonParser.parseString(raw)
        require(json.isJsonArray) { "Invalid capsule snapshot" }
        return json.asJsonArray.map(::parse).also { snapshot(it, coupleId, userId) }
    }
    fun serializeSnapshot(items: List<CapsuleDto>, coupleId: String, userId: String): String {
        snapshot(items, coupleId, userId)
        return cacheGson.toJson(items.map { it.copy(body = it.readableBody(userId)) })
    }
    fun uuid(value: String) { require(UUID.fromString(value).toString() == value) { "Invalid capsule identity" } }
    fun date(value: String) {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Invalid capsule date" }
        require(LocalDate.parse(value).year in 1..9999) { "Invalid capsule date" }
    }
    fun request(value: CapsuleRequest) {
        require(value.title.isNotBlank() && value.title.codePointCount(0, value.title.length) <= 80) { "标题请填写1至80个字" }
        require(value.body.isNotEmpty() && value.body.codePointCount(0, value.body.length) <= 20000) { "正文请填写1至20000个字" }
        date(value.unlockDate)
        require(value.visibility in setOf("SHARED", "PRIVATE")) { "Invalid capsule visibility" }
    }
    fun item(value: CapsuleDto, coupleId: String, userId: String) {
        uuid(value.id); uuid(value.coupleId); uuid(value.authorId); uuid(userId)
        require(value.coupleId == coupleId) { "Wrong capsule space" }
        require(value.title.isNotBlank() && value.title.codePointCount(0, value.title.length) <= 80)
        date(value.unlockDate); Instant.parse(value.updatedAt)
        require(value.visibility in setOf("SHARED", "PRIVATE"))
        require(value.authorId == userId || value.visibility == "SHARED") { "Private partner capsule" }
        if (value.authorId != userId && !value.unlocked) {
            require(value.body.isNullOrEmpty()) { "Sealed partner body was not redacted" }
        } else {
            require(!value.body.isNullOrEmpty()) { "Missing readable capsule body" }
        }
        value.body?.let { require(it.codePointCount(0, it.length) <= 20000) }
    }
    fun snapshot(items: List<CapsuleDto>, coupleId: String, userId: String) {
        items.forEach { item(it, coupleId, userId) }
        require(items.map { it.id }.distinct().size == items.size) { "Duplicate capsule IDs" }
    }
    internal fun parse(value: JsonElement): CapsuleDto {
        require(value.isJsonObject) { "Invalid capsule object" }
        val obj = value.asJsonObject
        fun string(key: String): String {
            val field = obj.get(key)
            require(field != null && field.isJsonPrimitive && field.asJsonPrimitive.isString) { "Invalid capsule $key" }
            return field.asString
        }
        val unlocked = obj.get("unlocked")
        require(unlocked != null && unlocked.isJsonPrimitive && unlocked.asJsonPrimitive.isBoolean) { "Invalid capsule unlocked" }
        require(obj.has("body")) { "Missing capsule body" }
        val body = if (obj.get("body").isJsonNull) null else string("body")
        return CapsuleDto(string("id"), string("coupleId"), string("authorId"), string("title"), body,
            string("unlockDate"), string("visibility"), unlocked.asBoolean, string("updatedAt"))
    }
}

class CapsuleApi(baseUrl: String, private val token: String, private val coupleId: UUID) {
    private val root = "${baseUrl.trimEnd('/')}/api/v1/couple/$coupleId/capsules"
    suspend fun read(userId: String): List<CapsuleDto> = withContext(Dispatchers.IO) {
        CapsuleValidation.parseSnapshot(call("GET", ""), coupleId.toString(), userId)
    }
    suspend fun save(id: String, value: CapsuleRequest, userId: String): CapsuleDto = withContext(Dispatchers.IO) {
        CapsuleValidation.uuid(id); CapsuleValidation.request(value)
        val result = CapsuleValidation.parse(JsonParser.parseString(call("PUT", "/$id", value)))
        CapsuleValidation.item(result, coupleId.toString(), userId)
        require(result.id == id && result.authorId == userId) { "Wrong saved capsule identity" }
        require(result.title == value.title.trim() && result.body == value.body && result.unlockDate == value.unlockDate && result.visibility == value.visibility) {
            "Server did not confirm this capsule edit"
        }
        result
    }
    suspend fun delete(id: String) {
        CapsuleValidation.uuid(id); call("DELETE", "/$id")
    }
    private suspend fun call(method: String, suffix: String, value: Any? = null): String = suspendCancellableCoroutine { continuation ->
        val body = value?.let { gson.toJson(it).toRequestBody("application/json".toMediaType()) }
        val call = client.newCall(Request.Builder().url(root + suffix).header("Authorization", "Bearer $token")
            .method(method, body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Capsule request failed: HTTP ${it.code}")
                        if (method == "DELETE" && it.code !in setOf(200, 204)) throw IOException("Capsule deletion was not confirmed")
                        it.body?.string().orEmpty()
                    }
                }
                result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
    companion object {
        private val gson = Gson()
        // Do not forward a device bearer token through a redirect to another origin.
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS).build()
    }
}
