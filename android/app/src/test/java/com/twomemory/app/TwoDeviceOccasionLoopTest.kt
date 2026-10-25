package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.network.RetrofitMediaApi
import com.twomemory.network.OccasionApi
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.UUID

/** Actual Android client -> authenticated HTTP -> Spring/PostgreSQL/media -> partner cache. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TwoDeviceOccasionLoopTest {
    private lateinit var running: RealServerHarness.Running
    @Before fun start() { running = RealServerHarness.start(18086, "moon_letter_occasion_loop_test", "occasion-test-only", "occasion-loop-server.log") }
    @After fun stop() { if (::running.isInitialized) RealServerHarness.stop(running) }

    @Test fun twoBirthdaysAndPhotoSurpriseReachPartnerAndWithdrawalConverges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = RetrofitSessionApi.create()
        val a = api.bootstrap(running.baseUrl, "occasion-test-only", "小满")
        val invite = api.pairingToken(running.baseUrl, a.token, a.coupleId)
        val b = api.pair(running.baseUrl, invite.pairingToken, "阿屿")
        SyncSession.save(context, a.token, a.coupleId, a.userId, running.baseUrl)
        val today = LocalDate.now().toString()
        val base = OccasionCard(UUID.randomUUID().toString(), a.coupleId.toString(), a.userId.toString(), "BIRTHDAY", "小满的生日", "", "2020-05-09", true, null, null, "")
        OccasionCloud.save(context, base)
        OccasionCloud.save(context, base.copy(id = UUID.randomUUID().toString(), title = "阿屿的生日", date = "2020-04-23"))
        val file = java.nio.file.Files.createTempFile("occasion-photo-", ".png").toFile()
        try {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", file)
            val asset = OccasionCloud.uploadPhoto(context, file.absolutePath)
            val surprise = base.copy(id = UUID.randomUUID().toString(), kind = "SURPRISE", title = "今天想你", body = "给你的小信", date = today,
                repeatsYearly = false, recipientId = b.userId.toString(), photoAssetId = asset)
            OccasionCloud.save(context, surprise)
            SyncSession.save(context, b.deviceToken, a.coupleId, b.userId, running.baseUrl)
            OccasionCloud.refresh(context)
            val cache = OccasionCloud.store(context, SyncSession.load(context)!!)
            assertEquals(3, cache.cards().size)
            assertEquals("今天想你", cache.due(b.userId.toString(), LocalDate.now()).single().title)
            assertArrayEquals(file.readBytes(), RetrofitMediaApi.create().download(running.baseUrl, b.deviceToken, UUID.fromString(asset)))
            // Only author withdraws; partner then loses the card from confirmed snapshots.
            OccasionApi(running.baseUrl, a.token, a.coupleId).delete(surprise.id)
            OccasionCloud.refresh(context)
            assertEquals(2, cache.cards().size)
            assertTrue(cache.due(b.userId.toString(), LocalDate.now()).isEmpty())
        } finally { file.delete() }
    }
    @Test fun rejectedSaveDoesNotInventDeliveryOrReplaceConfirmedCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a = RetrofitSessionApi.create().bootstrap(running.baseUrl, "occasion-test-only", "小满")
        SyncSession.save(context, a.token, a.coupleId, a.userId, running.baseUrl)
        val card = OccasionCard(UUID.randomUUID().toString(), a.coupleId.toString(), a.userId.toString(), "BIRTHDAY", "生日", "", "2020-05-09", true, null, null, "")
        OccasionCloud.save(context, card)
        val rejected = runCatching { OccasionCloud.save(context, card.copy(title = "")) }
        assertTrue(rejected.isFailure)
        assertEquals("生日", OccasionCloud.store(context, SyncSession.load(context)!!).cards().single().title)
        RealServerHarness.stop(running)
        OccasionCloud.refresh(context)
        assertEquals("生日", OccasionCloud.store(context, SyncSession.load(context)!!).cards().single().title)
    }
}
