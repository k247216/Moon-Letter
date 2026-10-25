package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OccasionApiCancellationTest {
    @Test fun cancelReadBeforeHeadersCompletesWithoutWaitingForServer() = runBlocking {
        verifyCancellation(stallBody = false)
    }

    @Test fun cancelReadDuringBodyCompletesWithoutWaitingForServer() = runBlocking {
        verifyCancellation(stallBody = true)
    }

    private suspend fun verifyCancellation(stallBody: Boolean) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val propagated = AtomicBoolean()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                if (stallBody) {
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.write('['.code)
                    exchange.responseBody.flush()
                }
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Test did not release response" }
                if (stallBody) exchange.responseBody.write(']'.code)
                else {
                    exchange.sendResponseHeaders(200, 2)
                    exchange.responseBody.write("[]".toByteArray())
                }
            } catch (_: java.io.IOException) { /* Expected when cancellation closes the socket. */ }
            finally { exchange.close() }
        }
        server.start()
        val api = OccasionApi("http://127.0.0.1:${server.address.port}", "occasion-test-token", UUID.randomUUID())
        val request = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            try { api.read() }
            catch (cancelled: CancellationException) { propagated.set(true); throw cancelled }
        }
        try {
            assertTrue("Real HTTP request must enter the held response", entered.await(5, TimeUnit.SECONDS))
            request.cancel()
            val completed = withTimeoutOrNull(2_000) { request.join(); true } ?: false
            assertTrue("Cancellation must finish while server still withholds ${if (stallBody) "body" else "headers"}", completed)
            assertTrue("Caller must observe CancellationException", propagated.get())
            assertEquals("The response must remain held through the assertion", 1L, release.count)
        } finally {
            release.countDown()
            request.cancel()
            withTimeout(5_000) { request.join() }
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
