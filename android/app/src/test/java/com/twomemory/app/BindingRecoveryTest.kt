package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BindingRecoveryTest {
    @Test fun corruptedTokenRecoveryKeepsDatabaseAndStorageOrigin() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        SyncSession.save(context,"old",UUID.randomUUID(),UUID.randomUUID(),ServerEndpointMigration.USB_ORIGIN)
        val before=SyncSession.load(context)!!
        val prefs=SyncSession.sessionPrefs(context)
        prefs.edit().putString("token","enc:AA==").commit()
        assertNull(SyncSession.load(context))
        val api=object : SessionApi by RetrofitSessionApi.create() {
            override suspend fun pair(baseUrl: String,pairingToken: String,displayName: String)=PairResultDto(CoupleViewDto(before.coupleId),"recovered",before.userId)
        }
        val issued=BindingRecovery.request(context,"rejoin-code",api)
        BindingRecovery.save(context,issued)
        assertEquals(before.copy(token="recovered"),SyncSession.load(context))
    }
    @Test fun differentIdentityCannotReplaceUnreadableBinding() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        SyncSession.save(context,"old",UUID.randomUUID(),UUID.randomUUID(),ServerEndpointMigration.USB_ORIGIN)
        val before=SyncSession.load(context)!!
        val prefs=SyncSession.sessionPrefs(context)
        prefs.edit().putString("token","enc:AA==").commit()
        val snapshot=prefs.all.toMap()
        val api=object : SessionApi by RetrofitSessionApi.create() {
            override suspend fun pair(baseUrl: String,pairingToken: String,displayName: String)=PairResultDto(CoupleViewDto(before.coupleId),"wrong",UUID.randomUUID())
        }
        assertTrue(runCatching { BindingRecovery.request(context,"wrong-code",api) }.isFailure)
        assertEquals(snapshot,prefs.all)
    }
}
