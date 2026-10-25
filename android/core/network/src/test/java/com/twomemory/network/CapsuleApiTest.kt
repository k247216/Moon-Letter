package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CapsuleApiTest {
    private val couple = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private val id = UUID.randomUUID().toString()
    private fun letter(body: String = "\"secret\"", unlocked: String = "false", visibility: String = "SHARED") =
        """{"id":"$id","coupleId":"$couple","authorId":"$author","title":"A letter","body":$body,"unlockDate":"2028-02-29","visibility":"$visibility","unlocked":$unlocked,"updatedAt":"2026-10-10T00:00:00Z"}"""

    private fun server(response: String, block: suspend (CapsuleApi) -> Unit) = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block(CapsuleApi("http://127.0.0.1:${server.address.port}", "test", couple)) }
        finally { server.stop(0) }
    }

    @Test fun sealedPartnerEnvelopeAllowsNullOrEmptyButRejectsBodyLeak() {
        for (body in listOf("null", "\"\"")) server("[${letter(body)}]") { api ->
            assertNull(api.read(partner.toString()).single().readableBody(partner.toString()))
        }
        server("[${letter()}]") { api -> assertFailsWith<IllegalArgumentException> { api.read(partner.toString()) } }
    }

    @Test fun authorCanRestoreSealedTextAndPartnerRequiresServerUnlock() {
        server("[${letter()}]") { api -> assertEquals("secret", api.read(author.toString()).single().body) }
        server("[${letter(unlocked = "true")}]") { api ->
            assertEquals("secret", api.read(partner.toString()).single().readableBody(partner.toString()))
        }
        server("[${letter("null", "true")}]") { api -> assertFailsWith<IllegalArgumentException> { api.read(partner.toString()) } }
        server("[${letter(visibility = "PRIVATE")}]") { api -> assertFailsWith<IllegalArgumentException> { api.read(partner.toString()) } }
    }

    @Test fun malformedSnapshotsRejectMissingOrCoercedFieldsAndDuplicateIds() {
        val valid = letter()
        val invalid = listOf("null", "{}", "[{}]", "[$valid,$valid]",
            "[${valid.replace("\"unlocked\":false,", "")}]",
            "[${letter(unlocked = "\"false\"")}]",
            "[${valid.replace("2028-02-29", "2027-02-29")}]",
            "[${valid.replace("2028-02-29", "0000-01-01")}]",
            "[${valid.replace(couple.toString(), UUID.randomUUID().toString())}]",
            "[${valid.replace("\"title\":\"A letter\"", "\"title\":42")}]",
            "[${valid.replace("2026-10-10T00:00:00Z", "today")}]",
            "[${letter(visibility = "PUBLIC")}]")
        for (response in invalid) server(response) { api ->
            assertFails("accepted $response") { api.read(author.toString()) }
        }
    }

    @Test fun putSendsOnlyRequestFieldsAndVerifiesReturnedIdentity() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var request = ""; var path = ""; var auth = ""
        server.createContext("/") { exchange ->
            request = exchange.requestBody.bufferedReader().readText()
            path = exchange.requestURI.path; auth = exchange.requestHeaders.getFirst("Authorization")
            val bytes = letter().toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = CapsuleApi("http://127.0.0.1:${server.address.port}", "test", couple)
            api.save(id, CapsuleRequest("A letter", "secret", "2028-02-29", "SHARED"), author.toString())
            assertEquals("/api/v1/couple/$couple/capsules/$id", path)
            assertEquals("Bearer test", auth)
            val json = com.google.gson.JsonParser.parseString(request).asJsonObject
            assertEquals(setOf("title", "body", "unlockDate", "visibility"), json.keySet())
            assertEquals("secret", json["body"].asString)
            assertFails { api.save(UUID.randomUUID().toString(), CapsuleRequest("A letter", "secret", "2028-02-29", "SHARED"), author.toString()) }
            Unit
        } finally { server.stop(0) }
    }
}
