package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AlbumBindingFenceTest {
    @Test fun staleSpaceOrUnavailableCredentialsNeverStartAlbumHttp() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        SyncSession.save(context,"old",UUID.randomUUID(),UUID.randomUUID(),"http://127.0.0.1:1")
        val old=SyncSession.load(context)!!
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            SyncSession.save(context,"new",UUID.randomUUID(),UUID.randomUUID(),old.baseUrl)
            // Port 1 is not a test server: any stale HTTP attempt fails this test.
            AlbumCloudSync.sync(context,db,old)
            SyncSession.sessionPrefs(context).edit().putString("token","enc:AA==").commit()
            AlbumCloudSync.sync(context,db,old)
        } finally { db.close() }
    }
}
