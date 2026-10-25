package com.twomemory.app

import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.database.AppDatabase
import com.twomemory.network.SharedAlbumApi
import com.twomemory.network.RetrofitMediaApi
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicitly authorized recovery only. No bootstrap, pairing or identity changes. */
class AlbumSessionRecoveryTest {
    @Test fun restoreSameMemberThenVerifyExistingOriginalPhotos() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Recovery requires explicit opt-in", arguments.getString("recover_same_member") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = requireNotNull(SyncSession.load(context))
        assertEquals(previous.userId.toString(), arguments.getString("expected_user"))
        assertEquals(previous.coupleId.toString(), arguments.getString("expected_space"))
        assertEquals(previous.baseUrl, arguments.getString("expected_origin"))
        val replacement = requireNotNull(arguments.getString("replacement_token"))
        require(replacement.matches(Regex("[A-Za-z0-9_-]{43}")))
        // Verify the issued token before writing it to the phone's AndroidKeyStore-protected prefs.
        SharedAlbumApi(previous.baseUrl, replacement, previous.coupleId).read()
        val db = AppDatabase.build(context)
        val originals = db.albumDao().allAlbums().filter { !it.album.deleted &&
            (it.album.serverUrl == null || (it.album.serverUrl == previous.baseUrl.trimEnd('/') &&
                it.album.coupleId == previous.coupleId.toString())) }.flatMap { it.photos }.filterNot { it.deleted }
        assertTrue("No original photos to verify", originals.isNotEmpty())
        SyncSession.save(context, replacement, previous.coupleId, previous.userId, previous.baseUrl)
        val restored = requireNotNull(SyncSession.load(context))
        assertEquals(previous.userId, restored.userId)
        assertEquals(previous.coupleId, restored.coupleId)
        println("ALBUM_RECOVERY same_member=true same_space=true originals=${originals.size}")
        SyncSession.installProcessHooks(context)
        AlbumCloudSync.sync(context, db, restored)
        val remote = SharedAlbumApi(restored.baseUrl, restored.token, restored.coupleId).read()
            .flatMap { it.photos }.filterNot { it.deleted }.associateBy { it.id.toString() }
        val media = RetrofitMediaApi.create()
        for (original in originals) {
            val saved = requireNotNull(db.albumDao().findPhoto(original.id))
            assertTrue("Original not acknowledged", saved.cloudKnown)
            assertNotNull("Original has no server asset", saved.assetId)
            assertEquals(original.localPath, saved.localPath)
            assertNotNull("Original missing from remote album snapshot", remote[original.id])
            val downloaded = media.download(restored.baseUrl, restored.token, UUID.fromString(saved.assetId))
            assertArrayEquals("Original bytes differ from authenticated download",
                MessageDigest.getInstance("SHA-256").digest(File(original.localPath).readBytes()),
                MessageDigest.getInstance("SHA-256").digest(downloaded))
        }
        println("ALBUM_RECOVERY acknowledged=${originals.size} original_download_hashes_match=true")
    }
}
