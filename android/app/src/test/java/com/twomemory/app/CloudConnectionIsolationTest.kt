package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.database.AlbumRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudConnectionIsolationTest {
    @Test fun changingFromLanToEcsCannotPublishOrDisplayTheOldLocalQueue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val space = UUID.randomUUID()
        val user = UUID.randomUUID()
        SyncSession.save(context, "local-test-token", space, user, ServerAddress.DEV_DEFAULT_BASE_URL)
        val old = AppDatabase.build(context)
        val oldId = AlbumRepository(old, context).createAlbum("旧局域网相册", "日常")
        SyncSession.save(context, "cloud-test-token", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:18088")
        val cloud = AppDatabase.build(context)
        assertNotSame("Cloud must not reuse LAN database handle", old, cloud)
        assertTrue(cloud.albumDao().observeAlbums().first().isEmpty())
        assertNotNull(old.albumDao().findAlbum(oldId))
    }

    @Test fun replacingACloudTokenKeepsTheSameCloudCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val space = UUID.randomUUID()
        val user = UUID.randomUUID()
        SyncSession.save(context, "first-cloud-token", space, user, "https://api.example.com")
        val before = AppDatabase.build(context)
        val id = AlbumRepository(before, context).createAlbum("云端相册缓存", "旅行")
        SyncSession.save(context, "replacement-cloud-token", space, user, "https://api.example.com/")
        val after = AppDatabase.build(context)
        assertSame(before, after)
        assertNotNull(after.albumDao().findAlbum(id))
    }
}
