package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import com.twomemory.model.PendingOperation
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPushHttpStatusTest {
    @Test fun permanentClientRejectionsAreParked() = runBlocking {
        for (code in listOf(400, 403, 404, 405, 410, 413, 415, 422)) {
            assertEquals("HTTP $code must stop automatic retries", PushResult.Status.CONFLICT, pushStatus(code))
        }
    }

    @Test fun temporaryFailuresRemainRetryable() = runBlocking {
        for (code in listOf(408, 429, 500, 502, 503, 504)) {
            assertEquals("HTTP $code", PushResult.Status.RETRYABLE_FAILURE, pushStatus(code))
        }
    }

    @Test fun unauthorizedAndConflictKeepExistingContracts() = runBlocking {
        assertEquals(PushResult.Status.UNAUTHORIZED, pushStatus(401))
        assertEquals(PushResult.Status.CONFLICT, pushStatus(409))
        assertEquals(PushResult.Status.APPLIED, pushStatus(200))
    }

    private suspend fun pushStatus(code: Int): PushResult.Status {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/sync/operations") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val bytes = "{}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = RetrofitCoupleDiaryApi.create("http://127.0.0.1:${server.address.port}") { "test-token" }
            return api.push(PendingOperation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CREATE_ENTRY", "{}", 0, 0)).status
        } finally {
            server.stop(0)
        }
    }
}
