package com.twomemory.app

import androidx.room.withTransaction
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
        val snapshot = database.withTransaction {
            database.entryDao().publishedEntries().map { entry ->
                ExportRecordSnapshot(
                    entry = entry,
                    blocks = database.entryDao().blocks(entry.id),
                    comments = database.commentDao().commentsForEntry(entry.id),
                )
            }
        }
        return buildSnapshot(snapshot, currentUserId, ownName, partnerName)
    }

    /** Renders only captured rows so archives can share the same database view. */
    internal fun buildSnapshot(
        snapshot: List<ExportRecordSnapshot>,
        currentUserId: UUID? = null,
        ownName: String = "",
        partnerName: String = "",
    ): LocalExportDocument {
        val markdown = buildString {
            appendLine("# 月笺本机缓存导出")
            appendLine()
            appendLine("> 仅包含这台设备当前能读取的已发布记录；不含登录令牌、配对密钥或私有文件路径。")
            appendLine("> 本文本不含原始媒体；带原文件的记录可使用「记录数据存档」，也不等于云端全量备份。")
            appendLine()
            snapshot.forEach { (entry, blocks, comments) ->
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
            }
        }
        val json = buildJsonSnapshot(snapshot, currentUserId, ownName, partnerName).toString(2)
        return LocalExportDocument(markdown = markdown, json = json)
    }

    /**
     * Builds a fresh machine-readable object directly from captured rows.
     * Archive callers can attach media paths without generating Markdown or
     * serializing and reparsing the entire JSON document first.
     */
    internal fun buildJsonSnapshot(
        snapshot: List<ExportRecordSnapshot>,
        currentUserId: UUID? = null,
        ownName: String = "",
        partnerName: String = "",
    ): JSONObject {
        val jsonRecords = JSONArray()
        snapshot.forEach { (entry, blocks, comments) ->
            jsonRecords.put(recordJson(entry, blocks, comments, currentUserId, ownName, partnerName))
        }
        return JSONObject()
            .put("format", "moon-letter-local-cache-v1")
            .put("records", jsonRecords)
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
        put("occurredAtEpochMillis", entry.occurredAtEpochMillis)
        put("timezone", entry.occurredTimezone)
        put("state", entry.state)
        put("visibility", entry.visibility)
        put("blocks", JSONArray().apply { blocks.sortedBy { it.orderKey }.forEach { put(blockJson(it, currentUserId, ownName, partnerName)) } })
        put("comments", JSONArray().apply {
            comments.forEach { comment ->
                put(JSONObject()
                    .put("id", comment.id)
                    .put("authorId", comment.authorId)
                    .put("author", authorName(comment.authorId, currentUserId, ownName, partnerName))
                    .put("createdAt", occurredAt(comment.createdAtEpochMillis, entry.occurredTimezone))
                    .put("createdAtEpochMillis", comment.createdAtEpochMillis)
                    .put("replyToId", comment.replyToId ?: JSONObject.NULL)
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

    /**
     * Export known scalar fields only. Unknown keys and nested objects/arrays
     * cannot smuggle device paths or credentials into the machine archive.
     * Text is user content: preserve it verbatim rather than redact words.
     * Invalid payloads produce an empty object, never a raw-data fallback.
     */
    internal fun safePayload(block: EntryBlockEntity): JSONObject {
        val source = runCatching { JSONObject(block.payload) }.getOrNull() ?: return JSONObject()
        val strings = when (block.type) {
            "TEXT" -> listOf("text")
            "MUSIC" -> listOf("title", "name", "artist", "album")
            "LOCATION" -> listOf("city", "cityName", "province")
            "IMAGE", "VIDEO", "AUDIO" -> listOf("title")
            else -> emptyList()
        }
        return JSONObject().apply {
            strings.forEach { key ->
                (source.opt(key) as? String)?.let { put(key, it) }
            }
            if (block.type == "MUSIC") {
                listOf("url", "link").forEach { key ->
                    (source.opt(key) as? String)?.takeIf { safeWebUrl(it) }?.let { put(key, it) }
                }
            }
            if (block.type in setOf("IMAGE", "VIDEO", "AUDIO")) {
                (source.opt("mime") as? String)?.takeIf {
                    it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
                }?.let { put("mime", it) }
            }
            val numbers = when (block.type) {
                "LOCATION" -> listOf("latitude", "longitude")
                "IMAGE", "VIDEO", "AUDIO" -> listOf("width", "height", "durationMillis", "durationMs")
                else -> emptyList()
            }
            numbers.forEach { key ->
                (source.opt(key) as? Number)?.takeIf { it.toDouble().isFinite() }?.let { put(key, it) }
            }
        }
    }

    private fun markdownBody(block: EntryBlockEntity): String {
        val payload = safePayload(block)
        return when (block.type) {
            "TEXT" -> payload.optString("text")
            "IMAGE" -> "照片（${if (block.assetId == null) "本机待上传" else "已有关联媒体"}）"
            "VIDEO" -> "视频（${if (block.assetId == null) "等待媒体同步" else "已有关联媒体"}）"
            "AUDIO" -> "语音（${if (block.assetId == null) "等待媒体同步" else "已有关联媒体"}）"
            "MUSIC" -> listOf(
                payload.optString("title").ifBlank { payload.optString("name") },
                payload.optString("artist"),
                payload.optString("url").ifBlank { payload.optString("link") },
            ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "音乐分享" }
            "LOCATION" -> payload.optString("city").ifBlank { payload.optString("cityName") }.ifBlank { "城市快照" }
            else -> "${block.type} 内容"
        }
    }

    // Keep the same credential rejection policy as MemoryBookExporter.safeWebUrl.
    private fun safeWebUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val fields = listOfNotNull(uri.rawQuery, uri.rawFragment).flatMap { it.split('&') }
        val sensitive = fields.any { field ->
            val key = java.net.URLDecoder.decode(field.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
            key.contains("token") || key.contains("secret") || key.contains("signature") ||
                key.contains("credential") || key.contains("password") || key.contains("accesskey") ||
                key.startsWith("x-amz-") || key.startsWith("x-oss-") ||
                key in setOf("auth", "authorization", "ossaccesskeyid", "awsaccesskeyid")
        }
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && !sensitive
    }.getOrDefault(false)

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
        else -> "对方"
    }

    private fun occurredAt(entry: EntryEntity): String =
        occurredAt(entry.occurredAtEpochMillis, entry.occurredTimezone)

    private fun occurredAt(epochMillis: Long, timezone: String): String {
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("UTC") }
        return Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateFormatter)
    }
}
