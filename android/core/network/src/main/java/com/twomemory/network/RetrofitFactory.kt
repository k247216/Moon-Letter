package com.twomemory.network

import com.google.gson.annotations.SerializedName
import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Canonical operation payload sent to POST /api/v1/sync/operations. */
data class SyncOperationBody(
    val operationId: UUID,
    val coupleId: UUID,
    @SerializedName("payloadHash") val payloadHash: String,
    @SerializedName("operationType") val operationType: String,
    val payload: String,
)

internal data class MutationBody(
    val status: Int = 0,
    val body: String? = null,
    val replayed: Boolean = false,
)

internal data class ChangeDto(
    val sequence: Long = 0,
    val entityType: String = "",
    val entityId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val operation: String = "",
    val payload: String? = null,
)

internal data class ChangePageDto(
    val changes: List<ChangeDto> = emptyList(),
    val nextSequence: Long = 0,
    val hasMore: Boolean = false,
)

internal interface MoonLetterRetrofitApi {
    @POST("api/v1/sync/operations")
    suspend fun push(@Body body: SyncOperationBody): Response<MutationBody>

    @GET("api/v1/sync/changes")
    suspend fun pull(
        @Query("coupleId") coupleId: UUID,
        @Query("after") after: Long,
        @Query("limit") limit: Int,
    ): Response<ChangePageDto>
}

/** Typed operation type used by the server dispatcher for entry creation. */
const val OPERATION_CREATE_PERSONAL_ENTRY = "CREATE_PERSONAL_ENTRY"

/**
 * SHA-256 hex of the canonical payload the server validates:
 * operationType + "\n" + payload.
 */
fun operationPayloadHash(operationType: String, payload: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest((operationType + "\n" + payload).toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

/**
 * Real HTTP implementation of [CoupleDiaryApi]: Retrofit/OkHttp with bearer
 * injection, 10s timeouts and HTTP status to result mapping.
 */
class RetrofitCoupleDiaryApi private constructor(
    private val retrofitApi: MoonLetterRetrofitApi,
    private val tokenProvider: () -> String?,
) : CoupleDiaryApi {

    override suspend fun push(operation: PendingOperation): PushResult = withContext(Dispatchers.IO) {
        val operationType = when (operation.action) {
            "CREATE_ENTRY" -> OPERATION_CREATE_PERSONAL_ENTRY
            else -> operation.action
        }
        val body = SyncOperationBody(
            operationId = operation.operationId,
            coupleId = operation.coupleId,
            payloadHash = operationPayloadHash(operationType, operation.payload),
            operationType = operationType,
            payload = operation.payload,
        )
        val response = retrofitApi.push(body)
        when {
            response.isSuccessful -> {
                val mutation = response.body() ?: MutationBody()
                PushResult(
                    operationId = operation.operationId,
                    status = PushResult.Status.APPLIED,
                    responseBody = mutation.body,
                    replayed = mutation.replayed,
                )
            }
            response.code() == 401 -> PushResult(operation.operationId, PushResult.Status.UNAUTHORIZED)
            response.code() == 409 -> PushResult(operation.operationId, PushResult.Status.CONFLICT)
            else -> PushResult(operation.operationId, PushResult.Status.RETRYABLE_FAILURE)
        }
    }

    override suspend fun pull(coupleId: UUID, after: Long, limit: Int): ChangePage =
        withContext(Dispatchers.IO) {
            val response = retrofitApi.pull(coupleId, after, limit)
            if (response.code() == 401) {
                throw HttpUnauthorizedException()
            }
            if (!response.isSuccessful) {
                throw IOException("change feed failed: HTTP ${response.code()}")
            }
            val page = response.body() ?: ChangePageDto()
            ChangePage(
                changes = page.changes.map { dto ->
                    RemoteChange(
                        sequence = dto.sequence,
                        entityType = dto.entityType,
                        entityId = dto.entityId,
                        operation = dto.operation,
                        payload = dto.payload,
                    )
                },
                nextSequence = page.nextSequence,
                hasMore = page.hasMore,
            )
        }

    companion object {
        private val uuidGson: com.google.gson.Gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(UUID::class.java, com.google.gson.JsonSerializer<UUID> { src, _, _ ->
                com.google.gson.JsonPrimitive(src.toString())
            })
            .registerTypeAdapter(UUID::class.java, com.google.gson.JsonDeserializer<UUID> { json, _, _ ->
                UUID.fromString(json.asString)
            })
            .create()

        fun create(baseUrl: String, tokenProvider: () -> String?): RetrofitCoupleDiaryApi {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val token = tokenProvider()
                    val request = if (token.isNullOrBlank()) {
                        chain.request()
                    } else {
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    }
                    chain.proceed(request)
                }
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(uuidGson))
                .build()
            return RetrofitCoupleDiaryApi(retrofit.create(MoonLetterRetrofitApi::class.java), tokenProvider)
        }
    }
}
