package com.twomemory.designsystem

/** User-visible persistence/sync state for a record. */
enum class MoonLetterRecordStatus(val label: String) {
    DRAFT("草稿"),
    LOCAL_SAVED("已保存到本机"),
    PENDING_SYNC("等待同步"),
    SYNCED("已同步"),
    SYNC_FAILED("同步失败，点击重试"),
}
