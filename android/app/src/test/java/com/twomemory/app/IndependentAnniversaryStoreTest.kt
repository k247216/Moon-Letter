package com.twomemory.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndependentAnniversaryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 13)

    @Test fun unknownOrFutureYearAndOverlongUnicodeTitleCannotBeSubmitted() {
        bind()
        val store = IndependentAnniversaryStore.store(context, requireNotNull(SyncSession.captureBinding(context)))
        assertNotNull("Placeholder 9/24 must not invent an origin year", store.validationError(today))
        store.editDraft(IndependentAnniversaryDraft(year = 2027))
        assertNotNull(store.validationError(today))
        store.editDraft(IndependentAnniversaryDraft(year = 2020, title = "😀".repeat(81)))
        assertNotNull(store.validationError(today))
        store.editDraft(IndependentAnniversaryDraft(year = 2020, title = "😀".repeat(80)))
        assertNull(store.validationError(today))
    }

    @Test fun localDraftAndReminderTimeSurviveReopeningButNotAnotherAccount() {
        bind()
        val binding = requireNotNull(SyncSession.captureBinding(context))
        val draft = IndependentAnniversaryDraft(year = 2020, reminderTime = "18:45")
        IndependentAnniversaryStore.store(context, binding).editDraft(draft)
        assertEquals(draft, IndependentAnniversaryStore.store(context, binding).state.value.draft)
        bind()
        assertNull(IndependentAnniversaryStore.store(context, requireNotNull(SyncSession.captureBinding(context))).state.value.draft.year)
    }

    @Test fun refreshUpdatesConfirmedButNeverOverwritesUnsubmittedDraftAndUnknownYearStaysNull() = runBlocking {
        withServer(startYear = "null") { store, _, _, _ ->
            val edited = IndependentAnniversaryDraft(title = "还没提交", year = 2019, reminderTime = "20:30")
            store.editDraft(edited)
            assertTrue(store.refresh())
            assertEquals("云端纪念日", store.state.value.confirmed?.title)
            assertNull(store.state.value.confirmed?.startYear)
            assertEquals(edited, store.state.value.draft)
        }
    }

    @Test fun knownGregorianCloudDateFillsAnUntouchedEditorWithoutCreatingAnUnsubmittedDraft() = runBlocking {
        withServer { store, _, _, _ ->
            assertTrue(store.refresh())
            assertEquals(2020, store.state.value.draft.year)
            assertEquals(9, store.state.value.draft.month)
            assertEquals(24, store.state.value.draft.day)
            assertEquals("云端纪念日", store.state.value.draft.title)
            assertFalse("Viewing a confirmation is not an unsubmitted local edit",
                IndependentAnniversaryStore.preferences(context, store.binding).contains("draft"))
        }
    }

    @Test fun acknowledgedUnchangedDraftIsRetiredSoLaterPartnerConfirmationCanFillTheEditor() = runBlocking {
        withServer { store, _, _, _ ->
            store.editDraft(IndependentAnniversaryDraft(year = 2020, reminderTime = "18:45"))
            assertTrue(store.save(today))
            assertFalse("ACK must retire only the exact submitted draft",
                IndependentAnniversaryStore.preferences(context, store.binding).contains("draft"))
            assertEquals("云端纪念日", store.state.value.draft.title)
            val partner = requireNotNull(store.state.value.confirmed).copy(title = "对方更新", startYear = 2018, month = 10, day = 6)
            assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, store.binding, partner))
            store.reloadConfirmed()
            assertEquals("对方更新", store.state.value.draft.title)
            assertEquals(2018, store.state.value.draft.year)
            assertEquals(10, store.state.value.draft.month)
            assertEquals(6, store.state.value.draft.day)
            assertEquals("18:45", store.state.value.draft.reminderTime)
        }
    }

    @Test fun acknowledgementNeverRetiresAChangedDraftWrittenWhileTheRequestWasPending() = runBlocking {
        withServer(hold = true) { store, entered, release, _ ->
            store.editDraft(IndependentAnniversaryDraft(year = 2020))
            val pending = async(Dispatchers.IO) { store.save(today) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val newer = IndependentAnniversaryDraft(title = "下一次才提交", year = 2017, reminderTime = "20:15")
                store.editDraft(newer)
                release.countDown()
                assertTrue(withTimeout(5_000) { pending.await() })
                assertEquals(2020, store.state.value.confirmed?.startYear)
                assertEquals(newer, store.state.value.draft)
                assertTrue(IndependentAnniversaryStore.preferences(context, store.binding).contains("draft"))
                assertEquals(newer, IndependentAnniversaryStore.store(context, store.binding).state.value.draft)
            } finally { release.countDown(); pending.cancelAndJoin() }
        }
    }

    @Test fun successfulSavePublishesOnlyServerAcknowledgedGregorianDateAndLocalTime() = runBlocking {
        withServer { store, _, _, requests ->
            store.editDraft(IndependentAnniversaryDraft(year = 2020, reminderTime = "18:45"))
            assertTrue(store.save(today))
            assertEquals(1, requests.get())
            assertEquals(2020, store.state.value.confirmed?.startYear)
            assertEquals("18:45", store.state.value.draft.reminderTime)
        }
    }

    @Test fun failedSaveKeepsDraftWithoutInventingConfirmedState() = runBlocking {
        withServer(status = 500) { store, _, _, requests ->
            val draft = IndependentAnniversaryDraft(year = 2020)
            store.editDraft(draft)
            assertFalse(store.save(today))
            assertEquals(1, requests.get())
            assertNull(store.state.value.confirmed)
            assertEquals(draft, store.state.value.draft)
            assertNotNull(store.state.value.error)
        }
    }

    @Test fun lateRefreshAfterAToBToAHasNoPublicationOrPersistedSideEffect() = runBlocking {
        withServer(hold = true) { store, entered, release, _ ->
            val a = store.binding.session
            val pending = async(Dispatchers.IO) { store.refresh() }
            try {
                assertTrue("Real HTTP read must enter", entered.await(5, TimeUnit.SECONDS))
                bind()
                SyncSession.save(context, a.token, a.coupleId, a.userId, a.baseUrl)
                release.countDown()
                withTimeout(5_000) { pending.await() }
                assertNull(store.state.value.confirmed)
                val current = IndependentAnniversaryStore.store(context, requireNotNull(SyncSession.captureBinding(context)))
                assertNull(current.state.value.confirmed)
            } finally { release.countDown(); pending.cancelAndJoin() }
        }
    }

    @Test fun cancellationEscapesRefreshAndNeverPublishesTheReleasedResponse() = runBlocking {
        withServer(hold = true) { store, entered, release, _ ->
            val pending = async(Dispatchers.IO) { store.refresh() }
            try {
                assertTrue("Real HTTP read must enter", entered.await(5, TimeUnit.SECONDS))
                pending.cancel(); release.countDown()
                try { pending.await(); fail("Cancellation must escape") } catch (_: CancellationException) { }
                assertNull(store.state.value.confirmed)
            } finally { release.countDown(); pending.cancelAndJoin() }
        }
    }

    private fun bind(base: String = "http://127.0.0.1:9") {
        SyncSession.save(context, "synthetic-anniversary", UUID.randomUUID(), UUID.randomUUID(), base)
    }

    private suspend fun withServer(status: Int = 200, startYear: String = "2020", hold: Boolean = false,
        block: suspend (IndependentAnniversaryStore, CountDownLatch, CountDownLatch, AtomicInteger) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        val entered = CountDownLatch(1); val release = CountDownLatch(if (hold) 1 else 0)
        val requests = AtomicInteger()
        bind("http://127.0.0.1:${server.address.port}")
        val binding = requireNotNull(SyncSession.captureBinding(context))
        server.createContext("/api/v1/couple/${binding.session.coupleId}/anniversary") { exchange ->
            try {
                requests.incrementAndGet()
                if (exchange.requestMethod == "PUT") {
                    val body = exchange.requestBody.bufferedReader().use { it.readText() }
                    check(body.contains("GREGORIAN") && body.contains("2020"))
                    check(!body.contains("18:45")) { "Local reminder time must not enter the shared protocol" }
                }
                entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
                val data = """{"id":"${UUID.randomUUID()}","coupleId":"${binding.session.coupleId}","title":"云端纪念日","calendarType":"GREGORIAN","month":9,"day":24,"lunarLeapMonth":false,"startYear":$startYear,"repeatsYearly":true}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, data.size.toLong())
                exchange.responseBody.use { it.write(data) }
            } catch (_: java.io.IOException) { } finally { exchange.close() }
        }
        server.start()
        try { block(IndependentAnniversaryStore.store(context, binding), entered, release, requests) }
        finally { release.countDown(); server.stop(0); executor.shutdownNow() }
    }
}
