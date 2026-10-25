package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import com.twomemory.database.AlbumCategoryEntity
import com.twomemory.database.AlbumEntity
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AppDatabase
import com.twomemory.network.CloudAlbum
import com.twomemory.network.CloudAlbumPhoto
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumCloudSyncEfficiencyTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var server: HttpServer
    private lateinit var session: SyncSession.Session
    private lateinit var original: File
    private val albumId = UUID.randomUUID()
    private val photoId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()
    private val otherPhotoId = UUID.randomUUID()
    private val otherAssetId = UUID.randomUUID()
    private val remoteOwner = UUID.randomUUID()
    private val snapshot = AtomicReference<List<CloudAlbum>>(emptyList())
    private val requests = CopyOnWriteArrayList<String>()
    private var mutation: (String, String) -> Int = { _, _ -> 500 }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        original = File(context.cacheDir, "album-original-${UUID.randomUUID()}.image")
        original.writeBytes(byteArrayOf(1, 2, 3))
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val space = UUID.randomUUID()
        server.createContext("/api/v1/couple/$space/albums") { exchange ->
            val method = exchange.requestMethod
            val path = exchange.requestURI.path
            exchange.requestBody.use { it.readBytes() }
            requests.add("$method $path")
            val status = if (method == "GET") 200 else mutation(method, path)
            val bytes = (if (method == "GET") snapshotJson() else "{}").toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        session = SyncSession.Session("synthetic-test-token", space, UUID.randomUUID(),
            "http://127.0.0.1:${server.address.port}")
        snapshot.set(listOf(remote()))
    }

    @After
    fun tearDown() {
        server.stop(0)
        database.close()
        original.delete()
    }

    @Test
    fun aReadOnlyCycleFetchesTheFullSnapshotOnce() = runBlocking {
        sync()

        assertEquals(listOf("GET"), methods())
        assertEquals("Cloud album", database.albumDao().findAlbum(albumId.toString())!!.name)
        assertEquals(assetId.toString(), database.albumDao().findPhoto(photoId.toString())!!.assetId)
    }

    @Test
    fun repeatedCompleteSyncDoesNotRewriteIdenticalRowsOrLoseTheOriginalPath() = runBlocking {
        sync()
        val dao = database.albumDao()
        val photo = dao.findPhoto(photoId.toString())!!
        dao.upsertPhoto(photo.copy(localPath = original.absolutePath, importedAtEpochMillis = 777))
        val before = dao.allAlbums()
        auditWrites()
        requests.clear()

        sync()

        assertEquals("Identical merged rows must not invalidate album observers", 0L, writeCount())
        assertEquals(before, dao.allAlbums())
        assertEquals(original.absolutePath, dao.findPhoto(photoId.toString())!!.localPath)
        assertEquals(777L, dao.findPhoto(photoId.toString())!!.importedAtEpochMillis)
        assertArrayEquals(byteArrayOf(1, 2, 3), original.readBytes())
    }

    @Test
    fun aLaterReadOnlyCycleStillLearnsRemoteAlbumAndPhotoTombstones() = runBlocking {
        sync()
        snapshot.set(listOf(remote().copy(deleted = true,
            photos = listOf(remote().photos.single().copy(deleted = true)))))
        requests.clear()

        sync()

        val dao = database.albumDao()
        assertTrue(dao.findAlbum(albumId.toString())!!.deleted)
        assertFalse(dao.findAlbum(albumId.toString())!!.cloudKnown)
        assertTrue(dao.findPhoto(photoId.toString())!!.deleted)
        assertFalse(dao.findPhoto(photoId.toString())!!.cloudKnown)
        assertEquals(listOf("GET"), methods())
        auditWrites()
        sync()
        assertEquals("Repeated tombstones must also merge without writes", 0L, writeCount())
    }

    @Test
    fun aFailedPhotoDeleteKeepsLocalDeletionUntilTheRemoteTombstoneArrives() = runBlocking {
        snapshot.set(listOf(remote().copy(photos = listOf(remote().photos.single().copy(addedBy = session.userId)))))
        sync()
        database.albumDao().deletePhoto(photoId.toString())
        requests.clear()

        sync()

        val pending = database.albumDao().findPhoto(photoId.toString())!!
        assertTrue(pending.deleted)
        assertTrue(pending.cloudKnown)
        assertEquals(listOf("GET", "DELETE", "GET"), methods())
        snapshot.set(listOf(snapshot.get().single().copy(photos = listOf(remote().photos.single().copy(deleted = true)))))
        requests.clear()
        sync()
        assertFalse(database.albumDao().findPhoto(photoId.toString())!!.cloudKnown)
        assertEquals(listOf("GET"), methods())
    }

    @Test
    fun anUnsendableLocalCoverIntentSurvivesWithoutASecondSnapshotRead() = runBlocking {
        sync()
        val dao = database.albumDao()
        val missingPath = File(context.cacheDir, "missing-${otherPhotoId}.image").absolutePath
        dao.upsertPhoto(AlbumPhotoEntity(otherPhotoId.toString(), albumId.toString(), missingPath,
            "image/jpeg", 10, 20, 2, 888, addedBy = session.userId.toString()))
        dao.setCover(albumId.toString(), otherPhotoId.toString())
        // Missing local bytes prevent upload; the user's cover selection is still owed.
        requests.clear()

        sync()

        val album = dao.findAlbum(albumId.toString())!!
        assertEquals(otherPhotoId.toString(), album.coverPhotoId)
        assertTrue(album.coverDirty)
        assertEquals(listOf("GET"), methods())
    }

    @Test
    fun aRemoteTombstoneInvalidatesAPendingCoverSelection() = runBlocking {
        val second = CloudAlbumPhoto(otherPhotoId, otherAssetId, remoteOwner, 2, "image/jpeg", 10, 20, false)
        snapshot.set(listOf(remote().copy(photos = remote().photos + second)))
        sync()
        database.albumDao().setCover(albumId.toString(), otherPhotoId.toString())
        snapshot.set(listOf(remote().copy(photos = remote().photos + second.copy(deleted = true))))
        requests.clear()

        sync()

        val album = database.albumDao().findAlbum(albumId.toString())!!
        assertEquals(photoId.toString(), album.coverPhotoId)
        assertFalse(album.coverDirty)
        assertTrue(database.albumDao().findPhoto(otherPhotoId.toString())!!.deleted)
        assertTrue("A deleted cover must not be sent back to the server", methods().all { it == "GET" })
    }

    @Test
    fun aLiveSnapshotCannotResurrectAnAlbumDeletedLocally() = runBlocking {
        snapshot.set(listOf(remote().copy(createdBy = session.userId)))
        sync()
        database.albumDao().deleteAlbum(albumId.toString())
        requests.clear()

        sync()

        val album = database.albumDao().findAlbum(albumId.toString())!!
        assertTrue(album.deleted)
        assertTrue("Failed DELETE still owes a remote acknowledgement", album.cloudKnown)
        assertEquals(listOf("GET", "DELETE", "GET"), methods())
    }

    @Test
    fun aSuccessfulCoverMutationStillRefreshesRemoteStateAndClearsOnlyAcknowledgedIntent() = runBlocking {
        snapshot.set(listOf(remote().copy(photos = remote().photos + CloudAlbumPhoto(otherPhotoId,
            otherAssetId, remoteOwner, 2, "image/jpeg", 10, 20, false))))
        sync()
        database.albumDao().setCover(albumId.toString(), otherPhotoId.toString())
        mutation = { _, path ->
            if (path.endsWith("/cover")) {
                snapshot.set(listOf(snapshot.get().single().copy(name = "Changed during mutation", coverAssetId = otherAssetId)))
                200
            } else 500
        }
        requests.clear()

        sync()

        val album = database.albumDao().findAlbum(albumId.toString())!!
        assertEquals(listOf("GET", "PUT", "GET"), methods())
        assertEquals("Changed during mutation", album.name)
        assertEquals(otherPhotoId.toString(), album.coverPhotoId)
        assertFalse(album.coverDirty)
    }

    @Test
    fun aMutationErrorStillRefreshesBecauseTheServerMayAlreadyHaveAppliedIt() = runBlocking {
        snapshot.set(emptyList())
        val dao = database.albumDao()
        val category = UUID.randomUUID().toString()
        dao.insertCategory(AlbumCategoryEntity(category, "Trips", 100))
        dao.upsertAlbum(AlbumEntity(albumId.toString(), "Local album", category, 100,
            serverUrl = session.storageOrigin, coupleId = session.coupleId.toString(),
            createdBy = session.userId.toString()))
        mutation = { _, _ ->
            snapshot.set(listOf(remote().copy(name = "Local album", createdBy = session.userId, photos = emptyList())))
            500
        }

        sync()

        assertEquals(listOf("GET", "PUT", "GET"), methods())
        assertTrue("Post-error snapshot confirms the server accepted the album", dao.findAlbum(albumId.toString())!!.cloudKnown)
    }

    private fun remote() = CloudAlbum(albumId, "Cloud album", "Trips", remoteOwner, 100,
        assetId, false, listOf(CloudAlbumPhoto(photoId, assetId, remoteOwner, 1, "image/jpeg", 10, 20, false)))

    private suspend fun sync() = AlbumCloudSync.sync(context, database, session, bindingValid = { true })

    private fun methods() = requests.map { it.substringBefore(' ') }

    private fun snapshotJson(): String {
        val albums = JSONArray()
        for (album in snapshot.get()) {
            val photos = JSONArray()
            for (photo in album.photos) {
                photos.put(JSONObject()
                    .put("id", photo.id.toString())
                    .put("assetId", photo.assetId.toString())
                    .put("addedBy", photo.addedBy.toString())
                    .put("takenAtEpochMillis", photo.takenAtEpochMillis)
                    .put("mimeType", photo.mimeType)
                    .put("width", photo.width)
                    .put("height", photo.height)
                    .put("deleted", photo.deleted))
            }
            albums.put(JSONObject()
                .put("id", album.id.toString())
                .put("name", album.name)
                .put("category", album.category)
                .put("createdBy", album.createdBy.toString())
                .put("createdAtEpochMillis", album.createdAtEpochMillis)
                .put("coverAssetId", album.coverAssetId?.toString() ?: JSONObject.NULL)
                .put("deleted", album.deleted)
                .put("photos", photos))
        }
        return albums.toString()
    }

    private fun auditWrites() {
        // Test-only SQLite triggers count actual persisted writes, not source text
        // or a mock DAO. Install after the baseline import so warmup is excluded.
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("CREATE TABLE album_test_writes (tableName TEXT NOT NULL)")
        for (table in listOf("independent_albums", "album_photos", "album_categories")) {
            for (event in listOf("INSERT", "UPDATE", "DELETE")) {
                sqlite.execSQL("CREATE TRIGGER audit_${table}_$event AFTER $event ON $table " +
                    "BEGIN INSERT INTO album_test_writes(tableName) VALUES ('$table'); END")
            }
        }
    }

    private fun writeCount(): Long = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM album_test_writes").use { it.moveToFirst(); it.getLong(0) }
}
