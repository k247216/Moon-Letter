package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** An unbound invite recipient should see a journal, not deployment fields. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstWelcomeScreenTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            val context: Context = ApplicationProvider.getApplicationContext()
            context.getSharedPreferences("moon_letter_session", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun invitationIsUsableWithoutDeploymentDetails() {
        compose.onNodeWithText("记录彼此想念的每一天").assertIsDisplayed()
        compose.onNodeWithText("接受邀请").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("配对码").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("服务器地址").assertDoesNotExist()
        compose.onAllNodesWithText("BOOTSTRAP_SECRET", substring = true).assertCountEquals(0)
        compose.onNodeWithText("你的称呼").performScrollTo().performTextInput("阿屿")
        compose.onNodeWithText("配对码").performScrollTo().performTextInput("a".repeat(43))
        compose.onNodeWithText("打开我们的手账").performScrollTo().assertIsEnabled()
    }

    @Test fun technicalAddressIsOnlyShownWhenRequested() {
        compose.onNodeWithText("连接设置").performScrollTo().performClick()
        compose.onNodeWithText("服务器地址").performScrollTo().assertIsDisplayed()
    }

    @Test fun personalGreetingIsOnlyForTheCreatorNotTheInviteRecipient() {
        if (BuildConfig.FIRST_MEMBER_WELCOME != "记录彼此想念的每一天")
            compose.onAllNodesWithText(BuildConfig.FIRST_MEMBER_WELCOME).assertCountEquals(0)
        compose.onNodeWithText("创建手账").performScrollTo().performClick()
        compose.onNodeWithText(BuildConfig.FIRST_MEMBER_WELCOME).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("接受邀请").performScrollTo().performClick()
        if (BuildConfig.FIRST_MEMBER_WELCOME != "记录彼此想念的每一天")
            compose.onAllNodesWithText(BuildConfig.FIRST_MEMBER_WELCOME).assertCountEquals(0)
    }

    @Test fun capturesFirstWelcome() {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("../../releases/v31/first-welcome-phone.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
