package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlin.test.*

class MediaFileTransportTest {
    @Test fun cancellationReleasesCompletionWithoutWaitingForServerReply() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/media/") { exchange ->
            exchange.requestBody.use { it.readBytes() }; entered.countDown()
            release.await(3, java.util.concurrent.TimeUnit.SECONDS)
            runCatching { exchange.sendResponseHeaders(200,-1); exchange.close() }
        }
        server.start()
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            RetrofitMediaApi.create().completeUpload("http://127.0.0.1:${server.address.port}","test",UUID.randomUUID(),"a".repeat(64))
        }
        try {
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS))
            kotlinx.coroutines.withTimeout(500) { job.cancel(); job.join() }
        } finally { release.countDown(); job.cancel(); server.stop(0) }
    }
    @Test fun fileUploadAndBoundedDownloadPreserveOriginalBytes() = runBlocking {
        val bytes = ByteArray(131073) { (it % 251).toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val id = UUID.randomUUID()
        var uploaded: ByteArray? = null
        server.createContext("/api/v1/media/$id/data") { exchange ->
            if (exchange.requestMethod == "POST") {
                uploaded = exchange.requestBody.use { it.readBytes() }
                exchange.sendResponseHeaders(200, -1)
            } else {
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        val source = File.createTempFile("media-source", ".bin").apply { writeBytes(bytes) }
        val target = File.createTempFile("media-target", ".bin").apply { writeBytes(byteArrayOf(9)) }
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val api = RetrofitMediaApi.create()
            api.uploadFileData(base, "test", id, source, "application/octet-stream")
            assertContentEquals(bytes, uploaded)
            assertFailsWith<java.io.IOException> { api.downloadToFile(base, "test", id, target, 1024) }
            assertContentEquals(byteArrayOf(9), target.readBytes())
            api.downloadToFile(base, "test", id, target, bytes.size.toLong())
            assertContentEquals(bytes, target.readBytes())
        } finally { server.stop(0); source.delete(); target.delete() }
    }
}
