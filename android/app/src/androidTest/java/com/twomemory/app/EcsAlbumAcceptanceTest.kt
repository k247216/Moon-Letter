package com.twomemory.app

import android.net.Uri
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.database.*
import com.twomemory.network.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Actual ECS/OSS acceptance; cannot pass against the LAN/local-storage deployment. */
class EcsAlbumAcceptanceTest {
    @Test fun realPhoneOriginalUploadsDirectlyToPrivateOssAndDownloadsThroughEcs() = runBlocking {
        assumeTrue("ECS acceptance requires opt-in", InstrumentationRegistry.getArguments().getString("ecs_acceptance") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = requireNotNull(SyncSession.load(context))
        require(session.baseUrl == "http://127.0.0.1:18088" || session.baseUrl == ServerEndpointMigration.PUBLIC_ORIGIN)
        require(session.databaseName != "moon-letter.db") { "Cloud must use an isolated database" }
        val old = AppDatabase.build(context, "moon-letter.db")
        val source = old.albumDao().allAlbums().flatMap { it.photos }.first {
            !it.deleted && it.localPath.contains("/independent_albums/") && File(it.localPath).isFile
        }
        val cloud = AppDatabase.build(context, session.databaseName)
        val repo = AlbumRepository(cloud, context, AlbumScope(session.baseUrl, session.coupleId.toString(), session.userId.toString()))
        val id = repo.createAlbum("云端链路验收", "测试")
        assertEquals(1, repo.importPhotos(id, listOf(Uri.fromFile(File(source.localPath)))).importedCount)
        val original = repo.observeAlbums().first().first { it.album.id == id }.photos.single()
        var signedOss = false
        val real = RetrofitMediaApi.create()
        val checked = object : MediaApi by real {
            override suspend fun createUpload(baseUrl: String, bearer: String, command: MediaCreateCommand): UploadTicketDto {
                val ticket = real.createUpload(baseUrl, bearer, command)
                val uri = java.net.URI(ticket.url)
                require(uri.scheme == "https" && uri.host == BuildConfig.OSS_HOST) {
                    "Expected configured private OSS signed transport, not local storage"
                }
                signedOss = true
                return ticket
            }
        }
        AlbumCloudSync.sync(context, cloud, session, checked)
        assertTrue("No private OSS ticket observed", signedOss)
        val local = requireNotNull(cloud.albumDao().findPhoto(original.id))
        assertTrue("ECS did not acknowledge original", local.cloudKnown)
        val asset = requireNotNull(local.assetId)
        val bytes = real.download(session.baseUrl, session.token, java.util.UUID.fromString(asset))
        val digest = MessageDigest.getInstance("SHA-256")
        assertArrayEquals(digest.digest(File(original.localPath).readBytes()), digest.digest(bytes))
        val snapshot = SharedAlbumApi(session.baseUrl, session.token, session.coupleId).read()
        assertTrue(snapshot.first { it.id.toString() == id }.photos.any { it.id.toString() == original.id })
        context.getSharedPreferences("moon_letter_cloud_acceptance", android.content.Context.MODE_PRIVATE).edit()
            .putString("albumId", id).putString("photoId", original.id).putString("assetId", asset).apply()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("cloud_verification", "ECS authenticated; private OSS signed upload; original download hash match")
            putString("albumId", id)
        })
    }
}
