package com.twomemory.app

import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryBlockEntity
import com.twomemory.database.EntryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class LocalExportDocument(
    val markdown: String,
    val json: String,
)

/**
 * Builds a useful text export from the rows this device can actually read.
 * It deliberately strips private file paths; original binary media remains a
 * separate server/export concern rather than leaking an unusable local path.
 */
object LocalExportBuilder {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

    suspend fun build(
        database: AppDatabase,
        currentUserId: UUID? = null,
        ownName: String = "",
        partnerName: String = "",
    ): LocalExportDocument {
        val entries = database.entryDao().publishedEntries()
        val jsonRecords = JSONArray()
        val markdown = buildString {
            appendLine("# 月笺本机缓存导出")
            appendLine()
            appendLine("> 仅包含这台设备当前能读取的已发布记录；不含登录令牌、配对密钥或私有文件路径。")
            appendLine("> 原始图片/视频文件不通过文本分享打包，需使用完整服务端导出。")
            appendLine()
            entries.forEach { entry ->
                val blocks = database.entryDao().blocks(entry.id)
                val comments = database.commentDao().commentsForEntry(entry.id)
                val whenText = occurredAt(entry)
                val author = authorName(entry.authorId, currentUserId, ownName, partnerName)
                appendLine("## ${entry.title.orEmpty().ifBlank { "未命名记录" }}")
                appendLine("- 发生时间：$whenText")
                appendLine("- 作者：$author")
                appendLine("- 记录 ID：${entry.id}")
                appendLine()
                blocks.sortedBy { it.orderKey }.forEach { block ->
                    appendLine("### ${blockLabel(block.type)} · ${authorName(block.authorId, currentUserId, ownName, partnerName)}")
                    appendLine(markdownBody(block))
                    appendLine()
                }
                if (comments.isNotEmpty()) {
                    appendLine("### 回应")
                    comments.forEach { comment ->
                        appendLine("- ${authorName(comment.authorId, currentUserId, ownName, partnerName)} · ${occurredAt(comment.createdAtEpochMillis, entry.occurredTimezone)}：${comment.body}")
                    }
                    appendLine()
                }
                jsonRecords.put(recordJson(entry, blocks, comments, currentUserId, ownName, partnerName))
            }
        }
        val json = JSONObject()
            .put("format", "moon-letter-local-cache-v1")
            .put("records", jsonRecords)
            .toString(2)
        return LocalExportDocument(markdown = markdown, json = json)
    }

    private fun recordJson(
        entry: EntryEntity,
        blocks: List<EntryBlockEntity>,
        comments: List<com.twomemory.database.CommentEntity>,
        currentUserId: UUID?,
        ownName: String,
        partnerName: String,
    ) = JSONObject().apply {
        put("id", entry.id)
        put("title", entry.title ?: JSONObject.NULL)
        put("authorId", entry.authorId)
        put("author", authorName(entry.authorId, currentUserId, ownName, partnerName))
        put("occurredAt", occurredAt(entry))
        put("timezone", entry.occurredTimezone)
        put("state", entry.state)
        put("blocks", JSONArray().apply { blocks.sortedBy { it.orderKey }.forEach { put(blockJson(it, currentUserId, ownName, partnerName)) } })
        put("comments", JSONArray().apply {
            comments.forEach { comment ->
                put(JSONObject()
                    .put("id", comment.id)
                    .put("authorId", comment.authorId)
                    .put("author", authorName(comment.authorId, currentUserId, ownName, partnerName))
                    .put("createdAt", occurredAt(comment.createdAtEpochMillis, entry.occurredTimezone))
                    .put("body", comment.body))
            }
        })
    }

    private fun blockJson(
        block: EntryBlockEntity,
        currentUserId: UUID?,
        ownName: String,
        partnerName: String,
    ) = JSONObject().apply {
        put("id", block.id)
        put("type", block.type)
        put("orderKey", block.orderKey)
        put("authorId", block.authorId)
        put("author", authorName(block.authorId, currentUserId, ownName, partnerName))
        put("assetId", block.assetId ?: JSONObject.NULL)
        put("payload", safePayload(block))
    }

    private fun safePayload(block: EntryBlockEntity): JSONObject = runCatching {
        JSONObject(block.payload).apply { remove("localPath") }
    }.getOrElse {
        JSONObject().put("value", block.payload)
    }

    private fun markdownBody(block: EntryBlockEntity): String {
        val payload = runCatching { JSONObject(block.payload) }.getOrNull()
        return when (block.type) {
            "TEXT" -> payload?.optString("text").orEmpty().ifBlank { block.payload }
            "IMAGE" -> "照片（${if (block.assetId == null) "本机待上传" else "已有关联媒体"}）"
            "VIDEO" -> "视频（${if (block.assetId == null) "等待媒体同步" else "已有关联媒体"}）"
            "AUDIO" -> "语音（${if (block.assetId == null) "等待媒体同步" else "已有关联媒体"}）"
            "MUSIC" -> payload?.optString("url").orEmpty().ifBlank { payload?.optString("link").orEmpty() }.ifBlank { "音乐分享" }
            "LOCATION" -> payload?.optString("city").orEmpty().ifBlank { payload?.optString("cityName").orEmpty() }.ifBlank { "城市快照" }
            else -> "${block.type} 内容"
        }
    }

    private fun blockLabel(type: String) = when (type) {
        "TEXT" -> "文字"
        "IMAGE" -> "照片"
        "VIDEO" -> "视频"
        "AUDIO" -> "语音"
        "MUSIC" -> "音乐"
        "LOCATION" -> "城市"
        else -> type
    }

    private fun authorName(id: String, currentUserId: UUID?, ownName: String, partnerName: String): String = when {
        currentUserId?.toString() == id -> ownName.ifBlank { "我" }
        partnerName.isNotBlank() -> partnerName
        else -> "伴侣"
    }

    private fun occurredAt(entry: EntryEntity): String =
        occurredAt(entry.occurredAtEpochMillis, entry.occurredTimezone)

    private fun occurredAt(epochMillis: Long, timezone: String): String {
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("UTC") }
        return Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateFormatter)
    }
}
