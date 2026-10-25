package com.twomemory.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SharedAlbumCallTest {
    private val space = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val album = UUID.fromString("00000000-0000-0000-0000-000000000011")
    @Test fun readsDeletionMarkersAndPhotosUsingMemberToken() = runBlocking {
        var token = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/couple/$space/albums") { exchange ->
            token = exchange.requestHeaders.getFirst("Authorization")
            val body = """[{"id":"$album","name":"旅行","category":"旅途","createdBy":"$album",
                "createdAtEpochMillis":1234,"deleted":true,"photos":[{"id":"$album","assetId":"$album",
                "addedBy":"$album","takenAtEpochMillis":4567,"mimeType":"image/png","width":20,"height":30,"deleted":true}]}]"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            val remote = SharedAlbumApi("http://127.0.0.1:${server.address.port}", "token", space).read().single()
            assertEquals("Bearer token", token)
            assertEquals("旅行", remote.name)
            assertTrue(remote.deleted)
            assertTrue(remote.photos.single().deleted)
            assertEquals(4567L, remote.photos.single().takenAtEpochMillis)
        } finally { server.stop(0) }
    }
    @Test fun writesUseStableAlbumIdAndDoNotTreatServerRejectionAsSuccess() = runBlocking {
        var method = ""; var content = ""; var status = 200
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/couple/$space/albums/$album") { exchange ->
            method = exchange.requestMethod
            content = exchange.requestBody.bufferedReader().readText()
            exchange.sendResponseHeaders(status, -1); exchange.close()
        }
        server.start()
        try {
            val api = SharedAlbumApi("http://127.0.0.1:${server.address.port}/", "token", space)
            api.saveAlbum(album, "旅行", "旅途")
            assertEquals("PUT", method)
            assertTrue(content.contains("\"category\":\"旅途\""))
            status = 403
            assertFailsWith<IOException> { api.deleteAlbum(album) }
            Unit
        } finally { server.stop(0) }
    }
}
