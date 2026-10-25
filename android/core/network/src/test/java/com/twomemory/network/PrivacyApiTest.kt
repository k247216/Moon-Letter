package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.Test

class PrivacyApiTest {
    @Test fun oldBackendCannotSilentlyAcceptPrivateContent(): Unit = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "{}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            assertFailsWith<java.io.IOException> {
                PrivacyApi("http://127.0.0.1:${server.address.port}", "test").requireSupport()
            }
        } finally { server.stop(0) }
    }
}
