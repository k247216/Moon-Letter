package com.twomemory.network

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import java.io.IOException
import java.util.UUID

interface CoupleDiaryApi {
    suspend fun push(operation: PendingOperation): PushResult

    suspend fun pull(coupleId: UUID, after: Long, limit: Int = 200): ChangePage
}

/** Thrown when the server answers 401: the session is gone, re-pair needed. */
class HttpUnauthorizedException(message: String = "session expired") : IOException(message)

data class PushResult(
    val operationId: UUID,
    val status: Status,
    val responseBody: String? = null,
) {
    enum class Status { APPLIED, DUPLICATE, CONFLICT, UNAUTHORIZED, RETRYABLE_FAILURE }
}

data class ChangePage(
    val changes: List<RemoteChange>,
    val nextSequence: Long,
    val hasMore: Boolean,
)
