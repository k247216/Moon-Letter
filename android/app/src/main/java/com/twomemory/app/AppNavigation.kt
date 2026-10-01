package com.twomemory.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import com.twomemory.couple.CoupleRoute
import com.twomemory.couple.CoupleViewModel
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.MoonLetterBottomNavigation
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.editor.EditorViewModel
import com.twomemory.editor.PersonalEditorScreen
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

    fun save(context: android.content.Context, title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("title", title)
            .putString("body", body)
            .apply()
    }

    fun load(context: android.content.Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val title = prefs.getString("title", null).orEmpty()
        val body = prefs.getString("body", null).orEmpty()
        if (title.isBlank() && body.isBlank()) return null
        return title to body
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
    var editing by remember { mutableStateOf(false) }
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
    val coupleViewModel = remember { CoupleViewModel() }
    val editorViewModel = remember { EditorViewModel() }
    val editorState by editorViewModel.state.collectAsState()

    // Unpublished text is kept off the critical path: restore on entry, keep
    // on every exit except a successful save.
    LaunchedEffect(editorState.saved) {
        if (editorState.saved) {
            DraftStore.clear(context)
            editing = false
        }
    }

    if (editing) {
        LaunchedEffect(Unit) {
            if (editorState.saved) editorViewModel.reset()
            DraftStore.load(context)?.let { (title, body) -> editorViewModel.restore(title, body) }
        }
        BackHandler {
            DraftStore.save(context, editorState.title, editorState.body)
            editing = false
        }
        DisposableEffect(Unit) {
            onDispose {
                if (!editorState.saved) DraftStore.save(context, editorState.title, editorState.body)
            }
        }
        PersonalEditorScreen(
            state = editorState,
            onTitleChange = editorViewModel::updateTitle,
            onBodyChange = editorViewModel::updateBody,
            onPublish = { editorViewModel.publish { state -> SyncSession.saveDraft(context, state) } },
        )
        return
    }

    Scaffold(
        bottomBar = {
            MoonLetterBottomNavigation(selectedKey) { tab ->
                if (tab.key == "create") editing = true else selectedKey = tab.key
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedKey) {
                "timeline" -> TimelineRoute(timelineViewModel)
                "couple" -> CoupleRoute(coupleViewModel, onThemeChange)
                "album" -> PlaceholderRoute("相册 · M2 功能准备中")
                "map" -> PlaceholderRoute("地图 · M2 功能准备中")
            }
        }
    }
}

@Composable
private fun PlaceholderRoute(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
