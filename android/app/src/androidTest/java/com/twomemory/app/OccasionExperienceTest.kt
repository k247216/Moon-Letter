package com.twomemory.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.designsystem.TwoMemoryTheme
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class OccasionExperienceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sendingCardIgnoresSystemBackAndPreservesItsContent() {
        var left = false
        val card = OccasionCard(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "BIRTHDAY", "还在递送的生日", "不要丢掉这句话", java.time.LocalDate.now().toString(), true, null, null, "")
        compose.setContent { TwoMemoryTheme { OccasionEditor(card, true, null, { left = true }, {}) } }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        compose.waitForIdle()
        compose.runOnIdle { org.junit.Assert.assertFalse(left) }
        compose.onNodeWithText("卡片标题").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(card.title)))
        compose.onNodeWithText("正在递送…").performScrollTo().assertIsNotEnabled()
    }
    @Test fun birthdayIsUserConfiguredAndPreviewDoesNotPublishIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val couple = UUID.randomUUID(); val user = UUID.randomUUID()
        SyncSession.save(context, "isolated-occasion-fixture", couple, user, "http://127.0.0.1:1")
        compose.setContent { TwoMemoryTheme { AppNavigation() } }
        compose.onNodeWithContentDescription("我们").performClick()
        compose.onNodeWithText("纪念日与倒计时").performClick()
        compose.onNodeWithText("生日").performClick()
        compose.onNode(hasText("卡片标题") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(""))).performTextInput("今天是你的生日")
        compose.onNode(hasText("想说的话") and hasSetTextAction()).performTextInput("希望你每天都开心")
        compose.onNodeWithText("预览").performScrollTo().performClick()
        compose.onNode(hasText("今天是你的生日") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasContentDescription("生日小蛋糕") and hasAnyAncestor(isDialog())).assertExists()
        capture("birthday-v25")
        compose.onNodeWithText("把心意收好").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(OccasionCloud.store(context, SyncSession.load(context)!!).cards().isEmpty()) }
    }
    @Test fun sameDayCardsAreOnePagerAndClosingPersistsForToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val couple = UUID.randomUUID(); val user = UUID.randomUUID()
        SyncSession.save(context, "isolated-occasion-fixture", couple, user, "http://127.0.0.1:1")
        val store = OccasionCloud.store(context, SyncSession.load(context)!!)
        val today = java.time.LocalDate.now()
        val card = OccasionCard(UUID.randomUUID().toString(), couple.toString(), user.toString(), "BIRTHDAY", "生日快乐", "今天也爱你", today.toString(), true, null, null, "")
        store.replace(listOf(card, card.copy(id = UUID.randomUUID().toString(), kind = "ANNIVERSARY", title = "相遇的日子")))
        compose.setContent { TwoMemoryTheme { AppNavigation() } }
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithText("下一页").performClick()
        compose.onNodeWithText("相遇的日子").assertExists()
        compose.onNodeWithText("把心意收好").performClick()
        compose.onNodeWithText("把心意收好").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertTrue(OccasionCloud.store(context, SyncSession.load(context)!!).due(user.toString(), today).isEmpty()) }
    }
    @Test fun switchingAccountsCannotReuseAnotherRecipientsOpenGreeting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val couple = UUID.randomUUID(); val a = UUID.randomUUID(); val b = UUID.randomUUID()
        val first = SyncSession.Session("isolated-account-a", couple, a, "http://127.0.0.1:1")
        val second = first.copy(userId = b, token = "isolated-account-b")
        val card = OccasionCard(UUID.randomUUID().toString(), couple.toString(), b.toString(), "SURPRISE", "只给第一位的心意", "", java.time.LocalDate.now().toString(), false, a.toString(), null, "")
        OccasionCloud.store(context, first).replace(listOf(card))
        OccasionCloud.store(context, second).replace(listOf(card))
        SyncSession.save(context, first.token, couple, a, first.baseUrl)
        compose.setContent { TwoMemoryTheme { AppNavigation() } }
        compose.onNodeWithText("把心意收好").assertExists()
        compose.runOnIdle { SyncSession.save(context, second.token, couple, b, second.baseUrl) }
        compose.waitForIdle()
        compose.onNodeWithText("把心意收好").assertDoesNotExist()
    }
    private fun capture(name: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa")!!
        folder.mkdirs()
        java.io.File(folder, "$name.png").outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
