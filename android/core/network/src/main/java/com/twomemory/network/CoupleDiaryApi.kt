package com.twomemory.network

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import java.util.UUID

interface CoupleDiaryApi {
    suspend fun push(operation: PendingOperation): PushResult

    suspend fun pull(coupleId: UUID, after: Long, limit: Int = 200): ChangePage
}

data class PushResult(
    val operationId: UUID,
    val status: Status,
    val responseBody: String? = null,
) {
    enum class Status { APPLIED, DUPLICATE, CONFLICT, RETRYABLE_FAILURE }
}

data class ChangePage(
    val changes: List<RemoteChange>,
    val nextSequence: Long,
    val hasMore: Boolean,
)
