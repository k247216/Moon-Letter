package com.twomemory.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.database.AppDatabase
import com.twomemory.database.AlbumRepository
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.designsystem.TwoMemoryTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndependentAlbumScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var repository: AlbumRepository
    private var previousPhotoView: Boolean? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE)
        previousPhotoView = if (prefs.contains("showPhotos")) prefs.getBoolean("showPhotos", false) else null
        prefs.edit().putBoolean("showPhotos", false).commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AlbumRepository(database, context)
    }
    @After fun tearDown() {
        database.close()
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        previousPhotoView?.let { editor.putBoolean("showPhotos", it) } ?: editor.remove("showPhotos")
        editor.commit()
    }

    @Test fun createsNamedClassifiedAlbumAndShowsHonestEmptyState() {
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("留一页给回忆").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithText("新建相册").onFirst().performClick()
        composeRule.onNodeWithTag("album-name").performTextInput("湖边的周末")
        composeRule.onNodeWithTag("album-category").performTextInput("旅行")
        composeRule.onNodeWithText("创建相册").performClick()
        composeRule.waitUntil(5_000) { runBlocking { database.albumDao().observeAlbums().first().isNotEmpty() } }
        composeRule.onNodeWithText("湖边的周末").assertIsDisplayed()
        composeRule.onNodeWithText("还没有照片").assertIsDisplayed()
        composeRule.onAllNodesWithText("添加照片").onLast().assertIsDisplayed()
        composeRule.onNodeWithText("把我们的日子，好好收起来").assertIsDisplayed()
        runBlocking {
            assertEquals(0, database.entryDao().observeTimeline().first().size)
            assertEquals(0, database.outboxDao().pending(Long.MAX_VALUE, 10).size)
        }
    }

    @Test fun remotePhotoWaitsForDownloadThenAppearsWithoutReadError() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File(context.cacheDir, "pending-${java.util.UUID.randomUUID()}.png")
        val photo = runBlocking {
            val id = repository.createAlbum("等待照片", "测试")
            AlbumPhotoEntity("pending", id, file.absolutePath, "image/png", 100, 100, 1, 1,
                assetId = java.util.UUID.randomUUID().toString(), cloudKnown = true)
                .also { database.albumDao().addPhoto(it) }
        }
        try {
            composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("加载中…").fetchSemanticsNodes().isNotEmpty() }
            org.junit.Assert.assertTrue(composeRule.onAllNodesWithText("照片无法读取").fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithText("照片").performClick()
            composeRule.onNodeWithTag("album-photo-pending").performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("加载中…").fetchSemanticsNodes().isNotEmpty() }
            org.junit.Assert.assertTrue(composeRule.onAllNodesWithText("照片无法读取").fetchSemanticsNodes().isEmpty())
            context.resources.openRawResource(com.twomemory.designsystem.R.drawable.moonletter_cover_v7)
                .use { input -> file.outputStream().use { input.copyTo(it) } }
            runBlocking { database.albumDao().upsertPhoto(photo.copy(importedAtEpochMillis = 2)) }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithContentDescription("相册照片", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            org.junit.Assert.assertTrue(composeRule.onAllNodesWithText("照片无法读取").fetchSemanticsNodes().isEmpty())
        } finally { file.delete() }
    }

    @Test fun corruptLocalPhotoShowsFailureAndRetryCanReadRepairedFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File(context.cacheDir, "corrupt-${java.util.UUID.randomUUID()}.png")
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            runBlocking {
                val id = repository.createAlbum("读取错误", "测试")
                database.albumDao().addPhoto(AlbumPhotoEntity("corrupt", id, file.absolutePath,
                    "image/png", 100, 100, 1, 1, assetId = java.util.UUID.randomUUID().toString(), cloudKnown = true))
            }
            composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
            composeRule.onNodeWithText("照片").performClick()
            composeRule.onNodeWithTag("album-photo-corrupt").performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("重试").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onAllNodesWithText("照片无法读取").onLast().assertIsDisplayed()
            context.resources.openRawResource(com.twomemory.designsystem.R.drawable.moonletter_cover_v7)
                .use { input -> file.outputStream().use { input.copyTo(it) } }
            composeRule.onAllNodesWithText("重试").onLast().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithContentDescription("相册照片", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } finally { file.delete() }
    }

    @Test fun mosaicKeepsPanoramaRatioAndLimitsTallPreviewHeight() {
        runBlocking {
            val id = repository.createAlbum("比例测试", "测试")
            database.albumDao().addPhoto(AlbumPhotoEntity("wide", id, "/missing/wide.png", "image/png", 400, 100, 1, 1))
            database.albumDao().addPhoto(AlbumPhotoEntity("tall", id, "/missing/tall.png", "image/png", 100, 400, 2, 2))
        }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("album-photo-wide").fetchSemanticsNodes().isNotEmpty() }
        val wide = composeRule.onNodeWithTag("album-photo-wide").getUnclippedBoundsInRoot()
        val tall = composeRule.onNodeWithTag("album-photo-tall").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals("Panorama keeps its natural frame ratio",
            4f, (wide.right - wide.left) / (wide.bottom - wide.top), .05f)
        org.junit.Assert.assertTrue((wide.bottom - wide.top).value <= 220.5f)
        org.junit.Assert.assertTrue("Long portrait preview must not dominate the album",
            (tall.bottom - tall.top).value <= 220.5f)
    }

    @Test fun photoWallUsesNaturalShapesRatherThanFixedHeroFrames() {
        runBlocking {
            val id = repository.createAlbum("照片墙", "测试")
            repeat(3) { index -> database.albumDao().addPhoto(AlbumPhotoEntity("piece-$index", id,
                "/missing/piece-$index.png", "image/png", if (index == 0) 200 else 100, 100, index + 1L, index + 1L)) }
        }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("album-photo-piece-0").fetchSemanticsNodes().isNotEmpty() }
        val sizes = (0..2).map { composeRule.onNodeWithTag("album-photo-piece-$it").getUnclippedBoundsInRoot() }
        val widths = sizes.map { (it.right - it.left).value }
        org.junit.Assert.assertTrue("Do not force a hero/small-photo width hierarchy", widths.max() - widths.min() < 1f)
        val wide = sizes[0]
        org.junit.Assert.assertEquals(2f, (wide.right - wide.left) / (wide.bottom - wide.top), .05f)
    }

    @Test fun captureAlbumWallAndCovers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            repeat(4) { albumIndex ->
                val id = repository.createAlbum(listOf("一起看晚霞", "月亮与我们", "周末散步", "留住今天")[albumIndex], "日常")
                repeat(if (albumIndex == 0) 6 else 1) { index ->
                    val file = java.io.File(context.cacheDir, "wall-visual-$albumIndex-$index.png")
                    val resource = if ((index + albumIndex) % 2 == 0) com.twomemory.designsystem.R.drawable.moonletter_cover_v7
                        else com.twomemory.designsystem.R.drawable.moonletter_cover_default
                    context.resources.openRawResource(resource).use { input -> file.outputStream().use { input.copyTo(it) } }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                    database.albumDao().addPhoto(AlbumPhotoEntity("visual-$albumIndex-$index", id, file.absolutePath,
                        "image/png", bounds.outWidth, bounds.outHeight, 1780000000000L + index, index + 1L))
                }
            }
        }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithContentDescription("相册照片", useUnmergedTree = true).fetchSemanticsNodes().size >= 2 }
        saveScreen("02-album-covers.png")
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithContentDescription("相册照片", useUnmergedTree = true).fetchSemanticsNodes().size >= 3 }
        saveScreen("01-photo-wall.png")
    }

    private fun saveScreen(name: String) {
        val directory = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-v18")!!
        java.io.File(directory, name).outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun tabletUsesMoreColumnsInsteadOfStretchingPhoneTiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.junit.Assume.assumeTrue(context.resources.configuration.screenWidthDp >= 600)
        runBlocking {
            val id = repository.createAlbum("平板布局", "测试")
            repeat(6) { index -> database.albumDao().addPhoto(AlbumPhotoEntity("square-$index", id,
                "/missing/$index.png", "image/png", 100, 100, 1L + index, 1L + index)) }
        }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("album-photo-square-0").fetchSemanticsNodes().isNotEmpty() }
        val columns = (0..5).map { composeRule.onNodeWithTag("album-photo-square-$it").fetchSemanticsNode().boundsInRoot.left.toInt() }.toSet()
        org.junit.Assert.assertTrue("Tablet needs at least four photo columns, got ${columns.size}", columns.size >= 4)
    }

    @Test fun albumCoverDoesNotCutOffPanoramaEdges() = checkPanoramaEdges(false)

    @Test fun recordPhotoDoesNotCutOffPanoramaEdges() = checkPanoramaEdges(true)

    @Test fun scaledTallPreviewStillShowsTopAndBottom() = checkPanoramaEdges(false, tall = true)

    private fun checkPanoramaEdges(record: Boolean, tall: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File(context.cacheDir, if (tall) "ratio-tall-edges.png" else "ratio-edges.png")
        val source = android.graphics.Bitmap.createBitmap(if (tall) 100 else 400, if (tall) 400 else 100, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(source)
        canvas.drawColor(android.graphics.Color.GREEN)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.RED
        if (tall) canvas.drawRect(0f, 0f, 100f, 80f, paint) else canvas.drawRect(0f, 0f, 80f, 100f, paint)
        paint.color = android.graphics.Color.BLUE
        if (tall) canvas.drawRect(0f, 320f, 100f, 400f, paint) else canvas.drawRect(320f, 0f, 400f, 100f, paint)
        file.outputStream().use { source.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        val id = runBlocking {
            val id = repository.createAlbum("完整照片", "测试")
            database.albumDao().addPhoto(AlbumPhotoEntity("edges", id, file.absolutePath, "image/png", if (tall) 100 else 400, if (tall) 400 else 100, 1, 1))
            id
        }
        composeRule.setContent { TwoMemoryTheme {
            if (record) Box(androidx.compose.ui.Modifier.requiredSize(120.dp, 180.dp)) {
                com.twomemory.designsystem.EntryPhoto(file.absolutePath, null, androidx.compose.ui.Modifier.fillMaxSize())
            }
            else IndependentAlbumScreen(database)
        } }
        if (tall) composeRule.onNodeWithText("照片").performClick()
        val description = if (record) "记录里的照片" else "相册照片"
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        if (!record && !tall) {
            val bounds = composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("Landscape cover should use a light horizontal frame",
                (bounds.right - bounds.left) / (bounds.bottom - bounds.top) > 1.3f)
        }
        val image = (if (record) composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true).onFirst()
            else composeRule.onNodeWithTag(if (tall) "album-photo-edges" else "album-cover-$id")).captureToImage().asAndroidBitmap()
        var red = 0
        var blue = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = image.getPixel(x, y)
            if (android.graphics.Color.red(color) > 220 && android.graphics.Color.green(color) < 30 && android.graphics.Color.blue(color) < 30) red++
            if (android.graphics.Color.blue(color) > 220 && android.graphics.Color.green(color) < 30 && android.graphics.Color.red(color) < 30) blue++
        }
        org.junit.Assert.assertTrue("Both panorama edges must remain visible: red=$red blue=$blue", red > 100 && blue > 100)
        file.delete()
    }

    @Test fun deletingAlbumRequiresConfirmationAndKeepsTombstone() {
        val id = runBlocking { repository.createAlbum("准备移除的相册", "生活") }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("准备移除的相册").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("准备移除的相册").performClick()
        composeRule.onNodeWithTag("album-delete").performClick()
        composeRule.onNodeWithText("取消").performClick()
        runBlocking { assertEquals(false, database.albumDao().findAlbum(id)!!.deleted) }
        composeRule.onNodeWithTag("album-delete").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.waitUntil(5_000) { runBlocking { database.albumDao().findAlbum(id)!!.deleted } }
    }

    @Test fun opensPhotoViewerMovesBetweenPhotosAndReportsMissingFile() {
        runBlocking {
            val id = repository.createAlbum("两张照片", "日常")
            database.albumDao().addPhoto(AlbumPhotoEntity("p1", id, "/missing/one.png", "image/png", 20, 30, 1, 1))
            database.albumDao().addPhoto(AlbumPhotoEntity("p2", id, "/missing/two.png", "image/png", 20, 30, 2, 2))
        }
        composeRule.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
        composeRule.waitUntil(5_000) { runBlocking { database.albumDao().observeAlbums().first().isNotEmpty() } }
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("album-photo-p2").performClick()
        composeRule.onNodeWithText("1 / 2").assertIsDisplayed()
        composeRule.onAllNodesWithText("照片无法读取").onLast().assertIsDisplayed()
        composeRule.onNodeWithText("下一张").performClick()
        composeRule.onNodeWithText("2 / 2").assertIsDisplayed()
        composeRule.onNodeWithText("上一张").performClick()
        composeRule.onNodeWithText("1 / 2").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
    }
}
