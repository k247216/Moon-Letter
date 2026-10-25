package com.twomemory.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.database.AppDatabase
import com.twomemory.network.RetrofitMediaApi
import com.twomemory.network.SharedAlbumApi
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in GET-only investigation. Never emits tokens, URLs, titles or photo contents. */
class AlbumMediaDiagnosticTest {
    @Test fun inspectCloudOriginalsAndDeviceCopies() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("live_media_diagnostic") == "true")
        val context = instrumentation.targetContext
        val session = requireNotNull(SyncSession.load(context))
        val dao = AppDatabase.build(context, session.databaseName).albumDao()
        val photos = SharedAlbumApi(session.baseUrl, session.token, session.coupleId).read()
            .filterNot { it.deleted }.flatMap { it.photos }.filterNot { it.deleted }
        val media = RetrofitMediaApi.create()
        fun report(value: String) { instrumentation.sendStatus(0, Bundle().apply { putString("media_diagnostic", value) }) }
        report("snapshot photos=${photos.size}")
        for ((index, photo) in photos.withIndex()) {
            val local = dao.findPhoto(photo.id.toString())?.localPath?.let(::File)
            val exists = local?.isFile == true
            try {
                val bytes = media.download(session.baseUrl, session.token, photo.assetId)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val small = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = 16 })
                val decoded = small != null
                small?.recycle()
                val same = if (exists) MessageDigest.getInstance("SHA-256").digest(local!!.readBytes())
                    .contentEquals(MessageDigest.getInstance("SHA-256").digest(bytes)) else false
                report("photo=${index + 1} http=200 bytes=${bytes.size} mime=${photo.mimeType} dimensions=${bounds.outWidth}x${bounds.outHeight} decoded=$decoded local=$exists identical=$same")
            } catch (failure: Exception) {
                val status = Regex("HTTP (\\d{3})").find(failure.message.orEmpty())?.groupValues?.get(1) ?: "none"
                report("photo=${index + 1} http=$status error=${failure.javaClass.simpleName} local=$exists")
            }
        }
    }
}
