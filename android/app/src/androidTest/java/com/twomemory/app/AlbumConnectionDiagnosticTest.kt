package com.twomemory.app

import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.network.SharedAlbumApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only live diagnostic. Never bootstraps members or prints authentication material. */
class AlbumConnectionDiagnosticTest {
    @Test fun boundPhoneCanReadItsActualAlbumEndpoint() = runBlocking {
        assumeTrue("Live diagnostic requires explicit opt-in", InstrumentationRegistry.getArguments()
            .getString("live_album_diagnostic") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = SyncSession.load(context)
        assertNotNull("No readable bound session on phone", session)
        val current = requireNotNull(session)
        println("ALBUM_DIAGNOSTIC backend=${current.baseUrl}")
        try {
            val albums = SharedAlbumApi(current.baseUrl, current.token, current.coupleId).read()
            println("ALBUM_DIAGNOSTIC authenticated=true albums=${albums.size} photos=${albums.sumOf { it.photos.size }}")
            val remoteIds = albums.filterNot { it.deleted }.flatMap { it.photos }.filterNot { it.deleted }.map { it.id.toString() }.toSet()
            val local = com.twomemory.database.AppDatabase.build(context, current.databaseName).albumDao().allAlbums()
                .filter { !it.album.deleted && it.album.serverUrl == current.baseUrl.trimEnd('/') && it.album.coupleId == current.coupleId.toString() }
            val localIds = local.flatMap { it.photos }.filterNot { it.deleted }.map { it.id }.toSet()
            val summary = "authenticated=true remoteLiveAlbums=${albums.count { !it.deleted }} remoteLivePhotos=${remoteIds.size} localLiveAlbums=${local.size} localLivePhotos=${localIds.size} missingFromLocal=${(remoteIds - localIds).size} localOnly=${(localIds - remoteIds).size}"
            InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("album_connection", summary) })
        } catch (exception: Exception) {
            val status = Regex("HTTP (\\d{3})").find(exception.message.orEmpty())?.groupValues?.get(1) ?: "none"
            fail("ALBUM_DIAGNOSTIC authenticated=false http=$status error=${exception.javaClass.simpleName}")
        }
    }
}
