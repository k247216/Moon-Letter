package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.twomemory.database.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryBookExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun bookContainsOfflineOriginalVoicePhotoAndResponsesButNotPrivateDrafts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val me = UUID.randomUUID(); val other = UUID.randomUUID(); val space = UUID.randomUUID()
            val entry = UUID.randomUUID().toString()
            db.entryDao().insertEntry(EntryEntity(entry, space.toString(), me.toString(), "PERSONAL", "PUBLISHED", 1756728000000L, "Asia/Shanghai", "雨天 <script>alert(1)</script>"))
            db.entryDao().insertEntry(EntryEntity(UUID.randomUUID().toString(), space.toString(), me.toString(), "PERSONAL", "DRAFT", 1756728000000L, "UTC", "私密内容不导出"))
            val mediaDirectory = File(context.filesDir, "photos").apply { mkdirs() }
            val image = File(mediaDirectory, "book-${UUID.randomUUID()}.png").apply {
                val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(180, 130, 90))
                outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            }
            val audio = File(mediaDirectory, "book-${UUID.randomUUID()}.m4a").apply { writeBytes(byteArrayOf(5,6,7)) }
            val video = File(mediaDirectory, "book-${UUID.randomUUID()}.mp4").apply { writeBytes(byteArrayOf(8,9,10)) }
            db.entryDao().insertBlocks(listOf(
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "TEXT", 0, me.toString(), "{\"text\":\"风里的声音 & 回忆\"}", null),
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "IMAGE", 1, me.toString(), "{\"localPath\":\"${image.absolutePath.replace('\\','/')}\",\"mime\":\"image/jpeg\"}", null),
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "AUDIO", 2, me.toString(), "{\"localPath\":\"${audio.absolutePath.replace('\\','/')}\",\"mime\":\"audio/mp4\"}", null),
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "MUSIC", 3, me.toString(), "{\"title\":\"晚风\",\"url\":\"https://music.example/play?token=SECRET&X-Amz-Signature=SIGNATURE\"}", null),
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "VIDEO", 4, me.toString(), "{\"localPath\":\"${video.absolutePath.replace('\\','/')}\",\"mime\":\"video/mp4\"}", null),
                EntryBlockEntity(UUID.randomUUID().toString(), entry, "TEXT", 5, me.toString(), "{\"localPath\":\"MALFORMED_PRIVATE_PATH\",\"token\":\"MALFORMED_TOKEN\",", null),
            ))
            db.commentDao().insert(CommentEntity(UUID.randomUUID().toString(), entry, other.toString(), "我也记得这一天", null, 1756728000000L))
            val output = MemoryBookExporter.build(context, db, me, "小满", "阿屿")
            ZipFile(output.file).use { zip ->
                val html = zip.getInputStream(zip.getEntry("index.html")).reader().readText()
                assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
                assertFalse(html.contains("私密内容不导出")); assertFalse(html.contains(context.filesDir.absolutePath))
                assertTrue(html.contains("我也记得这一天")); assertTrue(html.contains("<audio controls"))
                assertTrue(html.contains("风里的声音 &amp; 回忆"))
                assertFalse(html.contains("SECRET")); assertFalse(html.contains("SIGNATURE"))
                assertFalse(html.contains("MALFORMED_PRIVATE_PATH")); assertFalse(html.contains("MALFORMED_TOKEN"))
                val files = zip.entries().asSequence().filter { it.name.startsWith("media/") }.toList()
                assertEquals(3, files.size)
                assertTrue(html.contains("<video controls"))
                assertTrue(files.any { zip.getInputStream(it).readBytes().contentEquals(image.readBytes()) })
                assertTrue(files.any { zip.getInputStream(it).readBytes().contentEquals(byteArrayOf(5,6,7)) })
                assertTrue(files.any { zip.getInputStream(it).readBytes().contentEquals(byteArrayOf(8,9,10)) })
                val preview = Regex("src=['\"]data:image/(?:jpeg|png);base64,([^'\"]+)['\"]").find(html)
                assertNotNull("HTML copied alone must still contain its photograph", preview)
                val bytes = Base64.decode(preview!!.groupValues[1], Base64.DEFAULT)
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                assertNotNull(decoded)
                assertEquals(120, decoded.width); assertEquals(80, decoded.height)
                decoded.recycle()
                assertTrue(html.contains("data:font/ttf;base64,"))
                val manifest = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/manifest.json")).reader().readText())
                assertEquals(3, manifest.getJSONArray("media").length())
                val originals = manifest.getJSONArray("media")
                for (i in 0 until originals.length()) {
                    val item = originals.getJSONObject(i)
                    val archived = zip.getInputStream(zip.getEntry(item.getString("path"))).readBytes()
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(archived).joinToString("") { "%02x".format(it) }
                    assertEquals(hash, item.getString("sha256"))
                }
            }
            assertEquals(1, output.recordCount)
        } finally { db.close() }
    }

    @Test fun missingOriginalCannotProduceAFalseCompleteKeepsake() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val me = UUID.randomUUID(); val id = UUID.randomUUID().toString()
            db.entryDao().insertEntry(EntryEntity(id, UUID.randomUUID().toString(), me.toString(), "PERSONAL", "PUBLISHED", 100, "UTC", "丢失原图"))
            db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id, "IMAGE", 0, me.toString(), "{}", null)))
            val result = runCatching { MemoryBookExporter.build(context, db, me, "", "") }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("原文件"))
        } finally { db.close() }
    }

    @Test fun corruptPhotoCannotBeExportedAsASuccessfulKeepsake() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val me = UUID.randomUUID(); val id = UUID.randomUUID().toString()
            val file = File(File(context.filesDir, "photos").apply { mkdirs() }, "bad-${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            db.entryDao().insertEntry(EntryEntity(id, UUID.randomUUID().toString(), me.toString(), "PERSONAL", "PUBLISHED", 100, "UTC", "不可解码的照片"))
            db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id, "IMAGE", 0, me.toString(), org.json.JSONObject().put("localPath", file.absolutePath).toString(), null)))
            val result = runCatching { MemoryBookExporter.build(context, db, me, "", "") }
            assertTrue("Corrupt image must fail instead of showing a broken image", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("照片"))
        } finally { db.close() }
    }

    @Test fun independentDataArchiveRetainsOriginalsWithoutPresentationDependencies() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val me = UUID.randomUUID(); val id = UUID.randomUUID().toString()
            val file = File(File(context.filesDir, "photos").apply { mkdirs() }, "archive-${UUID.randomUUID()}.bin").apply { writeBytes(byteArrayOf(10, 20, 30)) }
            db.entryDao().insertEntry(EntryEntity(id, UUID.randomUUID().toString(), me.toString(), "PERSONAL", "PUBLISHED", 100, "UTC", "数据存档"))
            db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id, "VIDEO", 0, me.toString(), org.json.JSONObject().put("localPath", file.absolutePath).put("mime", "video/mp4").toString(), null)))
            val book = RecordArchiveExporter.build(context, db, me, "", "")
            ZipFile(book.file).use { zip ->
                assertNull(zip.getEntry("index.html"))
                val instructions = zip.getInputStream(zip.getEntry("阅读说明.txt")).reader().readText()
                assertFalse("Data archive must not instruct users to open an absent HTML file", instructions.contains("打开 index.html"))
                val records = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/records.json")).reader().readText())
                assertEquals(id, records.getJSONArray("records").getJSONObject(0).getString("id"))
                val manifest = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/manifest.json")).reader().readText())
                val item = manifest.getJSONArray("media").getJSONObject(0)
                val archivedBlock = records.getJSONArray("records").getJSONObject(0).getJSONArray("blocks").getJSONObject(0)
                assertEquals(item.getString("path"), archivedBlock.getString("mediaPath"))
                assertArrayEquals(byteArrayOf(10, 20, 30), zip.getInputStream(zip.getEntry(item.getString("path"))).readBytes())
            }
        } finally { db.close() }
    }

    @Test fun nestedMusicTitleAndArtistCannotLeakIntoHtmlOrArchiveMetadata() {
        val payload = org.json.JSONObject()
            .put("title", org.json.JSONObject().put("token", "nested-title-secret")
                .put("localPath", "/data/user/0/nested-title-private"))
            .put("artist", org.json.JSONObject().put("credential", "nested-artist-secret")
                .put("localPath", "/data/user/0/nested-artist-private"))
            .put("name", "安全的歌曲名")
        withPayloadBook("MUSIC", payload) { zip ->
            assertMetadataDoesNotContain(zip, "nested-title-secret", "nested-title-private",
                "nested-artist-secret", "nested-artist-private")
            val html = zip.getInputStream(zip.getEntry("index.html")).reader().readText()
            assertTrue("A scalar name must survive a rejected object title", html.contains("安全的歌曲名"))
            val record = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/records.json")).reader().readText())
                .getJSONArray("records").getJSONObject(0)
            val exported = record.getJSONArray("blocks").getJSONObject(0).getJSONObject("payload")
            assertEquals("安全的歌曲名", exported.getString("name"))
            assertFalse(exported.has("title"))
            assertFalse(exported.has("artist"))
        }
    }

    @Test fun nestedLocationCityCannotLeakIntoHtmlOrArchiveMetadata() {
        val payload = org.json.JSONObject()
            .put("city", org.json.JSONObject().put("token", "nested-city-secret")
                .put("localPath", "/data/user/0/nested-city-private"))
            .put("cityName", "杭州")
        withPayloadBook("LOCATION", payload) { zip ->
            assertMetadataDoesNotContain(zip, "nested-city-secret", "nested-city-private")
            val html = zip.getInputStream(zip.getEntry("index.html")).reader().readText()
            assertTrue("A scalar cityName must survive a rejected object city", html.contains("杭州"))
        }
    }

    @Test fun nestedMimeCannotLeakIntoBookOrDataArchiveManifest() {
        val payload = org.json.JSONObject().put("mime", org.json.JSONObject()
            .put("token", "nested-mime-secret").put("localPath", "/data/user/0/nested-mime-private"))
        for (archiveOnly in listOf(false, true)) {
            withPayloadBook("AUDIO", payload, archiveOnly) { zip ->
                assertMetadataDoesNotContain(zip, "nested-mime-secret", "nested-mime-private")
                val manifest = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/manifest.json")).reader().readText())
                val media = manifest.getJSONArray("media")
                assertEquals(1, media.length())
                val item = media.getJSONObject(0)
                val path = item.getString("path")
                assertArrayEquals(byteArrayOf(5, 6, 7), zip.getInputStream(zip.getEntry(path)).readBytes())
                val records = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/records.json")).reader().readText())
                val block = records.getJSONArray("records").getJSONObject(0).getJSONArray("blocks").getJSONObject(0)
                assertEquals(path, block.getString("mediaPath"))
                assertFalse(block.getJSONObject("payload").has("mime"))
            }
        }
    }

    @Test fun ordinaryMusicNameAndArtistAppearInHtmlAndMachineJson() {
        val payload = org.json.JSONObject().put("name", "晚风里的歌")
            .put("artist", "小满 & 阿屿").put("url", "https://music.example/song?id=42")
        withPayloadBook("MUSIC", payload) { zip ->
            val html = zip.getInputStream(zip.getEntry("index.html")).reader().readText()
            assertTrue("The HTML must display the MUSIC name field", html.contains("晚风里的歌"))
            assertTrue(html.contains("小满 &amp; 阿屿"))
            assertTrue(html.contains("https://music.example/song?id=42"))
            val records = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/records.json")).reader().readText())
            val exported = records.getJSONArray("records").getJSONObject(0).getJSONArray("blocks").getJSONObject(0)
                .getJSONObject("payload")
            assertEquals("晚风里的歌", exported.getString("name"))
            assertEquals("小满 & 阿屿", exported.getString("artist"))
            assertEquals("https://music.example/song?id=42", exported.getString("url"))
        }
    }

    private fun withPayloadBook(
        type: String,
        payload: org.json.JSONObject,
        archiveOnly: Boolean = false,
        assertions: (ZipFile) -> Unit,
    ) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var original: File? = null
        var output: File? = null
        try {
            val me = UUID.randomUUID()
            val id = UUID.randomUUID().toString()
            val storedPayload = org.json.JSONObject(payload.toString())
            if (type == "AUDIO") {
                val directory = File(context.filesDir, "photos").apply { check(mkdirs() || isDirectory) }
                val file = File(directory, "nested-${UUID.randomUUID()}.m4a")
                original = file
                file.writeBytes(byteArrayOf(5, 6, 7))
                storedPayload.put("localPath", file.absolutePath)
            }
            db.entryDao().insertEntry(EntryEntity(id, UUID.randomUUID().toString(), me.toString(),
                "PERSONAL", "PUBLISHED", 1756728000123L, "Asia/Shanghai", "字段安全回归"))
            db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id,
                type, 0, me.toString(), storedPayload.toString(), null)))
            val book = if (archiveOnly) RecordArchiveExporter.build(context, db, me, "我", "你")
                else MemoryBookExporter.build(context, db, me, "我", "你")
            output = book.file
            ZipFile(book.file).use(assertions)
        } finally {
            output?.delete()
            original?.delete()
            db.close()
        }
    }

    private fun assertMetadataDoesNotContain(zip: ZipFile, vararg forbidden: String) {
        for (name in listOf("index.html", "archive/records.json", "archive/manifest.json")) {
            val entry = zip.getEntry(name) ?: continue
            val text = zip.getInputStream(entry).reader().readText()
            forbidden.forEach { assertFalse("$name leaked $it", text.contains(it)) }
        }
    }

    @Test fun syncArrivingDuringMediaDownloadCannotSplitHtmlAndJsonVersions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val previous = com.twomemory.designsystem.EntryPhotos.remoteFile
        val asset = UUID.randomUUID().toString()
        try {
            val me = UUID.randomUUID(); val id = UUID.randomUUID().toString()
            val entry = EntryEntity(id, UUID.randomUUID().toString(), me.toString(), "PERSONAL", "PUBLISHED", 100, "UTC", "导出开始时的标题")
            db.entryDao().insertEntry(entry)
            db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id, "VIDEO", 0, me.toString(), "{\"mime\":\"video/mp4\"}", asset)))
            com.twomemory.designsystem.EntryPhotos.remoteFile = { _, destination ->
                db.entryDao().insertEntry(entry.copy(title = "下载时同步来的新标题"))
                destination.writeBytes(byteArrayOf(10, 20, 30))
            }
            val book = MemoryBookExporter.build(context, db, me, "", "")
            ZipFile(book.file).use { zip ->
                val json = org.json.JSONObject(zip.getInputStream(zip.getEntry("archive/records.json")).reader().readText())
                assertEquals("导出开始时的标题", json.getJSONArray("records").getJSONObject(0).getString("title"))
                val html = zip.getInputStream(zip.getEntry("index.html")).reader().readText()
                assertTrue(html.contains("导出开始时的标题")); assertFalse(html.contains("下载时同步来的新标题"))
            }
        } finally {
            com.twomemory.designsystem.EntryPhotos.remoteFile = previous
            File(context.filesDir, "downloaded-media/$asset").delete()
            db.close()
        }
    }

    @Test fun archiveCannotReadPrivateNonMediaFilesOrEscapedMediaPaths() = runBlocking {
        val privateFile = File(context.filesDir, "export-private-${UUID.randomUUID()}.xml").apply { writeText("SYNTHETIC_PRIVATE_SECRET") }
        try {
            val photos = File(context.filesDir, "photos").apply { mkdirs() }
            val paths = listOf(privateFile.absolutePath, File(photos, "../${privateFile.name}").path)
            for (path in paths) {
                val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
                try {
                    val me = UUID.randomUUID(); val id = UUID.randomUUID().toString()
                    db.entryDao().insertEntry(EntryEntity(id, UUID.randomUUID().toString(), me.toString(), "PERSONAL", "PUBLISHED", 100, "UTC", "路径隔离"))
                    db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id, "AUDIO", 0, me.toString(), org.json.JSONObject().put("localPath", path).put("mime", "audio/mp4").toString(), null)))
                    val result = runCatching { RecordArchiveExporter.build(context, db, me, "", "") }
                    result.getOrNull()?.file?.delete()
                    assertTrue("Exporter must reject a non-media file and canonical traversal", result.isFailure)
                } finally { db.close() }
            }
        } finally { privateFile.delete() }
    }

    @Test fun cancellationDuringCompletedResultHandoffRemovesUnownedZip() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val queue = java.util.concurrent.LinkedBlockingQueue<Runnable>()
        val dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queue.add(block) }
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher)
        val directory = File(context.cacheDir, "memory-books").apply { mkdirs() }
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        try {
            val me = UUID.randomUUID(); val id = UUID.randomUUID().toString()
            db.entryDao().insertEntry(EntryEntity(id, UUID.randomUUID().toString(), me.toString(), "PERSONAL", "PUBLISHED", 100, "UTC", "取消交接"))
            val job = scope.launch { RecordArchiveExporter.build(context, db, me, "", "") }
            queue.poll(30, java.util.concurrent.TimeUnit.SECONDS)!!.run()
            val handoff = queue.poll(30, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull("IO must complete and queue its caller resumption", handoff)
            assertTrue(directory.listFiles().orEmpty().any { it.name !in before && it.extension == "zip" })
            job.cancel()
            handoff!!.run()
            assertTrue(job.isCompleted)
            assertFalse("An undelivered export must not remain in cache", directory.listFiles().orEmpty().any { it.name !in before })
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            db.close()
        }
    }
}
