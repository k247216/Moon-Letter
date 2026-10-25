package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/** Real activity: a technical address must be opt-in, cancelling must not rebind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudConnectionJournalTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val compose = createAndroidComposeRule<CloudConnectionActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            SyncSession.save(context, "journal-screen-fixture", UUID.randomUUID(), UUID.randomUUID(), ServerEndpointMigration.USB_ORIGIN)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun technicalAddressIsHiddenUntilDetailsAreRequested() {
        compose.onNodeWithText(ServerEndpointMigration.USB_ORIGIN, substring = true).assertDoesNotExist()
        compose.onNodeWithText("连接详情").performScrollTo().performClick()
        compose.onNodeWithText(ServerEndpointMigration.USB_ORIGIN, substring = true).assertIsDisplayed()
        compose.onNodeWithText("收起详情").performClick()
        compose.onNodeWithText(ServerEndpointMigration.USB_ORIGIN, substring = true).assertDoesNotExist()
    }

    @Test fun cancellingLetterConfirmationKeepsOriginalIdentityAndStorage() {
        val before = SyncSession.load(context)
        compose.onNodeWithText("打开我们的手账").performScrollTo().performClick()
        compose.onNodeWithText("一起写下日常").assertExists()
        compose.onNodeWithText("再等等").performClick()
        compose.runOnIdle { assertEquals(before, SyncSession.load(context)) }
        compose.onNodeWithText("我是第一位").assertDoesNotExist()
    }

    @Test fun rendersCompactJournalWithoutSetupFormForBoundMember() {
        compose.onNodeWithText("我们的小空间").assertIsDisplayed()
        compose.onNodeWithText("记录彼此想念的每一天").assertIsDisplayed()
        compose.onNodeWithText("打开我们的手账").assertIsDisplayed()
        listOf("原来的回忆", "测试", "切换", "初始化", "保留数据").forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
        compose.onNodeWithText("服务器地址").assertDoesNotExist()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("../../releases/v31/connection-journal-phone.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
