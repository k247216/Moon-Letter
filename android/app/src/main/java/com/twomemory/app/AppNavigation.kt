package com.twomemory.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.twomemory.couple.CoupleRoute
import com.twomemory.couple.CoupleViewModel
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.MoonLetterBottomNavigation
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorViewModel
import com.twomemory.editor.PersonalEditorScreen
import com.twomemory.editor.SharedEditorRoute
import com.twomemory.model.EntryMode
import com.twomemory.model.EntryState
import com.twomemory.model.TimelineItem
import com.twomemory.timeline.TimelineRoute
import com.twomemory.timeline.TimelineViewModel
import org.json.JSONObject

private fun com.twomemory.database.EntryEntity.toTimelineItem(preview: String?) = TimelineItem(
    id = java.util.UUID.fromString(id),
    coupleId = java.util.UUID.fromString(coupleId),
    mode = EntryMode.valueOf(mode),
    state = EntryState.valueOf(state),
    occurredAt = java.time.Instant.ofEpochMilli(occurredAtEpochMillis),
    occurredTimezone = occurredTimezone,
    title = title,
    authorId = java.util.UUID.fromString(authorId),
    preview = preview,
)

private fun previewOf(type: String, payload: String): String? = when (type) {
    "TEXT" -> runCatching { JSONObject(payload).optString("text", payload) }.getOrNull() ?: payload
    "IMAGE" -> "[图片]"
    "VIDEO" -> "[视频]"
    "AUDIO" -> "[语音]"
    "MUSIC" -> "[音乐]"
    "LOCATION" -> "[位置]"
    else -> null
}

/** Unpublished editor text survives process death through SharedPreferences. */
object DraftStore {
    private const val PREFS = "moon_letter_draft"

    fun save(
        context: android.content.Context,
        title: String,
        body: String,
        mode: EntryMode = EntryMode.PERSONAL,
    ) {
        if (title.isBlank() && body.isBlank()) return
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("title", title)
            .putString("body", body)
            .putString("mode", mode.name)
            .apply()
    }

    data class Draft(val title: String, val body: String, val mode: EntryMode)

    fun load(context: android.content.Context): Draft? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val title = prefs.getString("title", null).orEmpty()
        val body = prefs.getString("body", null).orEmpty()
        if (title.isBlank() && body.isBlank()) return null
        val mode = runCatching {
            EntryMode.valueOf(prefs.getString("mode", EntryMode.PERSONAL.name).orEmpty())
        }.getOrDefault(EntryMode.PERSONAL)
        return Draft(title, body, mode)
    }

    fun clear(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
fun AppNavigation(onThemeChange: (MoonLetterTheme) -> Unit = {}) {
    val context = LocalContext.current
    var bound by remember { mutableStateOf(SyncSession.load(context) != null) }
    if (!bound) {
        TwoMemoryTheme {
            SetupScreen(onBound = { bound = true })
        }
        return
    }
    var selectedKey by remember { mutableStateOf("timeline") }
    var editingMode by remember { mutableStateOf<EntryMode?>(null) }
    val visualPrefs = remember { context.getSharedPreferences("moon_letter_visuals", android.content.Context.MODE_PRIVATE) }
    var coverUri by remember { mutableStateOf(visualPrefs.getString("coverUri", null)) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            coverUri = uri.toString()
            visualPrefs.edit().putString("coverUri", uri.toString()).apply()
        }
    }
    val coverBitmap = remember(coverUri) {
        coverUri?.let { value ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val coupleViewModel = remember { CoupleViewModel() }
    val coupleState by coupleViewModel.state.collectAsState()
    val timelineViewModel = remember {
        val dao = AppDatabase.build(context).entryDao()
        TimelineViewModel(
            loader = {
                dao.timelineSnapshot().map { entity ->
                    val preview = dao.blocks(entity.id)
                        .firstNotNullOfOrNull { previewOf(it.type, it.payload) }
                    entity.toTimelineItem(preview)
                }
            },
            currentUserId = SyncSession.load(context)?.userId,
        )
    }
    val editorViewModel = remember { EditorViewModel() }
    val editorState by editorViewModel.state.collectAsState()

    LaunchedEffect(coupleState.ownName, coupleState.partnerName) {
        timelineViewModel.updateNames(coupleState.ownName, coupleState.partnerName)
    }

    // Unpublished text is kept off the critical path: restore on entry, keep
    // on every exit except a successful save.
    LaunchedEffect(editorState.saved) {
        if (editorState.saved) {
            DraftStore.clear(context)
            editingMode = null
            timelineViewModel.refresh()
        }
    }

    if (selectedKey == "timeline" && editingMode == null) {
        LaunchedEffect(selectedKey, editingMode) { timelineViewModel.refresh() }
    }

    editingMode?.let { mode ->
        LaunchedEffect(Unit) {
            if (editorState.saved) editorViewModel.reset()
            DraftStore.load(context)?.let { draft ->
                editingMode = draft.mode
                editorViewModel.restore(draft.title, draft.body)
            }
        }
        BackHandler {
            DraftStore.save(context, editorState.title, editorState.body, mode)
            editingMode = null
        }
        DisposableEffect(mode) {
            onDispose {
                if (!editorState.saved) DraftStore.save(context, editorState.title, editorState.body, mode)
            }
        }
        val closeEditor = {
            DraftStore.save(context, editorState.title, editorState.body, mode)
            editingMode = null
        }
        if (mode == EntryMode.PERSONAL) {
            PersonalEditorScreen(
                state = editorState,
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onPublish = { editorViewModel.publish { state -> SyncSession.saveDraft(context, state, mode) } },
                onClose = closeEditor,
                mode = mode,
                onModeChange = { editingMode = it },
            )
        } else {
            SharedEditorRoute(
                state = editorState,
                blocks = emptyList(),
                onPublish = { editorViewModel.publish { state -> SyncSession.saveDraft(context, state, mode) } },
                onTitleChange = editorViewModel::updateTitle,
                onBodyChange = editorViewModel::updateBody,
                onClose = closeEditor,
                onModeChange = { editingMode = it },
            )
        }
        return
    }

    Scaffold(
        bottomBar = {
            MoonLetterBottomNavigation(selectedKey) { tab ->
                if (tab.key == "create") {
                    editorViewModel.reset()
                    editingMode = DraftStore.load(context)?.mode ?: EntryMode.PERSONAL
                } else {
                    selectedKey = tab.key
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedKey) {
                "timeline" -> TimelineRoute(
                    viewModel = timelineViewModel,
                    coverBitmap = coverBitmap,
                    onChangeCover = { coverPicker.launch(arrayOf("image/*")) },
                )
                "couple" -> CoupleRoute(coupleViewModel, onThemeChange)
                "album" -> AlbumPreviewScreen()
                "map" -> CityMapPreviewScreen()
            }
        }
    }
}
