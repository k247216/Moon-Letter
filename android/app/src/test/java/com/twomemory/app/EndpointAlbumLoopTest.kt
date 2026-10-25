package com.twomemory.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.*
import com.twomemory.network.*
import com.twomemory.model.EntryMode
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/** Uses a real authenticated backend. The test transport adapter avoids public DNS/TLS;
 * it does not pretend to verify the public certificate or cross-province reachability. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EndpointAlbumLoopTest {
    private lateinit var server: RealServerHarness.Running
    @Before fun start() { server = RealServerHarness.start(18085, "moon_letter_endpoint_loop_test", "endpoint-fixture-only", "endpoint-loop.log") }
    @After fun stop() { RealServerHarness.stop(server) }
    @Test fun waitingPhotoKeepsOriginalAndUploadsThroughNewTransportToPartner() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val real = RetrofitSessionApi.create()
        val first = real.bootstrap(server.baseUrl, "endpoint-fixture-only", "甲")
        val invite = real.pairingToken(server.baseUrl, first.token, first.coupleId)
        val second = real.pair(server.baseUrl, invite.pairingToken, "乙")
        SyncSession.save(context, first.token, first.coupleId, first.userId, ServerEndpointMigration.USB_ORIGIN)
        val before = SyncSession.load(context)!!
        val db = AppDatabase.build(context, before.databaseName)
        val partnerDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val image = java.nio.file.Files.createTempFile("endpoint-original-", ".png").toFile()
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(27, 19, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", image)
        try {
            val repository = AlbumRepository(db, context, AlbumScope(before.storageOrigin, before.coupleId.toString(), before.userId.toString()))
            val album = repository.createAlbum("待上传的照片", "旅行")
            assertEquals(1, repository.importPhotos(album, listOf(Uri.fromFile(image))).importedCount)
            val original = db.albumDao().allAlbums().single().photos.single()
            val originalBytes = java.io.File(original.localPath).readBytes()
            val scope = "${before.storageOrigin}|${before.coupleId}|${before.userId}"
            val draft = UUID.randomUUID().toString()
            DraftLibrary(context, scope).save(draft, EntryMode.PERSONAL, DraftStore.Draft("保留草稿", "正文"))
            ReadingProgressStore(context, scope).markRead("seen-record")
            val adapter = object : SessionApi by real {
                override suspend fun readSpace(baseUrl: String, bearer: String, coupleId: UUID): SpaceViewDto {
                    assertEquals(ServerEndpointMigration.PUBLIC_ORIGIN, baseUrl)
                    return real.readSpace(server.baseUrl, bearer, coupleId)
                }
            }
            ServerEndpointMigration.migrate(context, ServerEndpointMigration.PUBLIC_ORIGIN, adapter)
            val after = SyncSession.load(context)!!
            assertEquals(before.databaseName, after.databaseName)
            assertTrue(AlbumCloudSync.hasPending(db, after))
            assertEquals(original.localPath, db.albumDao().findPhoto(original.id)!!.localPath)
            assertArrayEquals(originalBytes, java.io.File(original.localPath).readBytes())
            val newScope = "${after.storageOrigin}|${after.coupleId}|${after.userId}"
            assertEquals("保留草稿", DraftLibrary(context, newScope).load(draft)!!.draft.title)
            assertTrue(ReadingProgressStore(context, newScope).readIds().contains("seen-record"))
            // Point only this isolated fixture's transport at its local real backend.
            // The production migration has already preserved USB-origin storage.
            assertTrue(SyncSession.sessionPrefs(context).edit().putString("baseUrl", server.baseUrl).commit())
            val sender = SyncSession.load(context)!!
            AlbumCloudSync.sync(context, db, sender, bindingValid = { true })
            val partner = SyncSession.Session(second.deviceToken, first.coupleId, second.userId, server.baseUrl)
            AlbumCloudSync.sync(context, partnerDb, partner, bindingValid = { true })
            val seen = partnerDb.albumDao().allAlbums().single()
            assertEquals(album, seen.album.id)
            assertEquals(original.id, seen.photos.single().id)
            assertArrayEquals(originalBytes, RetrofitMediaApi.create().download(server.baseUrl, second.deviceToken,
                UUID.fromString(seen.photos.single().assetId)))
            assertFalse(AlbumCloudSync.hasPending(db, sender))
        } finally { partnerDb.close(); image.delete() }
    }
}
