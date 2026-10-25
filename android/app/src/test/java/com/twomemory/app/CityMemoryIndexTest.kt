package com.twomemory.app

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

class CityMemoryIndexTest {
    private fun story(id: String, city: String, time: Long) = CityStoryUi(id, id, city, "date-$time", id, "body", "author", occurredAtEpochMillis = time)

    @Test fun canonicalCitiesDeduplicateEntriesAndUseActualTimeInsteadOfInputOrder() {
        val first = story("old", "杭州市", 100)
        val index = CityMemoryIndex(listOf(first, first.copy(id = "duplicate-location"), story("recent", "武汉", 900),
            story("middle", "杭州", 400), story("unknown", "随便填写的名字", 1000)))
        assertEquals(2, index.cities.size); assertEquals(2, index.provinceCount); assertEquals(3, index.recordCount)
        assertEquals("武汉", index.initialCity(null)?.name)
        assertEquals("杭州", index.initialCity(" 杭州市 ")?.name)
        assertEquals("武汉", index.initialCity("未知")?.name)
        val hangzhou = index.cities.single { it.city.name == "杭州" }
        assertEquals(2, hangzhou.stories.size)
        assertEquals("old", hangzhou.first.entryId); assertEquals("middle", hangzhou.latest.entryId)
    }

    @Test fun blankMapHasNoInventedDefaultOrRandomMemory() {
        val index = CityMemoryIndex(emptyList())
        assertNull(index.initialCity(null)); assertNull(index.randomCity(null, Random(1)))
        assertEquals(0, index.recordCount); assertEquals(0, index.provinceCount)
    }

    @Test fun randomMemoryOnlyChoosesRealStoryCitiesAndAvoidsCurrentWhenPossible() {
        val index = CityMemoryIndex(listOf(story("a", "武汉", 100), story("b", "湖州", 200)))
        assertEquals("湖州", index.randomCity("武汉", Random(4))?.name)
        assertEquals("武汉", CityMemoryIndex(listOf(story("a", "武汉", 1))).randomCity("武汉", Random(4))?.name)
    }
}
