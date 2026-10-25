package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class SessionTransportSafetyTest {
    @Test fun repeatedTextPollsReuseConnectionsWithoutSharingTokens() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val ports = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val tokens = java.util.concurrent.CopyOnWriteArrayList<String>()
        server.createContext("/api/") { response ->
            ports.add(response.remoteAddress.port)
            tokens.add(response.requestHeaders.getFirst("Authorization"))
            val bytes = "{\"changes\":[],\"nextSequence\":0,\"hasMore\":false}".toByteArray()
            response.responseHeaders.add("Content-Type", "application/json")
            response.sendResponseHeaders(200, bytes.size.toLong())
            response.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            for (token in listOf("text-A", "text-B", "text-A")) {
                val page = RetrofitCoupleDiaryApi.create(base) { token }.pull(UUID.randomUUID(), 0, 50)
                assertTrue(page.changes.isEmpty())
                assertEquals(0L, page.nextSequence)
            }
            assertEquals(listOf("Bearer text-A", "Bearer text-B", "Bearer text-A"), tokens.toList())
            assertEquals("Text polling must reuse its transport", 1, ports.toSet().size)
        } finally { server.stop(0) }
    }

    @Test fun repeatedProfileReadsReuseTheConnectionWithoutReusingAnotherAccountsToken() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val ports = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val tokens = java.util.concurrent.CopyOnWriteArrayList<String>()
        val couple = UUID.randomUUID()
        server.createContext("/api/") { response ->
            ports.add(response.remoteAddress.port)
            tokens.add(response.requestHeaders.getFirst("Authorization"))
            val bytes = "{\"id\":\"$couple\",\"members\":[]}".toByteArray()
            response.responseHeaders.add("Content-Type", "application/json")
            response.sendResponseHeaders(200, bytes.size.toLong())
            response.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            for (token in listOf("test-owner-A", "test-owner-B", "test-owner-A")) {
                assertEquals(couple, RetrofitSessionApi.create().readSpace(base, token, couple).id)
            }
            assertEquals(listOf("Bearer test-owner-A", "Bearer test-owner-B", "Bearer test-owner-A"), tokens.toList())
            assertEquals("Repeated reads should not create a fresh TCP connection", 1, ports.toSet().size)
        } finally { server.stop(0) }
    }

    @Test fun spaceVerificationRejectsRedirectInsteadOfFollowingAnUnverifiedEndpoint() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val followed = AtomicInteger()
        val couple = UUID.randomUUID()
        server.createContext("/api/") { response ->
            response.responseHeaders.add("Location", "/unverified")
            response.sendResponseHeaders(302, -1); response.close()
        }
        server.createContext("/unverified") { response ->
            followed.incrementAndGet()
            val bytes = "{\"id\":\"$couple\",\"members\":[]}".toByteArray()
            response.sendResponseHeaders(200, bytes.size.toLong()); response.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = runCatching { RetrofitSessionApi.create().readSpace("http://127.0.0.1:${server.address.port}", "test-only", couple) }
            assertTrue("A redirect is not proof of server identity", result.exceptionOrNull() is SetupHttpException)
            assertEquals(0, followed.get())
        } finally { server.stop(0) }
    }
}
