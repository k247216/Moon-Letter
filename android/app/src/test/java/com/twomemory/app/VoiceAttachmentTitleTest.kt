package com.twomemory.app

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.room.Room
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.editor.EditorAttachment
import com.twomemory.editor.EditorAttachmentActions
import com.twomemory.editor.EditorViewModel
import com.twomemory.editor.EditorUiState
import com.twomemory.editor.PersonalEditorScreen
import com.twomemory.model.BlockType
import com.twomemory.model.EntryMode
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceAttachmentTitleTest {
    @Test fun publishedCommandCarriesTheRenamedAudioTitleThroughTheRealBlockTransformAndOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val voice = audio(payload = """{"mime":"audio/mp4","duration":12,"custom":{"source":"recorder"}}""")
        val model = restored(voice)
        model.renameAudio(voice.id, "送给你的晚安")
        val state = model.state.value
        val author = UUID.randomUUID()
        try {
            // Exercise the same private block conversion used by SyncSession.publish;
            // do not duplicate it in a test builder or start background HTTP sync.
            val transform = SyncSession::class.java.declaredMethods.single {
                it.name == "blocks" && it.parameterTypes.contentEquals(arrayOf(EditorUiState::class.java, UUID::class.java))
            }.apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val blocks = transform.invoke(SyncSession, state, author) as List<LocalBlockCommand>
            val command = LocalEntryCommand(UUID.randomUUID(), author, EntryMode.PERSONAL,
                state.occurrenceTime, state.timezone, blocks = blocks)
            val block = command.blocks.single()
            val payload = JSONObject(block.payload)
            assertEquals(voice.id, block.id)
            assertEquals(BlockType.AUDIO, block.type)
            assertEquals(author, block.authorId)
            assertEquals("送给你的晚安", payload.getString("title"))
            assertEquals(voice.localPath, payload.getString("localPath"))
            assertEquals("audio/mp4", payload.getString("mime"))
            assertEquals(12, payload.getInt("duration"))
            assertEquals("recorder", payload.getJSONObject("custom").getString("source"))

            LocalEntryWriter(database).save(command, publish = true)

            val stored = database.entryDao().blocks(command.entryId.toString()).single()
            assertEquals("送给你的晚安", JSONObject(stored.payload).getString("title"))
            assertEquals(voice.id.toString(), stored.id)
            val create = database.outboxDao().observeForEntity(command.entryId.toString()).first()
                .single { it.action == "CREATE_ENTRY" }
            val wireBlock = JSONObject(create.payload).getJSONArray("blocks").getJSONObject(0)
            assertEquals(voice.id.toString(), wireBlock.getString("blockId"))
            assertEquals("AUDIO", wireBlock.getString("type"))
            val wire = JSONObject(wireBlock.getString("payload"))
            assertEquals("送给你的晚安", wire.getString("title"))
            assertEquals("audio/mp4", wire.getString("mime"))
            assertFalse("Device file paths must not travel in the cloud payload", wire.has("localPath"))
        } finally { database.close() }
    }

    @Test fun renamingAudioPreservesItsIdentityFileMimeAndUnknownPayloadFields() {
        val audio = audio(payload = """{"mime":"audio/mp4","duration":12,"title":"旧标题","custom":{"source":"recorder","flags":[1,2]},"nullable":null}""")
        val other = audio(payload = """{"title":"另一段声音","mime":"audio/mp4"}""")
        val model = restored(audio, other)

        rename(model, audio.id, "回家路上的声音")

        val renamed = model.state.value.attachments.first()
        assertEquals(audio.id, renamed.id)
        assertEquals(audio.type, renamed.type)
        assertEquals(audio.localPath, renamed.localPath)
        assertEquals(audio.mimeType, renamed.mimeType)
        val payload = JSONObject(renamed.payload)
        assertEquals("回家路上的声音", payload.getString("title"))
        assertEquals("audio/mp4", payload.getString("mime"))
        assertEquals(12, payload.getInt("duration"))
        assertEquals("recorder", payload.getJSONObject("custom").getString("source"))
        assertEquals("[1,2]", payload.getJSONObject("custom").getJSONArray("flags").toString())
        assertTrue(payload.has("nullable"))
        assertTrue(payload.isNull("nullable"))
        assertEquals(other, model.state.value.attachments.last())
    }

    @Test fun eightyCodePointsKeepWholeEmojiRatherThanEightyUtf16Units() {
        val audio = audio()
        val model = restored(audio)
        val emoji = "\uD83C\uDF19"

        rename(model, audio.id, emoji.repeat(81))

        val title = JSONObject(model.state.value.attachments.single().payload).getString("title")
        assertEquals(emoji.repeat(80), title)
        assertEquals(80, title.codePointCount(0, title.length))
    }

    @Test fun mixedTextIsCappedAtTheEightiethCompleteCodePoint() {
        val audio = audio()
        val model = restored(audio)

        rename(model, audio.id, "声".repeat(79) + "\uD83C\uDF19" + "尾巴")

        assertEquals("声".repeat(79) + "\uD83C\uDF19",
            JSONObject(model.state.value.attachments.single().payload).getString("title"))
    }

    @Test fun clearingAnAudioTitleKeepsTheRecordingAttached() {
        val audio = audio(payload = """{"mime":"audio/mp4","title":"旧标题","duration":12}""")
        val model = restored(audio)

        rename(model, audio.id, "")

        val renamed = model.state.value.attachments.single()
        assertEquals(audio.id, renamed.id)
        assertEquals(audio.localPath, renamed.localPath)
        assertEquals("", JSONObject(renamed.payload).optString("title"))
        assertEquals(12, JSONObject(renamed.payload).getInt("duration"))
    }

    @Test fun nonAudioAndMissingIdsDoNotAlterTheDraft() {
        val video = audio().copy(type = BlockType.VIDEO)
        val audio = audio()
        val model = restored(video, audio)
        val before = model.state.value

        rename(model, video.id, "不能改视频")
        rename(model, UUID.randomUUID(), "不存在")

        assertEquals(before, model.state.value)
    }

    @Test fun malformedLegacyPayloadIsNotSilentlyReplacedWithOnlyATitle() {
        val audio = audio(payload = "legacy-unparseable-payload")
        val model = restored(audio)

        rename(model, audio.id, "新的声音标题")

        assertEquals(audio, model.state.value.attachments.single())
        assertNotNull("Malformed legacy audio must report why renaming was refused", model.state.value.error)
    }

    @Test fun renamedTitleSurvivesDraftSaveAndRestoreWithoutRewritingTheMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = "voice-title-${UUID.randomUUID()}"
        val draftId = UUID.randomUUID().toString()
        val audio = audio(payload = """{"mime":"audio/mp4","duration":12}""")
        val model = restored(audio)
        val library = DraftLibrary(context, scope)

        rename(model, audio.id, "今天想说的话")
        library.save(draftId, EntryMode.PERSONAL,
            DraftStore.Draft("", "", attachments = model.state.value.attachments))
        try {
            val reopened = DraftLibrary(context, scope).load(draftId)!!.draft
            val restored = EditorViewModel()
            restored.restore("", "", savedAttachments = reopened.attachments)
            val attachment = restored.state.value.attachments.single()
            assertEquals(audio.id, attachment.id)
            assertEquals(audio.localPath, attachment.localPath)
            assertEquals(audio.mimeType, attachment.mimeType)
            assertEquals("今天想说的话", JSONObject(attachment.payload).getString("title"))
            assertEquals(12, JSONObject(attachment.payload).getInt("duration"))
        } finally {
            library.remove(draftId)
        }
    }

    private fun audio(payload: String = """{"mime":"audio/mp4"}""") = EditorAttachment(
        UUID.randomUUID(), BlockType.AUDIO, "/synthetic/voice.m4a", "audio/mp4", payload)

    private fun restored(vararg attachments: EditorAttachment) = EditorViewModel().also {
        it.restore("", "", savedAttachments = attachments.toList())
    }

    private fun rename(model: EditorViewModel, id: UUID, title: String) {
        model.renameAudio(id, title)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceAttachmentTitleScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editingTheAudioCardTitleRenamesOnlyThatRecording() {
        val voice = EditorAttachment(UUID.randomUUID(), BlockType.AUDIO,
            "/synthetic/voice.m4a", "audio/mp4", """{"mime":"audio/mp4","duration":12}""")
        val other = voice.copy(id = UUID.randomUUID(), localPath = "/synthetic/other.m4a",
            payload = """{"mime":"audio/mp4","title":"另一段声音"}""")
        val model = EditorViewModel().also { it.restore("", "", savedAttachments = listOf(voice, other)) }
        val callbackIds = mutableListOf<UUID>()
        val actions = EditorAttachmentActions(pickVideo = {}, pickAudio = {}, addMusic = { _, _, _ -> },
            addCity = {}, remove = model::removeAttachment, renameAudio = { id, title ->
                callbackIds += id
                model.renameAudio(id, title)
            })
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            PersonalEditorScreen(model.state.collectAsState().value,
                onTitleChange = model::updateTitle, onBodyChange = model::updateBody,
                onPublish = {}, attachmentActions = actions)
        } } }

        compose.onNodeWithContentDescription("语音标题：${voice.id}").performScrollTo()
            .performTextReplacement("晚安，明天见")
        compose.runOnIdle {
            assertTrue("Typing must invoke the real attachment action", callbackIds.isNotEmpty())
            assertTrue(callbackIds.all { it == voice.id })
            val renamed = model.state.value.attachments.first()
            assertEquals("晚安，明天见", JSONObject(renamed.payload).getString("title"))
            assertEquals(12, JSONObject(renamed.payload).getInt("duration"))
            assertEquals(voice.id, renamed.id)
            assertEquals(voice.localPath, renamed.localPath)
            assertEquals(voice.mimeType, renamed.mimeType)
            assertEquals(other, model.state.value.attachments.last())
        }
        compose.onNodeWithContentDescription("语音标题：${voice.id}").assertTextEquals("晚安，明天见")
    }

    @Test fun audioPayloadTitleIsVisibleReadOnlyWhenNoRenameCallbackIsSupplied() {
        val voice = EditorAttachment(UUID.randomUUID(), BlockType.AUDIO,
            payload = """{"mime":"audio/mp4","title":"回家的声音"}""")
        compose.activity.runOnUiThread { compose.activity.setContent { TwoMemoryTheme {
            PersonalEditorScreen(EditorUiState(attachments = listOf(voice)),
                onTitleChange = {}, onBodyChange = {}, onPublish = {})
        } } }

        compose.onNodeWithText("回家的声音").performScrollTo().assertIsDisplayed()
            .assert(!hasSetTextAction())
    }
}
