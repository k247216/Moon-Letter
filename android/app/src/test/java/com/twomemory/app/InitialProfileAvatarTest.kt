package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.twomemory.couple.storeProfileAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InitialProfileAvatarTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun pickedPhotoSurvivesSourceRemovalAndExistingNavigationCanDecodeIt() {
        val source = File.createTempFile("profile-input", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(12, 10, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val stored = storeProfileAvatar(context, Uri.fromFile(source))
        assertTrue(source.delete())
        val restored = context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE).getString("ownAvatar", null)
        assertEquals(stored, restored)
        context.contentResolver.openInputStream(Uri.parse(restored)).use {
            val decoded = BitmapFactory.decodeStream(it)
            assertNotNull(decoded)
            assertEquals(12, decoded!!.width)
            assertEquals(10, decoded.height)
        }
    }

    @Test fun unreadablePhotoDoesNotReplacePreviousAvatar() {
        val prefs = context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE)
        prefs.edit().putString("ownAvatar", "file:///previous-avatar.png").commit()
        val invalid = File.createTempFile("not-an-image", ".png", context.cacheDir)
        assertTrue(runCatching { storeProfileAvatar(context, Uri.fromFile(invalid)) }.isFailure)
        assertEquals("file:///previous-avatar.png", prefs.getString("ownAvatar", null))
    }

    @Test fun largePhotoIsReducedBeforeNavigationReadsIt() {
        val source = File.createTempFile("large-profile", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val stored = storeProfileAvatar(context, Uri.fromFile(source))
        context.contentResolver.openInputStream(Uri.parse(stored)).use {
            val decoded = BitmapFactory.decodeStream(it)!!
            assertTrue(decoded.width <= 1024)
            assertEquals(2, decoded.width / decoded.height)
        }
    }
}
