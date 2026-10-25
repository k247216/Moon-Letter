package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AlbumRepository
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.TwoMemoryTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class IndependentAlbumExportTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private var source: File? = null
    private val created = mutableListOf<Uri>()
    private var baseline = emptySet<Uri>()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        baseline = ownExports()
    }

    @After fun tearDown() {
        // Only rows introduced by this test; no clearing the user's gallery or album directory.
        (created + (ownExports() - baseline)).distinct().forEach { context.contentResolver.delete(it, null, null) }
        database.close()
        source?.delete()
    }

    @Test fun viewerSavesCurrentPhotoAndShowsSuccessOnlyAfterPublishedCopy() {
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        source = File.createTempFile("album-ui-export-", ".image", context.cacheDir).apply { writeBytes(bytes) }
        openViewer(source!!.absolutePath)
        composeRule.onNodeWithText("保存到手机").assertIsDisplayed().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("已保存到手机相册").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("已保存到手机相册").assertIsDisplayed()
        created += ownExports() - baseline
        assertEquals(1, created.size)
        val uri = created.single()
        assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertArrayEquals(bytes, source!!.readBytes())
        runBlocking {
            assertEquals(1, database.albumDao().observeAlbums().first().single().photos.size)
            assertEquals(0, database.entryDao().observeTimeline().first().size)
            assertEquals(0, database.outboxDao().pending(Long.MAX_VALUE, 10).size)
        }
        composeRule.onNodeWithText("关闭").performClick()
    }

    @Test fun missingOriginalShowsFailureWithoutCreatingGalleryRow() {
        openViewer(File(context.cacheDir, "missing-${UUID.randomUUID()}.image").absolutePath)
        composeRule.onNodeWithText("保存到手机").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("照片无法读取，保存失败").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("照片无法读取，保存失败").assertIsDisplayed()
        assertEquals(baseline, ownExports())
        composeRule.onNodeWithText("保存到手机").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
    }

    private fun openViewer(path: String) {
        runBlocking {
            val album = AlbumRepository(database, context).createAlbum("导出验证", "测试")
            database.albumDao().addPhoto(AlbumPhotoEntity("export-photo", album, path, "image/png", 3, 2, 1, 1))
        }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("照片").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1970 年 1 月").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("album-photo-export-photo").performClick()
    }

    private fun ownExports(): Set<Uri> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("Pictures/MoonLetter/", "MoonLetter_%"), null)!!.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(Uri.withAppendedPath(collection, cursor.getLong(0).toString()))
            }
        }
    }
}
