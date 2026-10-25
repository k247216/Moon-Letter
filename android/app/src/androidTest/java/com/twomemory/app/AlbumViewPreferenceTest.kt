package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AlbumRepository
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.TwoMemoryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumViewPreferenceTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var albumId: String
    private lateinit var original: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        original = File.createTempFile("album-view-test-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        runBlocking {
            albumId = AlbumRepository(database, context).createAlbum("独立原件", "日常")
            database.albumDao().addPhoto(AlbumPhotoEntity("view-pref-photo", albumId,
                original.absolutePath, "image/png", 20, 30, 1, 1))
        }
    }
    @After fun tearDown() {
        database.close()
        original.delete()
        context.getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultsToAlbumSetsAndAllPhotosUsesExistingViewer() {
        composeRule.setContent { TwoMemoryTheme { Box(Modifier.fillMaxSize()) { IndependentAlbumScreen(database) } } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("album-cover-$albumId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("album-cover-$albumId").assertIsDisplayed()
        composeRule.onNodeWithTag("album-view-photos").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("album-photo-view-pref-photo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("album-photo-view-pref-photo").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
        composeRule.onNodeWithTag("album-save-to-phone").assertIsDisplayed()
    }

    @Test fun selectedAllPhotosSurvivesLeavingAndReopeningScreen() {
        val visible = mutableStateOf(true)
        composeRule.setContent { Box(Modifier.fillMaxSize()) { if (visible.value) TwoMemoryTheme { IndependentAlbumScreen(database) } } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("album-cover-$albumId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("album-view-photos").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("album-photo-view-pref-photo").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(context.getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE).getBoolean("showPhotos", false))
        }
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible.value = true }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("album-photo-view-pref-photo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("album-photo-view-pref-photo").assertIsDisplayed()
        // Switching back must persist as well; otherwise restart always restores photos.
        composeRule.onNodeWithTag("album-view-sets").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("album-cover-$albumId").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(context.getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE).getBoolean("showPhotos", true))
        }
    }
}
