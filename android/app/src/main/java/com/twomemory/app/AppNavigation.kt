package com.twomemory.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.twomemory.couple.CoupleRoute
import com.twomemory.couple.CoupleViewModel
import com.twomemory.designsystem.MoonLetterBottomNavigation
import com.twomemory.designsystem.MoonLetterTheme
import com.twomemory.editor.EditorUiState
import com.twomemory.editor.PersonalEditorScreen
import com.twomemory.timeline.TimelineRoute
import com.twomemory.timeline.TimelineViewModel

@Composable
fun AppNavigation(onThemeChange: (MoonLetterTheme) -> Unit = {}) {
    var selectedKey by remember { mutableStateOf("timeline") }
    var editing by remember { mutableStateOf(false) }
    val timelineViewModel = remember { TimelineViewModel() }
    val coupleViewModel = remember { CoupleViewModel() }

    if (editing) {
        BackHandler { editing = false }
        PersonalEditorScreen(
            state = EditorUiState(),
            onTitleChange = {},
            onBodyChange = {},
            onPublish = { editing = false },
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
