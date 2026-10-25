package com.twomemory.app

enum class MemoryReviewRoute { PAST_TODAY, WEEKLY_SUMMARY }

data class ReviewMemoryUi(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val author: String,
    val title: String?,
    val body: String,
    val mediaKinds: List<String> = emptyList(),
    val city: String? = null,
    val date: java.time.LocalDate? = null,
    val blocks: List<com.twomemory.timeline.EntryBlockUi> = emptyList(),
    val shared: Boolean = false,
)
