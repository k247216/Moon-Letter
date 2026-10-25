package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.twomemory.network.MemberViewDto
import com.twomemory.network.ProfileViewDto
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.network.SpaceViewDto
import java.io.ByteArrayOutputStream
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileRefreshSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val couple = UUID.randomUUID()
    private val own = UUID.randomUUID()
    private val partner = UUID.randomUUID()
    private val asset = UUID.randomUUID()
    private val sessionPrefs get() = SyncSession.sessionPrefs(context)
    private val profilePrefs get() = context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE)

    @Test fun lateNamesCannotOverwriteNewAccountCache() = runBlocking { lateNames(roundTrip = false) }
    @Test fun lateNamesCannotOverwriteAfterAToBToA() = runBlocking { lateNames(roundTrip = true) }
    @Test fun lateAvatarCannotOverwriteNewAccountCache() = runBlocking { lateAvatar(roundTrip = false) }
    @Test fun lateAvatarCannotOverwriteAfterAToBToA() = runBlocking { lateAvatar(roundTrip = true) }

    @Test fun cancelledNamesRefreshPropagatesAndRetainsConfirmedCache() = runBlocking {
        heldServer(spaceJson().toByteArray(), "application/json") { _, gate ->
            seedCache()
            val propagated = AtomicBoolean()
            val refresh = launch(Dispatchers.IO) {
                try { SyncSession.refreshNames(context) }
                catch (cancelled: CancellationException) { propagated.set(true); throw cancelled }
            }
            try {
                assertTrue("Profile request must reach real server", gate.entered.await(5, TimeUnit.SECONDS))
                refresh.cancel()
                gate.release.countDown()
                withTimeout(5_000) { refresh.join() }
                assertCacheIntact()
                assertTrue("refreshNames must not swallow CancellationException", propagated.get())
            } finally { refresh.cancel(); gate.release.countDown(); refresh.join() }
        }
    }

    @Test fun cancelledAvatarDownloadRetainsConfirmedCache() = runBlocking {
        heldServer(png(), "image/png") { session, gate ->
            seedCache()
            val propagated = AtomicBoolean()
            val refresh = launch(Dispatchers.IO) {
                try { ProfileMediaSync.refresh(context, session, space(asset)) }
                catch (cancelled: CancellationException) { propagated.set(true); throw cancelled }
            }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                refresh.cancel()
                gate.release.countDown()
                withTimeout(5_000) { refresh.join() }
                assertCacheIntact()
                assertTrue(propagated.get())
            } finally { refresh.cancel(); gate.release.countDown(); refresh.join() }
        }
    }

    @Test fun obsoleteAvatarlessProfileCannotClearNewAccountAvatar() = runBlocking {
        heldServer(spaceJson().toByteArray(), "application/json") { session, gate ->
            gate.release.countDown()
            val snapshot = RetrofitSessionApi.create().readSpace(session.baseUrl, session.token, session.coupleId)
            switchBinding(session, roundTrip = false)
            seedCache()
            ProfileMediaSync.refresh(context, session, snapshot)
            assertCacheIntact()
        }
    }

    @Test fun cancelledAvatarlessProfileCannotClearCache() = runBlocking {
        heldServer(spaceJson().toByteArray(), "application/json") { session, gate ->
            gate.release.countDown()
            val snapshot = RetrofitSessionApi.create().readSpace(session.baseUrl, session.token, session.coupleId)
            seedCache()
            val propagated = AtomicBoolean()
            val refresh = launch(Dispatchers.IO) {
                currentCoroutineContext()[Job]!!.cancel(CancellationException("screen left"))
                try { ProfileMediaSync.refresh(context, session, snapshot) }
                catch (cancelled: CancellationException) { propagated.set(true); throw cancelled }
            }
            refresh.join()
            assertCacheIntact()
            assertTrue("The no-avatar branch must also check cancellation", propagated.get())
        }
    }

    @Test fun currentProfileResponseUpdatesNamesNormally() = runBlocking {
        heldServer(spaceJson().toByteArray(), "application/json") { _, gate ->
            seedCache()
            gate.release.countDown()
            SyncSession.refreshNames(context)
            assertEquals("Remote own", sessionPrefs.getString("ownName", null))
            assertEquals("Remote partner", sessionPrefs.getString("partnerName", null))
        }
    }

    @Test fun currentAvatarResponsePublishesDecodableImageNormally() = runBlocking {
        heldServer(png(), "image/png") { session, gate ->
            seedCache()
            gate.release.countDown()
            ProfileMediaSync.refresh(context, session, space(asset))
            assertEquals("$couple:$asset", profilePrefs.getString("partnerAvatarAsset", null))
            val uri = Uri.parse(requireNotNull(profilePrefs.getString("partnerAvatar", null)))
            context.contentResolver.openInputStream(uri).use { input ->
                val decoded = requireNotNull(BitmapFactory.decodeStream(input))
                try { assertEquals(4, decoded.width); assertEquals(3, decoded.height) }
                finally { decoded.recycle() }
            }
        }
    }

    private suspend fun lateNames(roundTrip: Boolean) {
        heldServer(spaceJson().toByteArray(), "application/json") { session, gate ->
            val refresh = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) { SyncSession.refreshNames(context) }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                switchBinding(session, roundTrip)
                seedCache()
                gate.release.countDown()
                withTimeout(5_000) { refresh.join() }
                assertCacheIntact()
            } finally { refresh.cancel(); gate.release.countDown(); refresh.join() }
        }
    }

    private suspend fun lateAvatar(roundTrip: Boolean) {
        heldServer(png(), "image/png") { session, gate ->
            val refresh = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
                ProfileMediaSync.refresh(context, session, space(asset))
            }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                switchBinding(session, roundTrip)
                seedCache()
                gate.release.countDown()
                withTimeout(5_000) { refresh.join() }
                assertCacheIntact()
            } finally { refresh.cancel(); gate.release.countDown(); refresh.join() }
        }
    }

    private fun switchBinding(before: SyncSession.Session, roundTrip: Boolean) {
        SyncSession.save(context, "other-test-token", UUID.randomUUID(), UUID.randomUUID(), before.baseUrl)
        if (roundTrip) {
            SyncSession.save(context, before.token, before.coupleId, before.userId, before.baseUrl)
            assertEquals("ABA must restore identical Session values", before, SyncSession.load(context))
        }
    }

    private fun seedCache() {
        assertTrue(sessionPrefs.edit().putString("ownName", "Confirmed own").putString("partnerName", "Confirmed partner").commit())
        assertTrue(profilePrefs.edit().putString("partnerAvatar", "file:///confirmed-avatar.png")
            .putString("partnerAvatarAsset", "confirmed-asset").commit())
    }

    private fun assertCacheIntact() {
        assertEquals("Confirmed own", sessionPrefs.getString("ownName", null))
        assertEquals("Confirmed partner", sessionPrefs.getString("partnerName", null))
        assertEquals("file:///confirmed-avatar.png", profilePrefs.getString("partnerAvatar", null))
        assertEquals("confirmed-asset", profilePrefs.getString("partnerAvatarAsset", null))
    }

    private fun space(avatar: UUID?) = SpaceViewDto(couple, listOf(
        MemberViewDto(own, ProfileViewDto("Remote own")),
        MemberViewDto(partner, ProfileViewDto("Remote partner", avatar)),
    ))
    private fun spaceJson() = """{"id":"$couple","members":[{"userId":"$own","profile":{"displayName":"Remote own"}},{"userId":"$partner","profile":{"displayName":"Remote partner","avatarAssetId":null}}]}"""

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
        } } finally { bitmap.recycle() }
    }

    private class Gate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    private suspend fun heldServer(bytes: ByteArray, contentType: String,
        block: suspend (SyncSession.Session, Gate) -> Unit) {
        val gate = Gate()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/") { exchange: HttpExchange ->
            try {
                gate.entered.countDown()
                check(gate.release.await(10, TimeUnit.SECONDS)) { "Test did not release response" }
                exchange.responseHeaders.add("Content-Type", contentType)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) { /* Client cancellation closes its socket. */ }
            finally { exchange.close() }
        }
        server.start()
        try {
            SyncSession.save(context, "profile-test-token", couple, own, "http://127.0.0.1:${server.address.port}")
            block(requireNotNull(SyncSession.load(context)), gate)
        } finally { gate.release.countDown(); server.stop(0); executor.shutdownNow() }
    }
}
