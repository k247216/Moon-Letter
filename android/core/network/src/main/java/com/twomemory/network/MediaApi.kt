package com.twomemory.network

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    val purpose: String = "RECORD",
)

data class UploadTicketDto(
    val assetId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val objectKey: String = "",
    @SerializedName("uploadUrl")
    val url: String = "",
)

/**
 * Image lifecycle calls: request an upload ticket, upload either through the
 * local compatibility endpoint or directly to the private cloud object store,
 * then explicitly complete the server-side checksum/size verification.
 */
interface MediaApi {
    suspend fun uploadFileToUrl(uploadUrl: String, file: File, mimeType: String): Unit =
        throw UnsupportedOperationException("File upload transport not provided")
    suspend fun uploadFileData(baseUrl: String, bearer: String, assetId: UUID, file: File, mimeType: String): Unit =
        throw UnsupportedOperationException("File upload transport not provided")
    suspend fun downloadToFile(baseUrl: String, bearer: String, assetId: UUID, destination: File,
        maxBytes: Long = 256L * 1024 * 1024): Unit =
        throw UnsupportedOperationException("File download transport not provided")
    suspend fun createUpload(baseUrl: String, bearer: String, command: MediaCreateCommand): UploadTicketDto

    suspend fun uploadToUrl(uploadUrl: String, bytes: ByteArray, mimeType: String)

    suspend fun completeUpload(baseUrl: String, bearer: String, assetId: UUID, sha256: String)

    suspend fun uploadData(baseUrl: String, bearer: String, assetId: UUID, bytes: ByteArray, mimeType: String)

    suspend fun download(baseUrl: String, bearer: String, assetId: UUID): ByteArray

    suspend fun downloadPreview(baseUrl: String, bearer: String, assetId: UUID): ByteArray =
        throw UnsupportedOperationException("Preview transport not provided")

    suspend fun downloadReadingPreview(baseUrl: String, bearer: String, assetId: UUID): ByteArray =
        downloadPreview(baseUrl, bearer, assetId)
}

class RetrofitMediaApi private constructor() : MediaApi {
    override suspend fun uploadFileToUrl(uploadUrl: String, file: File, mimeType: String) {
        executeStreaming(Request.Builder().url(uploadUrl).put(file.asRequestBody(mimeType.toMediaType())).build()) { }
    }

    override suspend fun uploadFileData(baseUrl: String, bearer: String, assetId: UUID, file: File, mimeType: String) {
        executeStreaming(Request.Builder().url("$baseUrl/api/v1/media/$assetId/data")
            .header("Authorization", "Bearer $bearer").post(file.asRequestBody(mimeType.toMediaType())).build()) { }
    }

    override suspend fun downloadToFile(baseUrl: String, bearer: String, assetId: UUID, destination: File, maxBytes: Long) {
        require(maxBytes > 0)
        executeStreaming(Request.Builder().url("$baseUrl/api/v1/media/$assetId/data")
            .header("Authorization", "Bearer $bearer").get().build()) { response ->
            val body = response.body ?: throw IOException("empty media response")
            if (body.contentLength() > maxBytes) throw IOException("media exceeds download limit")
            destination.absoluteFile.parentFile!!.mkdirs()
            val part = File.createTempFile("media-", ".part", destination.absoluteFile.parentFile)
            try {
                var count = 0L
                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val size = input.read(buffer)
                            if (size < 0) break
                            count += size
                            if (count > maxBytes) throw IOException("media exceeds download limit")
                            output.write(buffer, 0, size)
                        }
                        output.fd.sync()
                    }
                }
                if (count == 0L || (body.contentLength() >= 0 && count != body.contentLength()))
                    throw IOException("incomplete media response")
                Files.move(part.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { part.delete() }
        }
    }

    private suspend fun executeStreaming(request: Request, consume: (okhttp3.Response) -> Unit): Unit =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        val result = runCatching {
                            if (!response.isSuccessful) throw MediaDownloadException(response.code)
                            consume(response)
                        }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                }
            })
        }

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

    override suspend fun uploadToUrl(
        uploadUrl: String,
        bytes: ByteArray,
        mimeType: String,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(uploadUrl)
            .put(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("signed media upload failed: HTTP ${response.code}")
            }
        }
    }

    override suspend fun completeUpload(
        baseUrl: String,
        bearer: String,
        assetId: UUID,
        sha256: String,
    ) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/media/$assetId/complete")
            .post("{\"sha256\":\"$sha256\"}".toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $bearer")
            .build()
        executeStreaming(request) { }
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

    override suspend fun downloadPreview(baseUrl: String, bearer: String, assetId: UUID): ByteArray =
        fetch(baseUrl, bearer, assetId, "thumbnail")

    override suspend fun downloadReadingPreview(baseUrl: String, bearer: String, assetId: UUID): ByteArray =
        fetch(baseUrl, bearer, assetId, "thumbnail?maxEdge=1200")

    override suspend fun download(baseUrl: String, bearer: String, assetId: UUID): ByteArray =
        fetch(baseUrl, bearer, assetId, "data")

    private suspend fun fetch(baseUrl: String, bearer: String, assetId: UUID, variant: String): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/media/$assetId/$variant")
                .get()
                .header("Authorization", "Bearer $bearer")
                .build()
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, error: IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use {
                            val result = runCatching {
                                if (!response.isSuccessful) throw MediaDownloadException(response.code)
                                response.body?.bytes() ?: ByteArray(0)
                            }
                            if (continuation.isActive) continuation.resumeWith(result)
                        }
                    }
                })
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

class MediaDownloadException(val status: Int) : IOException("media download failed: HTTP $status")
