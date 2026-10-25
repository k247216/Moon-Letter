package com.twomemory.app

import com.twomemory.database.EntryEntity
import com.twomemory.database.EntryBlockEntity
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TimelineProjectionTest {
    @Test fun voiceTitleTravelsFromExistingPayloadToTimelineWithoutChangingTheMedia() {
        val couple = UUID.randomUUID().toString(); val user = UUID.randomUUID().toString()
        val entry = EntryEntity(UUID.randomUUID().toString(), couple, user, "PERSONAL", "PUBLISHED", 1,
            "Asia/Shanghai", "声音礼物")
        val file = java.io.File(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
            "${UUID.randomUUID()}.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val block = EntryBlockEntity(id = UUID.randomUUID().toString(), entryId = entry.id,
            type = "AUDIO", orderKey = 0, authorId = user,
            payload = org.json.JSONObject().put("title", "回家的声音").put("mime", "audio/mp4")
                .put("duration", 12).put("localPath", file.absolutePath).toString(),
            assetId = UUID.randomUUID().toString())
        try {
            val audio = projectTimelineRows(listOf(entry), listOf(block)).single().attachments.single()
            assertEquals("回家的声音", audio.title)
            assertEquals(file.absolutePath, audio.localPath)
            assertEquals(block.assetId, audio.assetId)
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        } finally { file.delete() }
    }
    @Test fun timelineKeepsRecordOrderAndMatchesOnlyItsOwnLiveBlocksInOneScan() {
        val author=UUID.randomUUID().toString(); val couple=UUID.randomUUID().toString()
        val entries=(0 until 100).map {EntryEntity(UUID.randomUUID().toString(),couple,author,"PERSONAL","PUBLISHED",it.toLong(),"Asia/Shanghai","第${it}篇")}
        val data=entries.flatMap {entry -> listOf(
            EntryBlockEntity(UUID.randomUUID().toString(),entry.id,"TEXT",0,author,"{\"text\":\"${entry.title}的正文\"}",null),
            EntryBlockEntity(UUID.randomUUID().toString(),entry.id,"LOCATION",1,author,"{\"city\":\"武汉\"}",null))}
        var visits=0
        val tracked=object:AbstractList<EntryBlockEntity>() {
            override val size=data.size
            override fun get(index:Int):EntryBlockEntity {visits++;return data[index]}
        }
        val shown=projectTimelineRows(entries,tracked)
        assertEquals(entries.map {it.id},shown.map {it.id.toString()})
        assertEquals("第0篇的正文",shown.first().preview)
        assertEquals("第99篇的正文",shown.last().preview)
        assertEquals("武汉",shown.first().attachments.single().title)
        assertTrue("Block preparation must scale with blocks, not records × blocks: $visits",visits<=data.size*2)
    }
}
