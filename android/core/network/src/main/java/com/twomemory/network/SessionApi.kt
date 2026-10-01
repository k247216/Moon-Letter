package com.twomemory.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class BootstrapRequestDto(val displayName: String)

data class BootstrapResultDto(
    val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val coupleId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val token: String = "",
)

data class CoupleViewDto(val id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"))

data class ProfileViewDto(val displayName: String = "")

data class MemberViewDto(
    val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val profile: ProfileViewDto = ProfileViewDto(),
)

data class SpaceViewDto(
    val id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    val members: List<MemberViewDto> = emptyList(),
)

/**
 * A freshly minted pairing token. [pairingTokenKind] is INVITE while the space
 * still has a free member slot and REJOIN when it is full, i.e. the token opens
 * one specific member's own slot again.
 */
data class PairingTokenResultDto(
    val couple: CoupleViewDto = CoupleViewDto(),
    val pairingToken: String = "",
    val pairingTokenKind: String = "INVITE",
)

data class PairRequestDto(val token: String, val displayName: String? = null)

data class UpdateProfileRequestDto(val displayName: String)

data class PairResultDto(
    val couple: CoupleViewDto = CoupleViewDto(),
    val deviceToken: String = "",
    val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"),
)

/** Thrown for non-2xx answers during bootstrap/pairing setup. */
class SetupHttpException(val code: Int, message: String) : IOException("setup failed: HTTP $code $message")

/**
 * Device binding and profile calls used around setup. Bootstrap and pair are
 * anonymous by design; the space read and the rename carry a bearer token.
 */
interface SessionApi {
    suspend fun bootstrap(baseUrl: String, secret: String, displayName: String): BootstrapResultDto

    /** Fetches (and rotates) the one-time pairing token for the space. */
    suspend fun pairingToken(baseUrl: String, bearer: String, coupleId: UUID): PairingTokenResultDto

    suspend fun pair(baseUrl: String, pairingToken: String, displayName: String): PairResultDto

    /** Reads the space with member profiles (display names for the timeline). */
    suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID): SpaceViewDto

    /**
     * Renames the caller in the space; the server rejects renaming anyone else
     * and keeps every field the client does not send.
     */
    suspend fun updateOwnProfile(
        baseUrl: String,
        bearer: String,
        coupleId: UUID,
        userId: UUID,
        displayName: String,
    ): ProfileViewDto
}

internal interface SessionRetrofitApi {
    @POST("api/v1/bootstrap")
    suspend fun bootstrap(
        @Header("X-Bootstrap-Secret") secret: String,
        @Body body: BootstrapRequestDto,
    ): Response<BootstrapResultDto>

    @POST("api/v1/couple/{coupleId}/pairing-token")
    suspend fun pairingToken(@Path("coupleId") coupleId: UUID): Response<PairingTokenResultDto>

    @POST("api/v1/couple/pair")
    suspend fun pair(@Body body: PairRequestDto): Response<PairResultDto>

    @GET("api/v1/couple/{coupleId}")
    suspend fun readSpace(@Path("coupleId") coupleId: UUID): Response<SpaceViewDto>

    @PATCH("api/v1/couple/{coupleId}/members/{userId}/profile")
    suspend fun updateOwnProfile(
        @Path("coupleId") coupleId: UUID,
        @Path("userId") userId: UUID,
        @Body body: UpdateProfileRequestDto,
    ): Response<ProfileViewDto>
}

class RetrofitSessionApi private constructor(
    private val bearerProvider: () -> String?,
) : SessionApi {

    override suspend fun bootstrap(baseUrl: String, secret: String, displayName: String): BootstrapResultDto =
        withContext(Dispatchers.IO) {
            val response = api(baseUrl).bootstrap(secret, BootstrapRequestDto(displayName))
            if (!response.isSuccessful) {
                throw SetupHttpException(response.code(), response.errorBody()?.string().orEmpty().take(200))
            }
            response.body() ?: throw SetupHttpException(response.code(), "empty body")
        }

    override suspend fun pairingToken(baseUrl: String, bearer: String, coupleId: UUID): PairingTokenResultDto =
        withContext(Dispatchers.IO) {
            val response = api(baseUrl, bearer).pairingToken(coupleId)
            if (!response.isSuccessful) {
                throw SetupHttpException(response.code(), response.errorBody()?.string().orEmpty().take(200))
            }
            response.body() ?: throw SetupHttpException(response.code(), "empty body")
        }

    override suspend fun pair(baseUrl: String, pairingToken: String, displayName: String): PairResultDto =
        withContext(Dispatchers.IO) {
            val response = api(baseUrl).pair(PairRequestDto(pairingToken, displayName))
            if (!response.isSuccessful) {
                throw SetupHttpException(response.code(), response.errorBody()?.string().orEmpty().take(200))
            }
            response.body() ?: throw SetupHttpException(response.code(), "empty body")
        }

    override suspend fun updateOwnProfile(
        baseUrl: String,
        bearer: String,
        coupleId: UUID,
        userId: UUID,
        displayName: String,
    ): ProfileViewDto =
        withContext(Dispatchers.IO) {
            val response = api(baseUrl, bearer)
                .updateOwnProfile(coupleId, userId, UpdateProfileRequestDto(displayName))
            if (!response.isSuccessful) {
                throw SetupHttpException(response.code(), response.errorBody()?.string().orEmpty().take(200))
            }
            response.body() ?: throw SetupHttpException(response.code(), "empty body")
        }

    override suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID): SpaceViewDto =
        withContext(Dispatchers.IO) {
            val response = api(baseUrl, bearer).readSpace(coupleId)
            if (!response.isSuccessful) {
                throw SetupHttpException(response.code(), response.errorBody()?.string().orEmpty().take(200))
            }
            response.body() ?: throw SetupHttpException(response.code(), "empty body")
        }

    companion object {
        /** Gson cannot construct UUID by reflection; register explicit adapters. */
        private val uuidGson: com.google.gson.Gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(UUID::class.java, com.google.gson.JsonSerializer<UUID> { src, _, _ ->
                com.google.gson.JsonPrimitive(src.toString())
            })
            .registerTypeAdapter(UUID::class.java, com.google.gson.JsonDeserializer<UUID> { json, _, _ ->
                UUID.fromString(json.asString)
            })
            .create()

        private fun okHttp(bearerProvider: () -> String?): okhttp3.OkHttpClient =
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val token = bearerProvider()
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

        private fun api(baseUrl: String, bearer: String? = null): SessionRetrofitApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/")
                .client(okHttp { bearer })
                .addConverterFactory(GsonConverterFactory.create(uuidGson))
                .build()
            return retrofit.create(SessionRetrofitApi::class.java)
        }

        fun create(bearerProvider: () -> String? = { null }): RetrofitSessionApi =
            RetrofitSessionApi(bearerProvider)
    }
}
