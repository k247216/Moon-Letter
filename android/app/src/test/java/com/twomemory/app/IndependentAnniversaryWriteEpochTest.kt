package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import com.twomemory.network.RetrofitSessionApi
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndependentAnniversaryWriteEpochTest {
    @Test fun getStartedBeforeSaveCannotPublishDuringSaveOrAfterItsAcknowledgement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        val getEntered = CountDownLatch(1); val putEntered = CountDownLatch(1)
        val releaseGet = CountDownLatch(1); val releasePut = CountDownLatch(1)
        val couple = UUID.randomUUID(); val user = UUID.randomUUID(); val id = UUID.randomUUID()
        server.createContext("/api/v1/couple/$couple/anniversary") { exchange ->
            try {
                val put = exchange.requestMethod == "PUT"
                if (put) {
                    exchange.requestBody.use { it.readBytes() }
                    putEntered.countDown(); check(releasePut.await(10, TimeUnit.SECONDS))
                } else { getEntered.countDown(); check(releaseGet.await(10, TimeUnit.SECONDS)) }
                val bytes = """{"id":"$id","coupleId":"$couple","title":"${if (put) "新确认" else "旧 GET"}","calendarType":"GREGORIAN","month":9,"day":24,"lunarLeapMonth":false,"startYear":${if (put) 2020 else 2019},"repeatsYearly":true}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) { } finally { exchange.close() }
        }
        server.start()
        SyncSession.save(context, "synthetic-write-epoch", couple, user, "http://127.0.0.1:${server.address.port}")
        val binding = requireNotNull(SyncSession.captureBinding(context))
        val store = IndependentAnniversaryStore.store(context, binding)
        store.editDraft(IndependentAnniversaryDraft(year = 2020))
        val epoch = IndependentAnniversaryStore.currentRevision(context, binding)
        val read = async(Dispatchers.IO) {
            val session = binding.session
            RetrofitSessionApi.create().readAnniversary(session.baseUrl, session.token, session.coupleId)
        }
        var save: Deferred<Boolean>? = null
        try {
            assertTrue(getEntered.await(5, TimeUnit.SECONDS))
            save = async(Dispatchers.IO) { store.save(LocalDate.of(2026, 10, 13)) }
            assertTrue(putEntered.await(5, TimeUnit.SECONDS))
            releaseGet.countDown()
            val old = withTimeout(5_000) { read.await() }
            assertFalse("A GET captured before a PUT must not publish while that PUT is active",
                IndependentAnniversaryStore.acceptConfirmed(context, binding, old, epoch))
            assertNull(store.state.value.confirmed)
            releasePut.countDown()
            assertTrue(withTimeout(5_000) { requireNotNull(save).await() })
            assertFalse("The same old GET must remain rejected after ACK",
                IndependentAnniversaryStore.acceptConfirmed(context, binding, old, epoch))
            store.reloadConfirmed()
            assertEquals(2020, store.state.value.confirmed?.startYear)
            assertEquals("新确认", store.state.value.confirmed?.title)
        } finally {
            releaseGet.countDown(); releasePut.countDown()
            read.cancelAndJoin(); save?.cancelAndJoin()
            server.stop(0); executor.shutdownNow()
        }
    }
}
