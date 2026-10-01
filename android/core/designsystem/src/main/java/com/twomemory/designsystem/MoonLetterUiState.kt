package com.twomemory.designsystem

/** User-visible persistence/sync state for a record. */
enum class MoonLetterRecordStatus(val label: String) {
    DRAFT("草稿"),
    LOCAL_SAVED("已保存到本机"),
    PENDING_SYNC("等待同步"),
    UPLOADING_MEDIA("照片还在上传"),
    RETRYING("发送没成功，正在自动重试"),
    REJECTED("服务器拒收了这条"),
    SYNCED("服务器已收下"),
    LOCAL_WRITE_FAILED("这条还没存进手机"),
}
