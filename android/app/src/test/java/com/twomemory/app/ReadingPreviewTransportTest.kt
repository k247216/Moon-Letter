package com.twomemory.app

import com.twomemory.network.RetrofitMediaApi
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReadingPreviewTransportTest {
    @Test fun readingRequestsItsOwnBoundedVariantRatherThanEnlargingTheListThumbnail() = runBlocking {
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val request = executor.submit<String> {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val first = reader.readLine()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc".toByteArray())
                    first
                }
            }
            val asset = UUID.randomUUID()
            val bytes = RetrofitMediaApi.create().downloadReadingPreview("http://127.0.0.1:${server.localPort}", "synthetic-reading", asset)
            assertArrayEquals("abc".toByteArray(), bytes)
            assertEquals("GET /api/v1/media/$asset/thumbnail?maxEdge=1200 HTTP/1.1", request.get())
        } finally { server.close(); executor.shutdownNow() }
    }
}
