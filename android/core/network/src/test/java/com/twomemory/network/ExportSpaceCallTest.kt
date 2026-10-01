package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The 全部回忆 and 按时间范围 exports are this one call, so the URL, the bearer
 * header, the optional date bounds and the failure path are the whole contract.
 * A JDK built-in server stands in for Spring so the test needs no device.
 */
class ExportSpaceCallTest {

    private val coupleId = UUID.fromString("3ab5002b-863b-4ee1-946b-378d449c7704")

    private class Recorded(val path: String, val query: String?, val authorization: String?) {
        fun params(): Map<String, String> = query?.split("&")?.mapNotNull { part ->
            val key = part.substringBefore('=').decoded()
            val value = part.substringAfter('=', "").decoded()
            if (part.contains('=')) key to value else null
        }?.toMap().orEmpty()

        private fun String.decoded() = URLDecoder.decode(this, "UTF-8")
    }

    private fun serverResponding(status: Int, body: String, onCall: (Recorded) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/export") { exchange ->
            onCall(
                Recorded(
                    path = exchange.requestURI.path,
                    query = exchange.requestURI.query,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                )
            )
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    @Test
    fun sendsBearerTokenAndReturnsTheServerJson() = runBlocking {
        var seen: Recorded? = null
        val json = "{\"coupleId\":\"$coupleId\",\"entries\":[]}"
        val server = serverResponding(200, json) { seen = it }
        try {
            val returned = RetrofitSessionApi.create { "unused" }
                .exportSpace("http://127.0.0.1:${server.address.port}", "device-token", coupleId)

            assertEquals(json, returned)
            assertEquals("/api/v1/export", seen?.path)
            assertEquals(mapOf("coupleId" to coupleId.toString()), seen?.params())
            assertEquals("Bearer device-token", seen?.authorization)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun passesDateBoundsOnlyWhenTheCoupleTypedThem() = runBlocking {
        val seen = mutableListOf<Recorded>()
        val server = serverResponding(200, "{}") { seen.add(it) }
        try {
            val api = RetrofitSessionApi.create { "unused" }
            val base = "http://127.0.0.1:${server.address.port}"
            api.exportSpace(base, "device-token", coupleId, "2026-10-01T00:00:00Z", "2026-10-31T23:59:59.999999999Z")
            api.exportSpace(base, "device-token", coupleId, null, null)

            assertEquals("2026-10-01T00:00:00Z", seen[0].params()["from"])
            assertEquals("2026-10-31T23:59:59.999999999Z", seen[0].params()["to"])
            assertNull(seen[1].params()["from"])
            assertNull(seen[1].params()["to"])
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aRejectedExportSurfacesItsStatusCode() = runBlocking {
        val server = serverResponding(403, "forbidden") { }
        try {
            val failure = runCatching {
                RetrofitSessionApi.create { "unused" }
                    .exportSpace("http://127.0.0.1:${server.address.port}", "wrong-token", coupleId)
            }.exceptionOrNull()

            assertTrue(failure is SetupHttpException, "expected SetupHttpException, got $failure")
            assertEquals(403, (failure as SetupHttpException).code)
        } finally {
            server.stop(0)
        }
    }
}
