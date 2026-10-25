package com.twomemory.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.TwoMemoryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PaperAlbumCategoryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unnamedCategoryCreatesAlbumsUnderOneReusableUnclassifiedGroup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("moon_letter_album_view", Context.MODE_PRIVATE).edit().putBoolean("showPhotos", false).commit()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            compose.setContent { TwoMemoryTheme { IndependentAlbumScreen(db) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("新建相册").fetchSemanticsNodes().isNotEmpty() }
            listOf("随手收好的照片", "另一页小日常").forEachIndexed { index, title ->
                compose.onAllNodesWithText("新建相册").onFirst().performClick()
                compose.onNodeWithTag("album-name").performTextInput(title)
                compose.onNodeWithText("创建相册").assertIsEnabled().assertIsDisplayed().performClick()
                compose.waitUntil(5_000) { runBlocking { db.albumDao().observeAlbums().first().size == index + 1 } }
                compose.onNodeWithText("‹ 返回").performClick()
            }
            runBlocking {
                assertEquals(listOf("未分类"), db.albumDao().observeCategories().first().map { it.name })
                assertEquals(1, db.albumDao().observeAlbums().first().map { it.album.categoryId }.distinct().size)
                assertTrue(db.entryDao().observeTimeline().first().isEmpty())
            }
        } finally { db.close() }
    }
}
