package com.twomemory.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

data class CloudAlbumPhoto(
    val id: UUID, val assetId: UUID, val addedBy: UUID, val takenAtEpochMillis: Long,
    val mimeType: String, val width: Int, val height: Int, val deleted: Boolean,
)
data class CloudAlbum(
    val id: UUID, val name: String, val category: String, val createdBy: UUID,
    val createdAtEpochMillis: Long, val coverAssetId: UUID?, val deleted: Boolean,
    val photos: List<CloudAlbumPhoto>,
)

/** Uses the same authenticated API in LAN and ECS deployments; contains no OSS credentials. */
class SharedAlbumApi(baseUrl: String, private val token: String, space: UUID) {
    private val root = "${baseUrl.trimEnd('/')}/api/v1/couple/$space/albums"
    suspend fun read(): List<CloudAlbum> = withContext(Dispatchers.IO) {
        val json = call("GET", "")
        gson.fromJson<List<CloudAlbum>>(json, object : TypeToken<List<CloudAlbum>>() {}.type)
            ?: throw IOException("empty album snapshot")
    }
    suspend fun saveAlbum(id: UUID, name: String, category: String) = withContext(Dispatchers.IO) {
        call("PUT", "/$id", mapOf("name" to name, "category" to category)); Unit
    }
    suspend fun addPhoto(album: UUID, photo: UUID, asset: UUID, takenAtEpochMillis: Long) = withContext(Dispatchers.IO) {
        call("PUT", "/$album/photos/$photo", mapOf("assetId" to asset, "takenAtEpochMillis" to takenAtEpochMillis)); Unit
    }
    suspend fun setCover(album: UUID, asset: UUID) = withContext(Dispatchers.IO) {
        call("PUT", "/$album/cover", mapOf("assetId" to asset)); Unit
    }
    suspend fun deleteAlbum(album: UUID) = withContext(Dispatchers.IO) {
        call("DELETE", "/$album"); Unit
    }
    suspend fun deletePhoto(album: UUID, photo: UUID) = withContext(Dispatchers.IO) {
        call("DELETE", "/$album/photos/$photo"); Unit
    }
    private fun call(method: String, suffix: String, payload: Any? = null): String {
        val body = payload?.let { gson.toJson(it).toRequestBody("application/json".toMediaType()) }
        val request = Request.Builder().url(root + suffix).header("Authorization", "Bearer $token")
            .method(method, body).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("album request failed: HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }
    companion object {
        private val gson = Gson()
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()
    }
}
