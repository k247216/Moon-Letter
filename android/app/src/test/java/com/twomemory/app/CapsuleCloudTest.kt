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
class CapsuleCloudTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val couple = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private fun card() = CapsuleCard(UUID.randomUUID().toString(), couple.toString(), author.toString(),
        "Confirmed", "secret", "2028-02-29", "SHARED", false, "2026-10-10T00:00:00Z")
    private fun json(card: CapsuleCard) = JSONObject().put("id", card.id).put("coupleId", card.coupleId)
        .put("authorId", card.authorId).put("title", card.title).put("body", card.body)
        .put("unlockDate", card.unlockDate).put("visibility", card.visibility).put("unlocked", card.unlocked)
        .put("updatedAt", card.updatedAt).toString()

    private suspend fun withServer(handler: (String, String) -> Pair<Int, String>, block: suspend () -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool(); server.executor = executor
        server.createContext("/") { exchange ->
            val (status, response) = handler(exchange.requestMethod, exchange.requestBody.bufferedReader().readText())
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, if (status == 204) -1 else bytes.size.toLong())
            exchange.responseBody.use { if (status != 204) it.write(bytes) }
        }
        server.start()
        try {
            SyncSession.save(context, "capsule-test", couple, author, "http://127.0.0.1:${server.address.port}")
            block()
        } finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun lateRefreshCannotEraseConfirmedSave() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val letter = card()
        withServer({ method, _ ->
            if (method == "GET") { entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to "[]" }
            else 200 to json(letter)
        }) {
            val refresh = launch(Dispatchers.IO) { CapsuleCloud.refresh(context) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                CapsuleCloud.save(context, letter)
            } finally { release.countDown() }
            refresh.join()
            assertEquals(letter.id, CapsuleCloud.store(context, SyncSession.load(context)!!).cards().single().id)
        }
    }

    @Test fun staleRefreshCannotResurrectConfirmedDeletion() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val letter = card()
        withServer({ method, _ ->
            if (method == "GET") { entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to "[${json(letter)}]" }
            else 204 to ""
        }) {
            val store = CapsuleCloud.store(context, SyncSession.load(context)!!); store.replace(listOf(letter))
            val refresh = launch(Dispatchers.IO) { CapsuleCloud.refresh(context) }
            try { assertTrue(entered.await(5, TimeUnit.SECONDS)); CapsuleCloud.delete(context, letter) }
            finally { release.countDown() }
            refresh.join(); assertTrue(store.cards().isEmpty())
        }
    }

    @Test fun invalidOrUnavailableSnapshotRetainsConfirmedDataAndEmptySnapshotIsAuthoritative() = runBlocking {
        var response = "[{}]"; var status = 200
        withServer({ _, _ -> status to response }) {
            val store = CapsuleCloud.store(context, SyncSession.load(context)!!); val letter = card(); store.replace(listOf(letter))
            assertFalse(CapsuleCloud.refresh(context)); assertEquals(letter, store.cards().single())
            status = 503; assertFalse(CapsuleCloud.refresh(context)); assertEquals(letter, store.cards().single())
            status = 200; response = "[]"; assertTrue(CapsuleCloud.refresh(context)); assertTrue(store.cards().isEmpty())
        }
    }

    @Test fun accountSwitchDiscardsInFlightSaveAndRefresh() = runBlocking {
        for (mutation in listOf(false, true)) {
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val letter = card()
            withServer({ _, _ -> entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to if (mutation) json(letter) else "[${json(letter)}]" }) {
                val before = SyncSession.load(context)!!; val oldStore = CapsuleCloud.store(context, before)
                val operation = async(Dispatchers.IO) {
                    runCatching { if (mutation) CapsuleCloud.save(context, letter) else CapsuleCloud.refresh(context) }
                }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    SyncSession.save(context, "other-token", couple, UUID.randomUUID(), before.baseUrl)
                } finally { release.countDown() }
                val result = operation.await()
                if (mutation) assertTrue(result.isFailure) else assertEquals(false, result.getOrNull())
                assertTrue(oldStore.cards().isEmpty())
                assertTrue(CapsuleCloud.store(context, SyncSession.load(context)!!).cards().isEmpty())
            }
        }
    }

    @Test fun cancelledRequestsNeverCommitAndCancellationPropagates() = runBlocking {
        for (mutation in listOf(false, true)) {
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val letter = card()
            withServer({ _, _ -> entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to if (mutation) json(letter) else "[${json(letter)}]" }) {
                val store = CapsuleCloud.store(context, SyncSession.load(context)!!)
                var cancelled = false
                val operation = launch(Dispatchers.IO) {
                    try { if (mutation) CapsuleCloud.save(context, letter) else CapsuleCloud.refresh(context) }
                    catch (expected: CancellationException) { cancelled = true; throw expected }
                }
                try { assertTrue(entered.await(5, TimeUnit.SECONDS)); operation.cancel() }
                finally { release.countDown() }
                operation.join(); assertTrue(cancelled); assertTrue(store.cards().isEmpty())
            }
        }
    }

    @Test fun partnerCannotMutateAndHttpFailureIsNotLocalSuccess() = runBlocking {
        var calls = 0
        withServer({ _, _ -> calls++; 503 to "offline" }) {
            val store = CapsuleCloud.store(context, SyncSession.load(context)!!); val letter = card(); store.replace(listOf(letter))
            try { CapsuleCloud.save(context, letter.copy(authorId = UUID.randomUUID().toString())); fail() } catch (_: IllegalArgumentException) { }
            try { CapsuleCloud.delete(context, letter.copy(authorId = UUID.randomUUID().toString())); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(0, calls)
            assertTrue(runCatching { CapsuleCloud.save(context, letter.copy(title = "Unsaved")) }.isFailure)
            assertTrue(runCatching { CapsuleCloud.delete(context, letter) }.isFailure)
            assertEquals(letter, store.cards().single())
        }
    }

    @Test fun switchingAwayAndBackStillDiscardsResponsesFromPreviousBinding() = runBlocking {
        for (mutation in listOf(false, true)) {
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val letter = card()
            withServer({ _, _ -> entered.countDown(); release.await(10, TimeUnit.SECONDS); 200 to if (mutation) json(letter) else "[${json(letter)}]" }) {
                val before = SyncSession.load(context)!!
                val operation = async(Dispatchers.IO) {
                    runCatching { if (mutation) CapsuleCloud.save(context, letter) else CapsuleCloud.refresh(context) }
                }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    SyncSession.save(context, "other-token", couple, UUID.randomUUID(), before.baseUrl)
                    SyncSession.save(context, before.token, before.coupleId, before.userId, before.baseUrl)
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    assertEquals(before, SyncSession.load(context))
                } finally { release.countDown() }
                val result = operation.await()
                if (mutation) assertTrue(result.isFailure) else assertEquals(false, result.getOrNull())
                assertTrue(CapsuleCloud.store(context, before).cards().isEmpty())
            }
        }
    }
}
