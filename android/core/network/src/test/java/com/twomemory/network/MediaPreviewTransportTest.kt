package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MediaPreviewTransportTest {
    @Test fun leavingViewportCancelsSlowResponseWithoutWaitingForReadTimeout() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = java.util.concurrent.CountDownLatch(1)
        server.createContext("/") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 1000)
                exchange.responseBody.write(byteArrayOf(1)); exchange.responseBody.flush()
                release.await(3, java.util.concurrent.TimeUnit.SECONDS)
            } finally { exchange.close() }
        }
        server.start()
        try {
            val start = System.nanoTime()
            assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                kotlinx.coroutines.withTimeout(250) {
                    RetrofitMediaApi.create().downloadPreview("http://127.0.0.1:${server.address.port}", "test", UUID.randomUUID())
                }
            }
            assertTrue((System.nanoTime() - start) / 1_000_000 < 1500, "Cancellation must not wait for the slow response body")
        } finally { release.countDown(); server.stop(0) }
    }

    @Test fun previewAndOriginalUseDistinctAuthenticatedRoutes() = runBlocking {
        val id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val requests = mutableListOf<Pair<String, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(exchange.requestURI.path to exchange.requestHeaders.getFirst("Authorization"))
            val data = if (exchange.requestURI.path.endsWith("/thumbnail")) byteArrayOf(1, 2) else byteArrayOf(3, 4, 5)
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
        server.start()
        try {
            val api = RetrofitMediaApi.create(); val base = "http://127.0.0.1:${server.address.port}"
            assertContentEquals(byteArrayOf(1, 2), api.downloadPreview(base, "test-only", id))
            assertContentEquals(byteArrayOf(3, 4, 5), api.download(base, "test-only", id))
            assertEquals(listOf<Pair<String, String?>>("/api/v1/media/$id/thumbnail" to "Bearer test-only", "/api/v1/media/$id/data" to "Bearer test-only"), requests)
        } finally { server.stop(0) }
    }
}
