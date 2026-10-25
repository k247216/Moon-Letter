package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.room.withTransaction
import com.twomemory.database.AlbumImageDecoder
import com.twomemory.database.AppDatabase
import com.twomemory.designsystem.EntryPhotos
import com.twomemory.designsystem.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class MemoryBook(val file: File, val recordCount: Int, val mediaCount: Int)

/** A self-contained keepsake. Missing originals are errors, never empty media placeholders. */
internal object MemoryBookExporter {
    suspend fun build(context: Context, database: AppDatabase, ownId: UUID?, ownName: String, partnerName: String, archiveOnly: Boolean = false): MemoryBook {
        var ownedOutput: File? = null
        try {
        return withContext(Dispatchers.IO) {
            val snapshot = database.withTransaction {
                database.entryDao().publishedEntries().sortedBy { it.occurredAtEpochMillis }.map {
                    ExportRecordSnapshot(it, database.entryDao().blocks(it.id), database.commentDao().commentsForEntry(it.id))
                }
            }
            val entries = snapshot.map { it.entry }
            require(entries.isNotEmpty()) { "还没有这台设备可读取的已发布回忆" }
            val directory = File(context.cacheDir, "memory-books").apply { check(mkdirs() || isDirectory) }
            val output = File(directory, "MoonLetter_${UUID.randomUUID()}.zip")
            ownedOutput = output
            val pageFile = File.createTempFile("pages-", ".html", directory)
            val manifest = JSONArray()
            var mediaCount = 0
            fun author(id: String) = if (ownId?.toString() == id) ownName.ifBlank { "我" } else partnerName.ifBlank { "对方" }
            fun date(epoch: Long, zone: String) = Instant.ofEpochMilli(epoch)
                .atZone(runCatching { ZoneId.of(zone) }.getOrElse { ZoneId.of("UTC") })
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 · HH:mm"))
            try {
                ZipOutputStream(output.outputStream().buffered()).use { zip ->
                    fun putText(name: String, text: String) {
                        zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                    }
                    if (!archiveOnly) {
                        zip.putNextEntry(ZipEntry("fonts/OFL.txt"))
                        context.assets.open("wenkai-OFL.txt").use { it.copyTo(zip) }; zip.closeEntry()
                    }
                    pageFile.bufferedWriter().use { pages ->
                    for ((index, row) in snapshot.withIndex()) {
                        val entry = row.entry
                        currentCoroutineContext().ensureActive()
                        if (!archiveOnly) pages.append("<article><header><p class='author'>第 ${index + 1} 页 · 我们的日子</p><time>${escape(date(entry.occurredAtEpochMillis, entry.occurredTimezone))}</time><h2>${escape(entry.title.orEmpty().ifBlank { "这一天的回忆" })}</h2><p class='author'>${escape(author(entry.authorId))}</p></header>")
                        for (block in row.blocks) {
                            val payload = runCatching { JSONObject(block.payload) }.getOrElse { JSONObject() }
                            val safe = LocalExportBuilder.safePayload(block)
                            when (block.type) {
                                "TEXT" -> if (!archiveOnly) pages.append("<section class='writing'><span class='author'>${escape(author(block.authorId))}</span><p>${escape(safe.optString("text"))}</p></section>")
                                "IMAGE", "AUDIO", "VIDEO" -> {
                                    val file = EntryPhotos.localOriginal(context,
                                        (payload.opt("localPath") as? String)?.takeIf { allowedMediaFile(context, File(it)) }, block.assetId)
                                        ?: error("有${mediaLabel(block.type)}原文件尚未下载或已不可读取，请连接服务后重试")
                                    require(allowedMediaFile(context, file)) { "媒体原文件路径不属于应用媒体目录" }
                                    require(file.isFile && file.length() > 0) { "媒体原文件不可读取" }
                                    val mime = safe.optString("mime").lowercase(java.util.Locale.ROOT)
                                    val extension = extension(mime, block.type)
                                    val path = "media/${UUID.randomUUID()}.$extension"
                                    zip.putNextEntry(ZipEntry(path))
                                    val expected = file.length()
                                    var copied = 0L
                                    val digest = MessageDigest.getInstance("SHA-256")
                                    file.inputStream().use { input ->
                                        val buffer = ByteArray(32 * 1024)
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            val n = input.read(buffer); if (n < 0) break
                                            zip.write(buffer, 0, n); digest.update(buffer, 0, n); copied += n
                                        }
                                    }
                                    check(copied == expected && copied > 0) { "媒体原文件复制不完整" }
                                    zip.closeEntry(); mediaCount++
                                    manifest.put(JSONObject().put("path", path).put("recordId", entry.id).put("blockId", block.id)
                                        .put("kind", block.type).put("bytes", copied).put("mime", mime)
                                        .put("sha256", digest.digest().joinToString("") { "%02x".format(it) }))
                                    if (archiveOnly) continue
                                    pages.append("<figure class='${if (block.type == "IMAGE") "photo" else "sound-slip"}'>")
                                    pages.append(when (block.type) {
                                        "IMAGE" -> {
                                            val bitmap = AlbumImageDecoder.decode(file.absolutePath, 1600)
                                                ?: error("有照片无法生成可阅读的预览，请检查原文件格式后重试")
                                            try {
                                                val bytes = java.io.ByteArrayOutputStream()
                                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bytes)) { "照片预览生成失败" }
                                                "<img width='${bitmap.width}' height='${bitmap.height}' src='data:image/jpeg;base64,${Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)}' alt='这一天的照片' loading='lazy'>"
                                            } finally { bitmap.recycle() }
                                        }
                                        "AUDIO" -> "<p class='note'>留住声音，像你在身边</p><audio controls preload='none' src='$path'></audio>"
                                        else -> "<p class='note'>会动的回忆</p><video controls playsinline preload='none' src='$path'></video>"
                                    })
                                    pages.append("<figcaption>${mediaLabel(block.type)} · ${escape(author(block.authorId))} <a href='$path' download>保存原文件</a></figcaption></figure>")
                                }
                                "LOCATION" -> if (!archiveOnly) pages.append("<p class='note'>⌖ ${escape(safe.optString("city").ifBlank { safe.optString("cityName") })}</p>")
                                "MUSIC" -> {
                                    if (archiveOnly) continue
                                    val title = safe.optString("title").ifBlank { safe.optString("name") }.ifBlank { "一起听过的歌" }
                                    pages.append("<p class='note'>♫ ${escape(title)} ${escape(safe.optString("artist"))}</p>")
                                    val url = safe.optString("url").ifBlank { safe.optString("link") }
                                    if (safeWebUrl(url)) pages.append("<p class='note'><a href='${escape(url)}' rel='noreferrer'>打开音乐来源（需要联网）</a></p>")
                                }
                            }
                        }
                        val comments = row.comments
                        if (!archiveOnly && comments.isNotEmpty()) {
                            pages.append("<aside><h3>留给彼此的话</h3>")
                            comments.forEach { pages.append("<p><span class='author'>${escape(author(it.authorId))} · ${escape(date(it.createdAtEpochMillis, entry.occurredTimezone))}</span><br>${escape(it.body)}</p>") }
                            pages.append("</aside>")
                        }
                        if (!archiveOnly) pages.append("</article>")
                    }
                    }
                    if (!archiveOnly) {
                    zip.putNextEntry(ZipEntry("index.html"))
                    zip.write(MemoryBookPresentation.start.toByteArray(Charsets.UTF_8))
                    zip.write("data:font/ttf;base64,".toByteArray(Charsets.UTF_8))
                    android.util.Base64OutputStream(zip, Base64.NO_WRAP or Base64.NO_CLOSE).use { encoded ->
                        context.resources.openRawResource(R.font.moonletter_wenkai).use { it.copyTo(encoded) }
                    }
                    zip.write(MemoryBookPresentation.afterFont.toByteArray(Charsets.UTF_8))
                    zip.write("<section class='cover'><p class='eyebrow'>MOON LETTER · 给我们的日子</p><h1>我们的<br>回忆手书</h1><p class='names'>${escape(ownName.ifBlank { "我" })} &amp; ${escape(partnerName.ifBlank { "你" })}</p><p>把普通的日子，一页一页留住。</p><p class='date-range'>${escape(date(entries.first().occurredAtEpochMillis, entries.first().occurredTimezone).substringBefore(" ·"))} — ${escape(date(entries.last().occurredAtEpochMillis, entries.last().occurredTimezone).substringBefore(" ·"))}</p></section>".toByteArray(Charsets.UTF_8))
                    pageFile.inputStream().use { it.copyTo(zip) }
                    zip.write("<footer>写在日子里，留在我们心里。<br><small>照片预览和字体随手书保存；播放声音、视频和保存原文件，请保留解压后的 media 文件夹。<br>仅含本机可读取的已发布记录，不含独立相册、草稿和未解锁时间胶囊。</small></footer></main></body></html>".toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    }
                    val archiveJson = LocalExportBuilder.buildJsonSnapshot(snapshot, ownId, ownName, partnerName)
                        .put("format", "moon-letter-record-archive-v1").put("scope", "device-readable-published-records")
                    val mediaByBlock = (0 until manifest.length()).associate {
                        manifest.getJSONObject(it).let { item -> item.getString("blockId") to item.getString("path") }
                    }
                    val records = archiveJson.getJSONArray("records")
                    for (i in 0 until records.length()) {
                        val blocks = records.getJSONObject(i).getJSONArray("blocks")
                        for (j in 0 until blocks.length()) {
                            val block = blocks.getJSONObject(j)
                            mediaByBlock[block.getString("id")]?.let { block.put("mediaPath", it) }
                        }
                    }
                    putText("archive/records.json", archiveJson.toString(2))
                    putText("archive/manifest.json", JSONObject().put("format", "moon-letter-media-manifest-v1")
                        .put("scope", "device-readable-published-records").put("recordCount", entries.size).put("media", manifest).toString(2))
                    val readingHelp = if (archiveOnly) "这是记录数据存档，不含阅读网页。请保留 archive 与 media 文件夹的相对位置。"
                        else "完整解压后用浏览器打开 index.html。照片预览与字体已嵌入网页，单独复制网页也能看照片；声音、视频和原文件请保留 media 文件夹，播放取决于设备的格式支持。可打印为 PDF，但声音和视频不会在纸上播放。"
                    putText("阅读说明.txt", "$readingHelp\narchive/records.json 为机器可读记录，manifest.json 含原媒体路径、大小和 SHA-256 校验值。目前没有应用内导入功能。\n仅包含本机可读取的已发布记录，包括自己的暂时隐藏记录；不包含独立相册、草稿或未解锁时间胶囊，不是云端全量备份。\n分享压缩包会交出其中全部正文和原媒体，请确认再分享。")
                }
                currentCoroutineContext().ensureActive()
                MemoryBook(output, entries.size, mediaCount)
            } catch (failure: Throwable) {
                output.delete()
                throw failure
            } finally { pageFile.delete() }
        }
        } catch (failure: Throwable) {
            // withContext can reject the completed result during dispatcher
            // handoff; the caller has not received ownership of the ZIP yet.
            ownedOutput?.delete()
            throw failure
        }
    }

    private fun allowedMediaFile(context: Context, file: File): Boolean = runCatching {
        val canonical = file.canonicalFile.toPath()
        listOf("photos", "recordings", "downloaded-media", "independent_albums").any { name ->
            val directory = File(context.filesDir, name).canonicalFile.toPath()
            canonical != directory && canonical.startsWith(directory)
        }
    }.getOrDefault(false)

    private fun mediaLabel(type: String) = when(type) { "AUDIO" -> "声音"; "VIDEO" -> "视频"; else -> "照片" }
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun safeWebUrl(url: String) = runCatching {
        val uri = java.net.URI(url)
        val fields = listOfNotNull(uri.rawQuery, uri.rawFragment).flatMap { it.split('&') }
        val sensitive = fields.any { field ->
            val key = java.net.URLDecoder.decode(field.substringBefore('='), "UTF-8").lowercase()
            key.contains("token") || key.contains("secret") || key.contains("signature") ||
                key.contains("credential") || key.contains("password") || key.contains("accesskey") ||
                key.startsWith("x-amz-") || key.startsWith("x-oss-") || key in setOf("auth", "authorization", "ossaccesskeyid", "awsaccesskeyid")
        }
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && !sensitive
    }.getOrDefault(false)
    private fun extension(mime: String, type: String) = when(mime) {
        "image/jpeg", "image/jpg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"
        "image/heic" -> "heic"; "image/heif" -> "heif"; "audio/mpeg" -> "mp3"; "audio/wav" -> "wav"
        "audio/ogg" -> "ogg"; "audio/aac" -> "aac"; "video/webm" -> "webm"; "video/quicktime" -> "mov"
        "audio/mp4" -> "m4a"; "video/mp4" -> "mp4"
        else -> when(type) { "AUDIO" -> "m4a"; "VIDEO" -> "mp4"; else -> "jpg" }
    }
}
