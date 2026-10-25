package com.twomemory.sync

import com.sun.net.httpserver.HttpServer
import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import com.twomemory.network.RetrofitCoupleDiaryApi
import java.net.InetSocketAddress
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncRejectionHttpTest {
    private val coupleId = UUID.randomUUID()

    @Test fun permanentRejectionHoldsSameEntityButAllowsUnrelatedEntityInPreloadedBatch() = runBlocking {
        for (code in listOf(400, 403, 404, 422)) verifyRejection(code)
    }

    @Test fun existing409ConflictAlsoHoldsPreloadedSameEntityOperations() = runBlocking {
        verifyRejection(409)
    }

    private suspend fun verifyRejection(code: Int) {
        withServer(code) { api, requests ->
                val entity = UUID.randomUUID()
                val first = operation(entity)
                val later = operation(entity)
                val unrelated = operation(UUID.randomUUID())
                val store = QueueStore(listOf(first, later, unrelated))
                val engine = SyncEngine(api, store, coupleId)
                val result = engine.pushPending()
                assertEquals("HTTP $code: unrelated record must be sent", 1, result.applied)
                assertEquals(1, result.conflicts)
                assertEquals(0, result.retried)
                assertEquals(listOf(first.operationId, unrelated.operationId), requests.toList())
                assertEquals(setOf(first.operationId), store.conflicts)
                assertEquals(listOf(later.operationId), store.operations.map { it.operationId }.filter { it !in store.conflicts })
                assertTrue(store.retries.isEmpty())
                engine.pushPending()
                assertEquals("Parked entity must not be automatically sent next cycle", 2, requests.size)
        }
    }

    @Test fun temporaryFailureStillStopsGlobalRetryFifo() = runBlocking {
        for (code in listOf(408, 429, 500, 503)) {
            withServer(code) { api, requests ->
                val first = operation(UUID.randomUUID())
                val store = QueueStore(listOf(first, operation(UUID.randomUUID())))
                val result = SyncEngine(api, store, coupleId).pushPending()
                assertEquals(1, result.retried)
                assertEquals(0, result.applied)
                assertEquals(listOf(first.operationId), requests.toList())
                assertEquals(listOf(first.operationId), store.retries)
                assertTrue(store.conflicts.isEmpty())
            }
        }
    }

    @Test fun immediate408TransportReplayKeepsSameOperationAndStillStopsFifo() = runBlocking {
        withServer(408, allowImmediateTimeoutReplay = true) { api, requests ->
            val first = operation(UUID.randomUUID())
            val second = operation(UUID.randomUUID())
            val store = QueueStore(listOf(first, second))
            val result = SyncEngine(api, store, coupleId).pushPending()
            assertEquals(1, result.retried)
            assertEquals(0, result.applied)
            assertEquals(0, result.conflicts)
            // OkHttp repeats one 408 with the original payload, then gives up
            // on the consecutive 408. SyncEngine must not send the next record.
            assertEquals(listOf(first.operationId, first.operationId), requests.toList())
            assertEquals(listOf(first.operationId), store.retries)
            assertEquals(listOf(first.operationId, second.operationId), store.operations.map { it.operationId })
            assertTrue(store.conflicts.isEmpty())
        }
    }

    @Test fun unauthorizedStopsWithoutParkingOrRetrying() = runBlocking {
        withServer(401) { api, requests ->
            val first = operation(UUID.randomUUID())
            val store = QueueStore(listOf(first, operation(UUID.randomUUID())))
            val result = SyncEngine(api, store, coupleId).pushPending()
            assertTrue(result.needsRePair)
            assertEquals(listOf(first.operationId), requests.toList())
            assertTrue(store.conflicts.isEmpty())
            assertTrue(store.retries.isEmpty())
        }
    }

    private fun operation(entity: UUID) = PendingOperation(UUID.randomUUID(), coupleId, entity,
        "CREATE_ENTRY", "{}", 0, 0)

    private suspend fun withServer(
        code: Int,
        allowImmediateTimeoutReplay: Boolean = false,
        block: suspend (RetrofitCoupleDiaryApi, List<UUID>) -> Unit,
    ) {
        val requests = Collections.synchronizedList(mutableListOf<UUID>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/sync/operations") { exchange ->
            val body = exchange.requestBody.use { String(it.readBytes(), Charsets.UTF_8) }
            val operationId = Regex("\"operationId\"\\s*:\\s*\"([^\"]+)\"").find(body)!!.groupValues[1]
            requests.add(UUID.fromString(operationId))
            // Keep rejecting this operation even if OkHttp replays it. A later
            // operation gets 200 so sending it incorrectly is observable.
            val status = if (requests.last() == requests.first()) code else 200
            val bytes = "{}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (status == 408 && !allowImmediateTimeoutReplay) {
                // Positive Retry-After suppresses OkHttp's immediate follow-up;
                // the usual case then tests SyncEngine's own retry scheduling.
                exchange.responseHeaders.add("Retry-After", "1")
            }
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(RetrofitCoupleDiaryApi.create("http://127.0.0.1:${server.address.port}") { "test-token" }, requests)
        } finally {
            server.stop(0)
        }
    }

    // Models the existing DAO's persisted CONFLICT guard, while returning a
    // snapshot so SyncEngine must protect the already preloaded batch itself.
    private class QueueStore(initial: List<PendingOperation>) : SyncStore {
        val operations = initial.toMutableList()
        val conflicts = mutableSetOf<UUID>()
        val retries = mutableListOf<UUID>()
        override suspend fun pendingOperations(limit: Int): List<PendingOperation> =
            operations.filterIndexed { index, operation ->
                operation.operationId !in conflicts && operations.take(index).none {
                    it.entityId == operation.entityId && it.operationId in conflicts
                }
            }.take(limit)
        override suspend fun markApplied(operationId: UUID, serverEntrySnapshot: String?) {
            operations.removeAll { it.operationId == operationId }
        }
        override suspend fun markConflict(operationId: UUID) { conflicts.add(operationId) }
        override suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
            retries.add(operationId)
        }
        override suspend fun currentCursor(coupleId: UUID) = 0L
        override suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long) = Unit
    }
}
