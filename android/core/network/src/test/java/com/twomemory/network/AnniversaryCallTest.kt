package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class AnniversaryCallTest {

    private val coupleId = UUID.fromString("3ab5002b-863b-4ee1-946b-378d449c7704")

    @Test
    fun readsTheSharedAnniversaryWithBearerToken() = runBlocking {
        var authorization: String? = null
        val server = server { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            respond(exchange, 200, """
                {"id":"00000000-0000-0000-0000-000000000011","coupleId":"$coupleId",
                 "title":"相遇日","calendarType":"GREGORIAN","month":10,"day":6,
                 "lunarLeapMonth":false,"startYear":2026,"repeatsYearly":true}
            """.trimIndent())
        }
        try {
            val anniversary = RetrofitSessionApi.create { "unused" }
                .readAnniversary("http://127.0.0.1:${server.address.port}", "device-token", coupleId)

            assertEquals("相遇日", anniversary?.title)
            assertEquals(10, anniversary?.month)
            assertEquals("Bearer device-token", authorization)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aMissingSharedAnniversaryIsAnEmptyResult() = runBlocking {
        val server = server { exchange -> respond(exchange, 404, "") }
        try {
            val anniversary = RetrofitSessionApi.create { "unused" }
                .readAnniversary("http://127.0.0.1:${server.address.port}", "device-token", coupleId)
            assertNull(anniversary)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun savesTheSharedAnniversaryAsAnUpsert() = runBlocking {
        var method: String? = null
        var body: String? = null
        val server = server { exchange ->
            method = exchange.requestMethod
            body = exchange.requestBody.bufferedReader().readText()
            respond(exchange, 200, """
                {"id":"00000000-0000-0000-0000-000000000011","coupleId":"$coupleId",
                 "title":"相遇日","calendarType":"GREGORIAN","month":10,"day":6,
                 "lunarLeapMonth":false,"startYear":2026,"repeatsYearly":true}
            """.trimIndent())
        }
        try {
            val anniversary = RetrofitSessionApi.create { "unused" }.saveAnniversary(
                "http://127.0.0.1:${server.address.port}",
                "device-token",
                coupleId,
                SaveAnniversaryRequestDto("相遇日", "GREGORIAN", 10, 6, false, 2026, true),
            )

            assertEquals("相遇日", anniversary.title)
            assertEquals("PUT", method)
            assertEquals(true, body?.contains("\"repeatsYearly\":true"))
            assertEquals(true, body?.contains("\"coupleId\"") == false)
        } finally {
            server.stop(0)
        }
    }

    private fun server(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/couple/$coupleId/anniversary", handler)
        server.start()
        return server
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
