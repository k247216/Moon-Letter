package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.couple.storeProfileAvatar
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InitialProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearLocalAvatar() {
        context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE).edit().remove("ownAvatar").commit()
    }

    @Test fun personalProfileComesBeforeBindingAndDoesNotDependOnMemberMode() {
        val model = SetupViewModel()
        compose.setContent { TwoMemoryTheme { SetupScreen(model, onBound = {}) } }
        compose.onNodeWithText("你的称呼").performTextReplacement("小月")
        compose.onNodeWithContentDescription("选择头像").assertIsDisplayed()
        compose.onNodeWithText("小", useUnmergedTree = true).assertIsDisplayed()
        val profileBottom = compose.onNodeWithText("你的称呼").fetchSemanticsNode().boundsInRoot.bottom
        val bindingTop = compose.onNodeWithText("创建手账").fetchSemanticsNode().boundsInRoot.top
        assertTrue("个人资料应先于设备绑定", profileBottom < bindingTop)
        compose.onNodeWithText("接受邀请").performScrollTo().performClick()
        compose.onNodeWithText("你的称呼").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("小", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun skippingAvatarKeepsNeutralDefaultAndNameStillRequired() {
        val model = SetupViewModel()
        compose.setContent { TwoMemoryTheme { SetupScreen(model, onBound = {}) } }
        compose.onNodeWithText("使用默认头像").performClick()
        compose.onNodeWithText("我", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE).getString("ownAvatar", null) == null)
            assertTrue(!model.state.value.canSubmit)
        }
    }

    @Test fun savedPhotoCanBeResetToNeutralDefaultInSetup() {
        val source = File.createTempFile("initial-profile", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        storeProfileAvatar(context, Uri.fromFile(source))
        val model = SetupViewModel()
        compose.setContent { TwoMemoryTheme { SetupScreen(model, onBound = {}) } }
        compose.onNodeWithContentDescription("选择头像").assertIsDisplayed()
        compose.onNodeWithText("使用默认头像").performClick()
        compose.onNodeWithText("我", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE).getString("ownAvatar", null) == null)
        }
    }
}
