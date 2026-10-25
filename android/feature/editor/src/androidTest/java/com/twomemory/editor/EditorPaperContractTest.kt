package com.twomemory.editor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.model.BlockType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditorPaperContractTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun existingTitleCanBeLockedWithoutInventingPartnerContent() {
        composeRule.setContent {
            TwoMemoryTheme { SharedEditorRoute(
                state = EditorUiState(title = "共同回忆", ownPublishedBody = "我已写过这段"),
                onPublish = {}, ownName = "小满", titleReadOnly = true,
            ) }
        }
        composeRule.onNodeWithText("共同回忆").assert(!hasSetTextAction())
        composeRule.onNodeWithText("我已写过这段").assert(!hasSetTextAction())
        composeRule.onNodeWithText("我已写下的内容 · 只读").assertIsDisplayed()
        composeRule.onNodeWithText("伴侣", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("共同记录补写正文").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString("")))
        capture("editor-own-append-v9.png")
    }

    @Test fun moreMenuExecutesRealDraftAndImportActions() {
        var saved = 0
        var imported = 0
        composeRule.setContent {
            TwoMemoryTheme { EditorAttachmentToolbar(onSaveDraft = { saved++ }, onImportAudio = { imported++ }) }
        }
        composeRule.onNodeWithContentDescription("添加更多").performClick()
        composeRule.onNodeWithText("导入音频文件").performClick()
        composeRule.onNodeWithContentDescription("添加更多").performClick()
        composeRule.onNodeWithText("存为草稿").performClick()
        assertEquals(1, imported)
        assertEquals(1, saved)
    }

    @Test fun sixToolsHaveEqualSlotsAndUsableTouchTargets() {
        composeRule.setContent {
            TwoMemoryTheme {
                EditorAttachmentToolbar(onImageClick = {}, onVideoClick = {}, onAudioClick = {},
                    onMusicAdded = { _, _, _ -> }, onCitySelected = {})
            }
        }
        val nodes = listOf("图片", "视频", "语音", "音乐", "城市", "更多").map {
            composeRule.onNodeWithContentDescription("添加$it").assertIsDisplayed().fetchSemanticsNode()
        }
        val widths = nodes.map { it.boundsInRoot.width }
        assertTrue(widths.max() - widths.min() < 2f)
        // Semantics bounds are physical pixels; compare against the rule's density.
        val minimum = with(composeRule.density) { androidx.compose.ui.unit.Dp(48f).toPx() }
        nodes.forEach { assertTrue(it.boundsInRoot.height >= minimum - 1f) }
        nodes.zipWithNext().forEach { (left, right) ->
            assertTrue(left.boundsInRoot.center.x < right.boundsInRoot.center.x)
        }
    }

    @Test fun musicWithOnlyNameAndArtistCanJoinThePage() {
        var added: Triple<String, String, String>? = null
        composeRule.setContent {
            TwoMemoryTheme { EditorAttachmentToolbar(onMusicAdded = { title, artist, url ->
                added = Triple(title, artist, url)
            }) }
        }
        composeRule.onNodeWithContentDescription("添加音乐").performClick()
        composeRule.onNodeWithText("歌曲名称").performTextInput("晴天")
        composeRule.onNodeWithText("歌手 / 专辑").performTextInput("周杰伦")
        composeRule.onNodeWithText("加入这一页").assertIsEnabled().performClick()
        assertEquals(Triple("晴天", "周杰伦", ""), added)
    }

    @Test fun selectedLocationIsVisibleAboveToolsAndCanBeRemoved() {
        val id = UUID.randomUUID()
        var removed: UUID? = null
        composeRule.setContent {
            TwoMemoryTheme {
                PersonalEditorScreen(
                    state = EditorUiState(attachments = listOf(EditorAttachment(id,
                        BlockType.LOCATION, payload = "{\"city\":\"杭州\"}"))),
                    onTitleChange = {}, onBodyChange = {}, onPublish = {},
                    attachmentActions = EditorAttachmentActions({}, {}, { _, _, _ -> }, {}, { removed = it }),
                )
            }
        }
        val city = composeRule.onNodeWithText("杭州").assertIsDisplayed().fetchSemanticsNode()
        val tool = composeRule.onNodeWithContentDescription("添加城市").fetchSemanticsNode()
        assertTrue(city.boundsInRoot.bottom <= tool.boundsInRoot.top)
        composeRule.onNodeWithContentDescription("移除地点杭州").assertIsDisplayed().performClick()
        assertEquals(id, removed)
    }

    @Test fun sharedWritingStartsFromRealPartnerContextBeforeEditablePerspective() {
        composeRule.setContent {
            TwoMemoryTheme { SharedEditorRoute(
                state = EditorUiState(body = "我的补写", partnerName = "阿屿", partnerBody = "雨停后见到彩虹"),
                onPublish = {}, ownName = "小满",
            ) }
        }
        val partner = composeRule.onNodeWithText("雨停后见到彩虹").fetchSemanticsNode()
        val own = composeRule.onNodeWithText("我的补写").fetchSemanticsNode()
        assertTrue(partner.boundsInRoot.top < own.boundsInRoot.top)
        composeRule.onNodeWithText("雨停后见到彩虹").assert(!hasSetTextAction())
        capture("editor-shared-v9.png")
    }

    @Test fun existingSharedContextMakesOwnSectionAnExplicitEmptyAppend() {
        composeRule.setContent {
            TwoMemoryTheme { SharedEditorRoute(
                state = EditorUiState(title = "雨后的散步", partnerName = "阿屿", partnerBody = "雨停后见到彩虹", appendingPerspective = true),
                onPublish = {}, ownName = "小满",
            ) }
        }
        composeRule.onNodeWithText("补写我的视角").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("共同记录补写正文").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString("")))
        composeRule.onNodeWithText("雨停后见到彩虹").assert(!hasSetTextAction())
    }

    private fun capture(name: String) {
        val directory = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("visual-qa"))
        File(directory, name).outputStream().use {
            assertTrue(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
