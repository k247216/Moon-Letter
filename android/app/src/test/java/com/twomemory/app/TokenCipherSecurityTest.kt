package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.Security
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TokenCipherSecurityTest {
    @Test fun failedDurableBindingWriteIsNotAcknowledgedAndPreservesPreviousIdentity() {
        SyncSession.save(context,"old",UUID.randomUUID(),UUID.randomUUID())
        val prefs=SyncSession.sessionPrefs(context)
        val before=prefs.all.toMap()
        val failedPrefs=object : android.content.SharedPreferences by prefs {
            override fun edit(): android.content.SharedPreferences.Editor {
                val delegate=prefs.edit()
                return object : android.content.SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { delegate.putString(key,value); return this }
                    override fun remove(key: String?): android.content.SharedPreferences.Editor { delegate.remove(key); return this }
                    override fun clear(): android.content.SharedPreferences.Editor { delegate.clear(); return this }
                    override fun commit(): Boolean { delegate.apply(); return false }
                }
            }
        }
        val wrapped=object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int)=failedPrefs
        }
        assertTrue(runCatching { SyncSession.save(wrapped,"new",UUID.randomUUID(),UUID.randomUUID()) }.isFailure)
        assertEquals(before,prefs.all)
    }
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    @Test fun unavailableKeystoreNeverWritesPlaintextOrChangesExistingBinding() {
        SyncSession.save(context,"old-test-token",UUID.randomUUID(),UUID.randomUUID())
        val before=SyncSession.sessionPrefs(context).all.toMap()
        val provider=Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        try {
            assertTrue(runCatching { SyncSession.save(context,"new-test-token",UUID.randomUUID(),UUID.randomUUID()) }.isFailure)
            assertEquals(before,SyncSession.sessionPrefs(context).all)
        } finally { Security.addProvider(provider) }
    }
    @Test fun unreadableEncryptedTokenDoesNotClearOriginalSpaceAndDatabase() {
        SyncSession.save(context,"test-token",UUID.randomUUID(),UUID.randomUUID())
        val prefs=SyncSession.sessionPrefs(context)
        prefs.edit().putString("token","enc:AA==").commit()
        val before=prefs.all.toMap()
        assertNull(SyncSession.load(context))
        assertEquals(before,prefs.all)
    }
    @Test fun legacyPlainTokenIsResealedWithoutReplacingIdentity() {
        SyncSession.save(context,"test-token",UUID.randomUUID(),UUID.randomUUID())
        val before=SyncSession.load(context)!!
        val prefs=SyncSession.sessionPrefs(context)
        prefs.edit().putString("token","plain:test-token").commit()
        assertEquals(before,SyncSession.load(context))
        assertTrue(prefs.getString("token","")!!.startsWith("enc:"))
        assertFalse(prefs.getString("token","")!!.contains("test-token"))
    }
    @Test fun realGcmRoundTripUsesRandomIvAndRejectsCorruption() {
        val first=TokenCipher.seal("private-token")
        val second=TokenCipher.seal("private-token")
        assertNotEquals(first,second)
        assertEquals("private-token",TokenCipher.unseal(first))
        val bytes=android.util.Base64.decode(first.removePrefix("enc:"),android.util.Base64.NO_WRAP)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { TokenCipher.unseal("enc:"+android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)) }.isFailure)
    }
}
