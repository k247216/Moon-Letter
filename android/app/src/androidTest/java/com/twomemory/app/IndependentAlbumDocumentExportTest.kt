package com.twomemory.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.twomemory.database.AlbumPhotoEntity
import com.twomemory.database.AlbumRepository
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.TwoMemoryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android 8–9. The real ActivityResult contract is used; only external picker UI is replaced. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26, maxSdkVersion = 28)
class IndependentAlbumDocumentExportTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private val registry = PickerRegistry(context)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            val album = AlbumRepository(database, context).createAlbum("文件保存验证", "测试")
            database.albumDao().addPhoto(AlbumPhotoEntity("document-photo", album,
                "/missing/document-original.image", "image/heic", 3, 2, 1, 1))
        }
    }

    @After fun tearDown() = database.close()

    @Test fun systemPickerCancellationAfterRecreationRestoresUsableViewer() {
        val restoration = StateRestorationTester(composeRule)
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TwoMemoryTheme { IndependentAlbumScreen(database) }
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("照片").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("照片").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1970 年 1 月").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("album-photo-document-photo").performClick()
        composeRule.onNodeWithText("保存到手机").performClick()
        composeRule.runOnIdle {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, registry.intent!!.action)
            assertEquals("image/heic", registry.intent!!.type)
            assertTrue(registry.intent!!.hasCategory(Intent.CATEGORY_OPENABLE))
            assertTrue(registry.intent!!.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith(".heic"))
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("选择保存位置…").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { registry.cancelSelection() }
        composeRule.onNodeWithText("已取消保存").assertIsDisplayed()
        composeRule.onNodeWithText("保存到手机").assertIsEnabled()
        // Cancellation must neither read the missing source nor claim a write failure/success.
        assertTrue(composeRule.onAllNodesWithText("照片无法读取，保存失败").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("关闭").performClick()
    }

    private class PickerRegistry(private val context: Context) : ActivityResultRegistry() {
        var intent: Intent? = null
        private var launchedCode = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) {
            intent = contract.createIntent(context, input)
            launchedCode = requestCode
        }
        fun cancelSelection() { dispatchResult(launchedCode, Activity.RESULT_CANCELED, null) }
    }
}
