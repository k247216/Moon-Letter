package com.twomemory.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.couple.CoupleScreen
import com.twomemory.couple.CoupleUiState
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileVisualTest {
    @get:Rule val compose = createComposeRule()
    @Test fun profileAndSettingsNativeScreenshots() {
        compose.setContent { TwoMemoryTheme {
            CoupleScreen(CoupleUiState(ownName = "星星", partnerName = "月亮", draftName = "星星"), {}, {},
                anniversaryName = "第一次见面", anniversaryDate = "2025-05-20", versionLabel = "v8 · 验收")
        } }
        capture("profile-overview.png")
        compose.onNodeWithContentDescription("设置").performClick()
        capture("profile-settings.png")
    }
    @Test fun identityFirstSetupNativeScreenshot() {
        val model = SetupViewModel().apply { updateDisplayName("星星") }
        compose.setContent { TwoMemoryTheme { SetupScreen(model, onBound = {}) } }
        capture("setup-identity.png")
    }
    private fun capture(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa")!!
        File(dir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
