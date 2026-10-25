package com.twomemory.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.database.AppDatabase
import com.twomemory.database.AlbumRepository
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.twomemory.designsystem.*
import com.twomemory.editor.*
import com.twomemory.model.CityOption
import com.twomemory.model.EntryMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File
import java.time.Instant

class ProductVisualAcceptanceTest {
    @get:Rule val compose = createComposeRule()
    private var previousPhotoView: Boolean? = null

    @Before fun isolateAlbumView() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("moon_letter_album_view", android.content.Context.MODE_PRIVATE)
        previousPhotoView = if (prefs.contains("showPhotos")) prefs.getBoolean("showPhotos", false) else null
        prefs.edit().putBoolean("showPhotos", false).commit()
    }

    @After fun restoreAlbumView() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("moon_letter_album_view", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        previousPhotoView?.let { editor.putBoolean("showPhotos", it) } ?: editor.remove("showPhotos")
        editor.commit()
    }

    @Test fun mapSelectsRealPinAndShowsOnlyThatCity() {
        var writeCity: CityOption? = null
        compose.setContent {
            TwoMemoryTheme {
                CityMapPreviewScreen(listOf(
                    CityStoryUi("h", "eh", "杭州", "10月2日", "杭州晚风", "湖边走了一段路。", "小满"),
                    CityStoryUi("s", "es", "上海", "10月1日", "上海周末", "把周末留在外滩。", "阿屿"),
                ), onCreateInCity = { writeCity = it })
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("上海，1篇记录", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        capture("map-hangzhou.png")
        compose.onNodeWithText("杭州晚风").assertIsDisplayed()
        compose.onNodeWithText("上海周末").assertDoesNotExist()
        compose.onNode(hasContentDescription("上海，1篇记录", substring = true)).performClick()
        compose.onNodeWithText("上海的故事").assertIsDisplayed()
        compose.onNodeWithText("上海周末").assertIsDisplayed()
        compose.onNodeWithText("杭州晚风").assertDoesNotExist()
        compose.onNodeWithText("写故事").performClick()
        assertEquals("上海", writeCity?.name)
        capture("map-shanghai.png")
    }

    @Test fun editorUsesSamePaperAndSixWorkingToolSlots() {
        compose.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(EditorUiState(
                    occurrenceTime = Instant.parse("2026-10-02T12:18:00Z"),
                    title = "晚风经过", body = "今天下课后绕了远路回家。\n\n晚霞把整片天空染成了橘粉色，云朵像被风推着慢慢流动。幸好今天，我停下来了。"),
                    {}, {}, {}, ownName = "小满", photoActions = EditorPhotoActions({}, {}))
            }
        }
        listOf("图片", "视频", "语音", "音乐", "城市", "更多").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        capture("editor-personal.png")
    }

    @Test fun independentAlbumCreationHasNoDiarySideEffect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            compose.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("留一页给回忆").fetchSemanticsNodes().isNotEmpty() }
            capture("albums-empty.png")
            compose.onAllNodesWithText("新建相册").onFirst().performClick()
            compose.onNodeWithTag("album-name").performTextInput("我们的旅行")
            compose.onNodeWithTag("album-category").performTextInput("旅行")
            compose.onNodeWithText("创建相册").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("我们的旅行").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("我们的旅行").assertIsDisplayed()
            compose.onNodeWithText("还没有照片").assertIsDisplayed()
            capture("albums-created.png")
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM entries").use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
        } finally { database.close() }
    }

    @Test fun importedPhotosAppearInIndependentMosaicAndFullScreenViewer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val sources = (1..3).map { File.createTempFile("album-visual-", ".png", context.cacheDir) }
        try {
            val repository = AlbumRepository(database, context)
            val album = runBlocking { repository.createAlbum("湖边的周末", "旅行") }
            sources.forEach { file ->
                context.resources.openRawResource(com.twomemory.designsystem.R.drawable.moonletter_cover_v7).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }
            runBlocking { assertEquals(3, repository.importPhotos(album, sources.map(Uri::fromFile)).importedCount) }
            compose.setContent { TwoMemoryTheme { IndependentAlbumScreen(database) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("湖边的周末").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("湖边的周末").performClick()
            val photos = runBlocking { database.albumDao().observeAlbums().first().first { it.album.id == album }.photos }
                .sortedWith(compareByDescending<com.twomemory.database.AlbumPhotoEntity> { it.takenAtEpochMillis }.thenBy { it.id })
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("album-photo-${photos.first().id}").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("相册照片").fetchSemanticsNodes().size == 3 }
            capture("albums-photos.png")
            compose.onNodeWithTag("album-photo-${photos.first().id}").performClick()
            compose.onNodeWithText("1 / 3").assertIsDisplayed()
            capture("albums-viewer.png")
            compose.onNodeWithText("下一张").performClick()
            compose.onNodeWithText("2 / 3").assertIsDisplayed()
        } finally {
            database.close()
            sources.forEach { it.delete() }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir("visual-qa")!!
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
