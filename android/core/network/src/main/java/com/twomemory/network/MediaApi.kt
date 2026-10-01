package com.twomemory.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class MediaCreateCommand(
    val ownerId: UUID,
    val coupleId: UUID,
    val operationId: UUID,
    val kind: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val sha256: String,
)

data class UploadTicketDto(
    val assetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val objectKey: String = "",
    val url: String = "",
)

/**
 * Image lifecycle calls (Task 13): request an upload ticket, stream the raw
 * bytes to the local-storage endpoint, then the server marks the asset READY.
 * A partner fetches bytes from the same endpoint.
 */
interface MediaApi {
    suspend fun createUpload(baseUrl: String, bearer: String, command: MediaCreateCommand): UploadTicketDto

    suspend fun uploadData(baseUrl: String, bearer: String, assetId: UUID, bytes: ByteArray, mimeType: String)

    suspend fun download(baseUrl: String, bearer: String, assetId: UUID): ByteArray
}

class RetrofitMediaApi private constructor() : MediaApi {

    override suspend fun createUpload(
        baseUrl: String,
        bearer: String,
        command: MediaCreateCommand,
    ): UploadTicketDto = withContext(Dispatchers.IO) {
        val response = api(baseUrl, bearer).createUpload(command)
        if (!response.isSuccessful) {
            throw IOException("media ticket failed: HTTP ${response.code()}")
        }
        response.body() ?: throw IOException("empty media ticket")
    }

    override suspend fun uploadData(
        baseUrl: String,
        bearer: String,
        assetId: UUID,
        bytes: ByteArray,
        mimeType: String,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/media/$assetId/data")
            .post(bytes.toRequestBody(mimeType.toMediaType()))
            .header("Authorization", "Bearer $bearer")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("media upload failed: HTTP ${response.code}")
            }
        }
    }

    override suspend fun download(baseUrl: String, bearer: String, assetId: UUID): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/media/$assetId/data")
                .get()
                .header("Authorization", "Bearer $bearer")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("media download failed: HTTP ${response.code}")
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    internal interface MediaRetrofitApi {
        @POST("api/v1/media/uploads")
        suspend fun createUpload(@Body command: MediaCreateCommand): retrofit2.Response<UploadTicketDto>
    }

    companion object {
        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun api(baseUrl: String, bearer: String): MediaRetrofitApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/")
                .client(OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder()
                            .header("Authorization", "Bearer $bearer").build())
                    }
                    .build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(MediaRetrofitApi::class.java)
        }

        @Volatile
        private var instance: RetrofitMediaApi? = null

        fun create(): MediaApi = instance ?: synchronized(this) {
            instance ?: RetrofitMediaApi().also { instance = it }
        }
    }
}
