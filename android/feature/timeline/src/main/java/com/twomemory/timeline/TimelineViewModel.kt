package com.twomemory.timeline

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimelineEntryUi(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val author: String,
    val body: String,
    val shared: Boolean = false,
)

class TimelineViewModel : ViewModel() {
    private val mutableEntries = MutableStateFlow(
        listOf(
            TimelineEntryUi("1", "2026年9月30日", "20:18", "我", "今天也有好好生活。"),
            TimelineEntryUi("2", "2026年9月29日", "22:06", "我们", "路过熟悉的街角，一起买了月饼。", shared = true),
        ),
    )
    val entries: StateFlow<List<TimelineEntryUi>> = mutableEntries.asStateFlow()
}
