package com.twomemory.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.*
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.network.SharedAlbumApi
import com.twomemory.network.MediaApi
import com.twomemory.network.RetrofitMediaApi
import java.io.IOException
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Two real Room stores -> authenticated HTTP -> Spring -> PostgreSQL -> original bytes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TwoDeviceAlbumLoopTest {
    companion object {
        private const val secret = "album-integration-only-secret"
    }
    private lateinit var running: RealServerHarness.Running
    @Before fun start() {
        running = RealServerHarness.start(18084, "moon_letter_album_loop_test", secret, "album-loop-server.log")
    }
    @After fun stop() { if (::running.isInitialized) RealServerHarness.stop(running) }

    @Test fun preCloudAlbumWithOriginalPhotoBindsAndReachesPartnerWithoutRecreatingIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val image = Files.createTempFile("legacy-album-loop-", ".png").toFile()
        ImageIO.write(BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB), "png", image)
        try {
            // V10 rows have no origin, couple or uploader; preserve those real defaults.
            val legacy = AlbumRepository(dbA, context)
            val id = legacy.createAlbum("升级前的旅行", "旅行")
            assertEquals(1, legacy.importPhotos(id, listOf(Uri.fromFile(image))).importedCount)
            val original = dbA.albumDao().allAlbums().single().photos.single()
            assertNull(dbA.albumDao().findAlbum(id)!!.serverUrl)
            assertNull(original.addedBy)
            val sessions = RetrofitSessionApi.create()
            val first = sessions.bootstrap(running.baseUrl, secret, "小满")
            val invite = sessions.pairingToken(running.baseUrl, first.token, first.coupleId)
            val second = sessions.pair(running.baseUrl, invite.pairingToken, "阿屿")
            val a = SyncSession.Session(first.token, first.coupleId, first.userId, running.baseUrl)
            val b = SyncSession.Session(second.deviceToken, first.coupleId, second.userId, running.baseUrl)
            val realMedia = RetrofitMediaApi.create()
            val classifiedMedia = object : MediaApi by realMedia {
                override suspend fun createUpload(baseUrl: String, bearer: String,
                    command: com.twomemory.network.MediaCreateCommand): com.twomemory.network.UploadTicketDto {
                    assertEquals("ALBUM", command.purpose)
                    return realMedia.createUpload(baseUrl, bearer, command).also { ticket ->
                        assertTrue(ticket.objectKey.startsWith("couples/${a.coupleId}/albums/photos/"))
                        assertTrue(ticket.objectKey.endsWith(".png"))
                    }
                }
            }
            AlbumCloudSync.sync(context, dbA, a, classifiedMedia, bindingValid = { true })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            val seen = dbB.albumDao().allAlbums().single()
            assertEquals(id, seen.album.id)
            assertEquals("升级前的旅行", seen.album.name)
            assertEquals(original.id, seen.photos.single().id)
            assertFalse("Metadata sync must not eagerly fetch the partner original", File(seen.photos.single().localPath).isFile)
            val preview = AlbumImageLoader.load(context, seen.photos.single(), false, b, dbB)
            assertTrue(preview.isFile)
            assertFalse(File(seen.photos.single().localPath).isFile)
            // Same save/restore contract used while the Android 8–9 document picker is open.
            val saverField = Class.forName("com.twomemory.app.IndependentAlbumScreenKt").getDeclaredField("ExportPhotoSaver")
            saverField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val saver = saverField.get(null) as androidx.compose.runtime.saveable.Saver<AlbumPhotoEntity?, Any>
            val scope = object : androidx.compose.runtime.saveable.SaverScope { override fun canBeSaved(value: Any) = true }
            val serialized = with(saver) { scope.save(seen.photos.single()) }
            val restored = requireNotNull(saver.restore(requireNotNull(serialized)))
            AlbumImageLoader.load(context, restored, true, b, dbB)
            assertArrayEquals(image.readBytes(), File(seen.photos.single().localPath).readBytes())
            assertEquals(original.localPath, dbA.albumDao().findPhoto(original.id)!!.localPath)
            assertFalse(AlbumCloudSync.hasPending(dbA, a))
            assertFalse(AlbumCloudSync.hasPending(dbB, b))
        } finally { image.delete(); dbA.close(); dbB.close() }
    }
    @Test fun independentAlbumOriginalCoverAndDeletionConvergeWithoutDiaryRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val image = Files.createTempFile("album-loop-", ".png").toFile()
        ImageIO.write(BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "png", image)
        try {
            val sessionApi = RetrofitSessionApi.create()
            val first = sessionApi.bootstrap(running.baseUrl, secret, "小满")
            val invite = sessionApi.pairingToken(running.baseUrl, first.token, first.coupleId)
            val second = sessionApi.pair(running.baseUrl, invite.pairingToken, "阿屿")
            val a = SyncSession.Session(first.token, first.coupleId, first.userId, running.baseUrl)
            val b = SyncSession.Session(second.deviceToken, first.coupleId, second.userId, running.baseUrl)
            val repoA = AlbumRepository(dbA, context, AlbumScope(a.baseUrl, a.coupleId.toString(), a.userId.toString()))
            val repoB = AlbumRepository(dbB, context, AlbumScope(b.baseUrl, b.coupleId.toString(), b.userId.toString()))
            val id = repoA.createAlbum("湖边周末", "旅行")
            val imported = repoA.importPhotos(id, listOf(Uri.fromFile(image)))
            assertEquals(imported.failures.toString(), 1, imported.importedCount)
            val photoA = repoA.observeAlbums().first().single().photos.single()
            AlbumCloudSync.sync(context, dbA, a, bindingValid = { true })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            val received = repoB.observeAlbums().first().single()
            assertEquals("湖边周末", received.album.name)
            assertEquals(photoA.id, received.album.coverPhotoId)
            assertFalse(File(received.photos.single().localPath).isFile)
            AlbumImageLoader.load(context, received.photos.single(), true, b, dbB)
            assertArrayEquals(image.readBytes(), File(received.photos.single().localPath).readBytes())
            assertNotEquals(photoA.localPath, received.photos.single().localPath)
            AlbumCloudSync.sync(context, dbA, a, bindingValid = { true })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            assertEquals(1, repoB.observeAlbums().first().single().photos.size)
            // Cache eviction must not make a cloud photo permanently unplayable/unreadable.
            File(received.photos.single().localPath).delete()
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            AlbumImageLoader.load(context, received.photos.single(), true, b, dbB)
            assertArrayEquals(image.readBytes(), File(repoB.observeAlbums().first().single().photos.single().localPath).readBytes())
            repoB.setCover(id, photoA.id) // Offline intent loses its target to a remote tombstone.
            repoA.deletePhoto(photoA.id)
            AlbumCloudSync.sync(context, dbA, a, bindingValid = { true })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            assertTrue(repoB.observeAlbums().first().single().photos.isEmpty())
            assertFalse(dbB.albumDao().findAlbum(id)!!.coverDirty)
            assertTrue(SharedAlbumApi(a.baseUrl, a.token, a.coupleId).read().single().photos.single().deleted)
            repoA.deleteAlbum(id)
            AlbumCloudSync.sync(context, dbA, a, bindingValid = { true })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            assertTrue(repoB.observeAlbums().first().isEmpty())
            assertEquals(0, dbA.entryDao().observeTimeline().first().size)
        } finally { image.delete(); dbA.close(); dbB.close() }
    }

    @Test fun interruptedUploadSurvivesReopeningAndDoesNotBlockPartnerPhotos() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "album-retry-${UUID.randomUUID()}.db"
        var dbA = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val image = Files.createTempFile("album-retry-", ".png").toFile()
        ImageIO.write(BufferedImage(16, 18, BufferedImage.TYPE_INT_RGB), "png", image)
        try {
            val sessions = RetrofitSessionApi.create()
            val first = sessions.bootstrap(running.baseUrl, secret, "小满")
            val invite = sessions.pairingToken(running.baseUrl, first.token, first.coupleId)
            val second = sessions.pair(running.baseUrl, invite.pairingToken, "阿屿")
            val a = SyncSession.Session(first.token, first.coupleId, first.userId, running.baseUrl)
            val b = SyncSession.Session(second.deviceToken, first.coupleId, second.userId, running.baseUrl)
            fun repo(db: AppDatabase, session: SyncSession.Session) = AlbumRepository(db, context,
                AlbumScope(session.baseUrl, session.coupleId.toString(), session.userId.toString()))
            val ownId = repo(dbA, a).createAlbum("待重试的相册", "日常")
            assertEquals(1, repo(dbA, a).importPhotos(ownId, listOf(Uri.fromFile(image))).importedCount)
            val partnerId = repo(dbB, b).createAlbum("对方的新相册", "旅行")
            assertEquals(1, repo(dbB, b).importPhotos(partnerId, listOf(Uri.fromFile(image))).importedCount)
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            val transportFailure = object : MediaApi by RetrofitMediaApi.create() {
                override suspend fun uploadFileData(baseUrl: String, bearer: String, assetId: UUID, file: File, mimeType: String) {
                    throw IOException("test-only connection interrupted")
                }
            }
            AlbumCloudSync.sync(context, dbA, a, transportFailure, bindingValid = { true })
            assertTrue(AlbumCloudSync.hasPending(dbA, a))
            val incoming = repo(dbA, a).observeAlbums().first().first { it.album.id == partnerId }
            AlbumImageLoader.load(context, incoming.photos.single(), true, a, dbA)
            assertArrayEquals(image.readBytes(), File(incoming.photos.single().localPath).readBytes())
            dbA.close()
            dbA = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
            assertTrue(AlbumCloudSync.hasPending(dbA, a))
            AlbumCloudSync.sync(context, dbA, a, bindingValid = { true })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            assertFalse(AlbumCloudSync.hasPending(dbA, a))
            val received = repo(dbB, b).observeAlbums().first().first { it.album.id == ownId }
            assertEquals(1, received.photos.size)
            AlbumImageLoader.load(context, received.photos.single(), true, b, dbB)
            assertArrayEquals(image.readBytes(), File(received.photos.single().localPath).readBytes())
        } finally { image.delete(); dbA.close(); dbB.close(); context.deleteDatabase(dbName) }
    }

    @Test fun eightFailedTransfersCannotStarveTheNinthHealthyPhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val image = Files.createTempFile("album-budget-", ".png").toFile()
        ImageIO.write(BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB), "png", image)
        try {
            val sessions = RetrofitSessionApi.create()
            val first = sessions.bootstrap(running.baseUrl, secret, "小满")
            val invite = sessions.pairingToken(running.baseUrl, first.token, first.coupleId)
            val second = sessions.pair(running.baseUrl, invite.pairingToken, "阿屿")
            val a = SyncSession.Session(first.token, first.coupleId, first.userId, running.baseUrl)
            val b = SyncSession.Session(second.deviceToken, first.coupleId, second.userId, running.baseUrl)
            val repo = AlbumRepository(dbA, context, AlbumScope(a.baseUrl, a.coupleId.toString(), a.userId.toString()))
            val id = repo.createAlbum("公平重试", "日常")
            repeat(9) { assertEquals(1, repo.importPhotos(id, listOf(Uri.fromFile(image))).importedCount) }
            val failures = dbA.albumDao().allAlbums().single { it.album.id == id }.photos.take(8).map { it.id }.toSet()
            val real = RetrofitMediaApi.create()
            val interrupted = object : MediaApi by real {
                override suspend fun createUpload(baseUrl: String, bearer: String, command: com.twomemory.network.MediaCreateCommand): com.twomemory.network.UploadTicketDto {
                    if (command.operationId.toString() in failures) throw IOException("persistent test failure")
                    return real.createUpload(baseUrl, bearer, command)
                }
            }
            AlbumCloudSync.sync(context, dbA, a, interrupted, bindingValid = { true })
            assertEquals(1, dbA.albumDao().allAlbums().single { it.album.id == id }.photos.count { it.cloudKnown })
            AlbumCloudSync.sync(context, dbA, a, bindingValid = { true })
            var attempts = 0
            val interruptedDownload = object : MediaApi by real {
                override suspend fun downloadToFile(baseUrl: String, bearer: String, assetId: UUID, destination: File, maxBytes: Long) {
                    if (++attempts <= 8) throw IOException("persistent test download failure")
                    real.downloadToFile(baseUrl, bearer, assetId, destination, maxBytes)
                }
            }
            var continuations = 0
            AlbumCloudSync.syncAndContinue(context, dbB, b, interruptedDownload, bindingValid = { true }) { continuations++ }
            assertEquals(0, attempts)
            val pendingPhotos = dbB.albumDao().allAlbums().single { it.album.id == id }.photos
            for (photo in pendingPhotos) try { AlbumImageLoader.load(context, photo, true, b, dbB, interruptedDownload) }
                catch (_: IOException) { }
            assertEquals(9, attempts)
            assertEquals(0, continuations)
            assertEquals(1, dbB.albumDao().allAlbums().single { it.album.id == id }.photos.count { File(it.localPath).isFile })
            AlbumCloudSync.sync(context, dbB, b, bindingValid = { true })
            assertFalse(AlbumCloudSync.hasPending(dbB, b))
        } finally { image.delete(); dbA.close(); dbB.close() }
    }
}
