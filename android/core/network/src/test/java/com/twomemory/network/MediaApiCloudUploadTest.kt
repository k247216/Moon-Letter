package com.twomemory.network

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class MediaApiCloudUploadTest {

    private lateinit var server: ServerSocket
    private lateinit var requests: MutableList<String>
    private lateinit var worker: Thread

    @BeforeTest
    fun startServer() {
        server = ServerSocket(0)
        requests = Collections.synchronizedList(mutableListOf())
        worker = thread(start = true, name = "media-api-test-server") {
            repeat(2) {
                server.accept().use { socket ->
                    val raw = readRequest(socket)
                    requests += raw
                    socket.getOutputStream().use { output ->
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            }
        }
    }

    @AfterTest
    fun stopServer() {
        server.close()
        worker.join(2_000)
    }

    @Test
    fun signedUploadUsesPutWithoutBearerAndCompletionUsesAuthenticatedApi() = runBlocking {
        val api = RetrofitMediaApi.create()
        val assetId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        val image = byteArrayOf(1, 2, 3, 4)

        api.uploadToUrl("$baseUrl/signed-object", image, "image/png")
        api.completeUpload(baseUrl, "cloud-session-token", assetId, "a".repeat(64))

        worker.join(2_000)
        assertEquals(2, requests.size)
        val put = requests[0].lowercase()
        assertContains(put, "put /signed-object http/1.1")
        assertContains(put, "content-type: image/png")
        assertContains(put, "\r\n\r\n\u0001\u0002\u0003\u0004")
        assertFalse(put.contains("authorization:"))

        val complete = requests[1].lowercase()
        assertContains(complete, "post /api/v1/media/$assetId/complete http/1.1")
        assertContains(complete, "authorization: bearer cloud-session-token")
        assertContains(complete, "\"sha256\":\"${"a".repeat(64)}\"")
    }

    private fun readRequest(socket: Socket): String {
        val input = DataInputStream(socket.getInputStream())
        val headerBuffer = ByteArrayOutputStream()
        var previous = 0
        var current: Int
        while (true) {
            current = input.read()
            check(current >= 0) { "request ended before headers" }
            headerBuffer.write(current)
            if (previous == '\r'.code && current == '\n'.code) {
                val data = headerBuffer.toByteArray()
                val size = data.size
                if (size >= 4 && data[size - 4] == '\r'.code.toByte()
                    && data[size - 3] == '\n'.code.toByte()
                    && data[size - 2] == '\r'.code.toByte()
                    && data[size - 1] == '\n'.code.toByte()
                ) {
                    break
                }
            }
            previous = current
        }
        val headers = headerBuffer.toString(Charsets.ISO_8859_1.name())
        val contentLength = Regex("(?im)^content-length: (\\d+)$")
            .find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val body = ByteArray(contentLength)
        input.readFully(body)
        return headers + body.toString(Charsets.ISO_8859_1)
    }
}
