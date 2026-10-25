package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OccasionRefreshConfirmationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val couple = UUID.randomUUID()
    private val user = UUID.randomUUID()
    private fun letter() = OccasionCard(UUID.randomUUID().toString(), couple.toString(), user.toString(), "BIRTHDAY",
        "保留的生日", "", "2000-05-09", true, null, null, "2026-10-10T00:00:00Z")
    private suspend fun fixture(handler: (String, String) -> Pair<Int, String>, block: suspend () -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool(); server.executor = executor
        server.createContext("/") { exchange ->
            val (status, response) = handler(exchange.requestMethod, exchange.requestBody.bufferedReader().readText())
            val bytes = response.toByteArray(); exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            SyncSession.save(context, "occasion-confirmation", couple, user, "http://127.0.0.1:${server.address.port}")
            block()
        } finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun failedOrMalformedSnapshotReturnsFalseAndRetainsConfirmedCards() = runBlocking {
        for ((status, response) in listOf(503 to "offline", 200 to "[{}]")) fixture({ _, _ -> status to response }) {
            val store = OccasionCloud.store(context, SyncSession.load(context)!!); val card = letter(); store.replace(listOf(card))
            assertEquals(false, OccasionCloud.refresh(context))
            assertEquals(card, store.cards().single())
        }
    }
    @Test fun confirmedEmptySnapshotReturnsTrueAndClearsRevokedCards() = runBlocking {
        fixture({ _, _ -> 200 to "[]" }) {
            val store = OccasionCloud.store(context, SyncSession.load(context)!!); store.replace(listOf(letter()))
            assertEquals(true, OccasionCloud.refresh(context)); assertTrue(store.cards().isEmpty())
        }
    }
    @Test fun accountChangeReturnsFalseWithoutWritingTheOldSnapshot() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        fixture({ _, _ -> entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to "[]" }) {
            val before = SyncSession.load(context)!!; val store = OccasionCloud.store(context, before); val card = letter(); store.replace(listOf(card))
            val request = async(Dispatchers.IO) { OccasionCloud.refresh(context) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                SyncSession.save(context, "other", couple, UUID.randomUUID(), before.baseUrl)
            } finally { release.countDown() }
            assertEquals(false, request.await()); assertEquals(card, store.cards().single())
        }
    }
    @Test fun mutationMakesOldSnapshotUnconfirmedInsteadOfClaimingRefreshSuccess() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val card = letter()
        fixture({ method, raw ->
            if (method == "GET") { entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to "[]" }
            else 200 to JSONObject(raw).put("id", card.id).put("coupleId", card.coupleId).put("authorId", card.authorId)
                .put("updatedAt", card.updatedAt).toString()
        }) {
            val request = async(Dispatchers.IO) { OccasionCloud.refresh(context) }
            try { assertTrue(entered.await(5, TimeUnit.SECONDS)); OccasionCloud.save(context, card) }
            finally { release.countDown() }
            assertEquals(false, request.await())
            assertEquals(card.id, OccasionCloud.store(context, SyncSession.load(context)!!).cards().single().id)
        }
    }
}
