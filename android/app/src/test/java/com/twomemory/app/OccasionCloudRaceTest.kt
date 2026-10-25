package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OccasionCloudRaceTest {
    @Test fun lateSnapshotCannotEraseAConfirmedNewCard() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val couple = UUID.randomUUID(); val user = UUID.randomUUID(); val id = UUID.randomUUID().toString()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            val response = if (exchange.requestMethod == "GET") { entered.countDown(); release.await(10, TimeUnit.SECONDS); "[]" }
                else JSONObject(exchange.requestBody.bufferedReader().readText()).put("id", id).put("coupleId", couple.toString())
                    .put("authorId", user.toString()).put("updatedAt", "2026-10-04T00:00:00Z").toString()
            val bytes = response.toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            SyncSession.save(context, "race-test", couple, user, "http://127.0.0.1:${server.address.port}")
            val refresh = launch(Dispatchers.IO) { OccasionCloud.refresh(context) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val card = OccasionCard(id, couple.toString(), user.toString(), "BIRTHDAY", "新生日", "", "2020-05-09", true, null, null, "")
            OccasionCloud.save(context, card)
            release.countDown(); refresh.join()
            assertEquals("新生日", OccasionCloud.store(context, SyncSession.load(context)!!).cards().single().title)
        } finally { release.countDown(); server.stop(0); (server.executor as java.util.concurrent.ExecutorService).shutdownNow() }
    }
    @Test fun malformedSuccessfulSnapshotDoesNotEraseConfirmedCards() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> val bytes = "[{}]".toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) } }
        server.start()
        try {
            val couple = UUID.randomUUID(); val user = UUID.randomUUID()
            SyncSession.save(context, "malformed-test", couple, user, "http://127.0.0.1:${server.address.port}")
            val store = OccasionCloud.store(context, SyncSession.load(context)!!)
            store.replace(listOf(OccasionCard(UUID.randomUUID().toString(), couple.toString(), user.toString(), "BIRTHDAY", "保留的生日", "", "2020-05-09", true, null, null, "")))
            OccasionCloud.refresh(context)
            assertEquals("保留的生日", store.cards().single().title)
        } finally { server.stop(0) }
    }
}
