package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.twomemory.couple.storeProfileAvatar
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.network.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ProfilePersistenceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun selectedCameraPhotoKeepsItsExifOrientation() {
        val source = File.createTempFile("profile-rotated-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val prefs = context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE)
        val old = prefs.getString("ownAvatar", null)
        var stored: String? = null
        try {
            stored = storeProfileAvatar(context, Uri.fromFile(source))
            val decoded = context.contentResolver.openInputStream(Uri.parse(stored)).use { BitmapFactory.decodeStream(it) }!!
            assertEquals(20, decoded.width)
            assertEquals(40, decoded.height)
            decoded.recycle()
        } finally {
            prefs.edit().putString("ownAvatar", old).commit()
            stored?.let { File(Uri.parse(it).path!!).delete() }
            source.delete()
        }
    }

    @Test fun newlyBoundIdentityIsAvailableBeforeTheFirstNetworkRefresh() {
        val prefs = SyncSession.sessionPrefs(context)
        val keys = listOf("token", "coupleId", "userId", "baseUrl", "ownName")
        val old = keys.associateWith { prefs.getString(it, null) }
        prefs.edit().remove("ownName").commit()
        val api = object : SessionApi by RetrofitSessionApi.create() {
            override suspend fun pair(baseUrl: String, pairingToken: String, displayName: String) =
                PairResultDto(CoupleViewDto(UUID.randomUUID()), "local-test-token", UUID.randomUUID())
        }
        val model = SetupViewModel(api).apply {
            updateMode(SetupViewModel.Mode.PARTNER_DEVICE)
            updateServerUrl("http://127.0.0.1:1")
            updateDisplayName("星星")
            updatePairingToken("test-invite")
        }
        try {
            compose.setContent { TwoMemoryTheme { SetupScreen(model, onBound = {}) } }
            compose.runOnIdle { model.pair() }
            compose.waitUntil(5_000) { model.state.value.sessionSaved }
            assertEquals("星星", SyncSession.loadNames(context).own)
        } finally {
            val edit = prefs.edit()
            keys.forEach { key -> old[key]?.let { edit.putString(key, it) } ?: edit.remove(key) }
            edit.commit()
        }
    }
}
