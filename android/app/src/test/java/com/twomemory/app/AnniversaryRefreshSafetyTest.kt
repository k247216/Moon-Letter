package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnniversaryRefreshSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val couple = UUID.randomUUID()
    private val user = UUID.randomUUID()
    private val tools get() = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)

    @Test fun oldAnniversaryCannotOverwriteNewBindingCache() = runBlocking { lateResponse(roundTrip = false) }
    @Test fun oldAnniversaryCannotOverwriteAfterAToBToA() = runBlocking { lateResponse(roundTrip = true) }

    @Test fun cancelledAnniversaryResponseRetainsCacheAndPropagatesCancellation() = runBlocking {
        withServer { _, entered, release ->
            seedCache()
            val propagated = AtomicBoolean()
            val cycle = launch(Dispatchers.IO) {
                try { SyncSession.runCycleSideEffects(context, scheduleAlbums = false) }
                catch (cancelled: CancellationException) { propagated.set(true); throw cancelled }
            }
            try {
                assertTrue("Earlier auxiliary endpoints must finish and anniversary must enter", entered.await(5, TimeUnit.SECONDS))
                cycle.cancel()
                release.countDown()
                withTimeout(5_000) { cycle.join() }
                assertCacheIntact()
                assertTrue("Cancellation must escape the side-effect cycle", propagated.get())
            } finally { cycle.cancel(); release.countDown(); cycle.join() }
        }
    }

    @Test fun currentAnniversaryResponseUpdatesCacheNormally() = runBlocking {
        withServer { _, _, release ->
            seedCache()
            release.countDown()
            SyncSession.runCycleSideEffects(context, scheduleAlbums = false)
            assertEquals("Remote anniversary", tools.getString("anniversaryName", null))
            assertEquals("2020-05-09", tools.getString("anniversaryDate", null))
            assertFalse(tools.getBoolean("anniversaryRepeats", true))
        }
    }

    private suspend fun lateResponse(roundTrip: Boolean) {
        withServer { before, entered, release ->
            val cycle = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
                SyncSession.runCycleSideEffects(context, scheduleAlbums = false)
            }
            try {
                assertTrue("Anniversary must be held after preceding HTTP calls", entered.await(5, TimeUnit.SECONDS))
                SyncSession.save(context, "other-test-token", UUID.randomUUID(), UUID.randomUUID(), before.baseUrl)
                if (roundTrip) {
                    SyncSession.save(context, before.token, before.coupleId, before.userId, before.baseUrl)
                    assertEquals(before, SyncSession.load(context))
                }
                seedCache()
                release.countDown()
                withTimeout(5_000) { cycle.join() }
                assertCacheIntact()
            } finally { cycle.cancel(); release.countDown(); cycle.join() }
        }
    }

    private fun seedCache() {
        assertTrue(tools.edit().putString("anniversaryName", "Confirmed anniversary")
            .putString("anniversaryDate", "2021-06-10").putBoolean("anniversaryRepeats", true).commit())
    }
    private fun assertCacheIntact() {
        assertEquals("Confirmed anniversary", tools.getString("anniversaryName", null))
        assertEquals("2021-06-10", tools.getString("anniversaryDate", null))
        assertTrue(tools.getBoolean("anniversaryRepeats", false))
    }

    private suspend fun withServer(block: suspend (SyncSession.Session, CountDownLatch, CountDownLatch) -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                val path = exchange.requestURI.path
                val json = when {
                    path.endsWith("/anniversary") -> {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS)) { "Test did not release anniversary" }
                        """{"id":"${UUID.randomUUID()}","coupleId":"$couple","title":"Remote anniversary","calendarType":"GREGORIAN","month":5,"day":9,"startYear":2020,"repeatsYearly":false}"""
                    }
                    path.endsWith("/occasions") || path.endsWith("/capsules") -> "[]"
                    path == "/api/v1/couple/$couple" ->
                        """{"id":"$couple","members":[{"userId":"$user","profile":{"displayName":"Test owner","avatarAssetId":null}}]}"""
                    else -> error("Unexpected HTTP request: $path")
                }
                val bytes = json.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) { /* Cancellation closes the client socket. */ }
            finally { exchange.close() }
        }
        server.start()
        try {
            SyncSession.save(context, "anniversary-test-token", couple, user, "http://127.0.0.1:${server.address.port}")
            block(requireNotNull(SyncSession.load(context)), entered, release)
        } finally { release.countDown(); server.stop(0); executor.shutdownNow() }
    }
}
