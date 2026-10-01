package com.twomemory.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private fun com.twomemory.database.EntryEntity.toTimelineItem() = TimelineItem(
    id = java.util.UUID.fromString(id),
    coupleId = java.util.UUID.fromString(coupleId),
    mode = EntryMode.valueOf(mode),
    state = EntryState.valueOf(state),
    occurredAt = java.time.Instant.ofEpochMilli(occurredAtEpochMillis),
    occurredTimezone = occurredTimezone,
    title = title,
)

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
        TimelineViewModel(loader = { dao.timelineSnapshot().map { entity -> entity.toTimelineItem() } })
    }
    val coupleViewModel = remember { CoupleViewModel() }
    val editorViewModel = remember { EditorViewModel() }

    // Pulled changes land in Room in the background; re-read the snapshot
    // whenever the user returns to the timeline tab.
    LaunchedEffect(selectedKey) {
        if (selectedKey == "timeline") timelineViewModel.refresh()
    }

    if (editing) {
        BackHandler { editing = false }
        PersonalEditorScreen(
            state = editorViewModel.state.collectAsState().value,
            onTitleChange = editorViewModel::updateTitle,
            onBodyChange = editorViewModel::updateBody,
            onPublish = {
                editorViewModel.publish { state -> SyncSession.saveDraft(context, state) }
                editing = false
            },
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
