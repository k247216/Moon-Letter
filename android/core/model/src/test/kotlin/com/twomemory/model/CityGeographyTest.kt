package com.twomemory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CityGeographyTest {
    @Test fun cityLookupNormalizesSuffixButNeverGuessesUnknownCities() {
        assertEquals("杭州", CityCatalog.find(" 杭州市 ")?.name)
        assertEquals("香港", CityCatalog.find("香港特别行政区")?.name)
        assertNull(CityCatalog.find("不存在的城市"))
        assertNull(CityCatalog.find(null))
    }

    @Test fun provincialCapitalsAndTheirGeographicAnchorsAreAvailable() {
        listOf("哈尔滨", "长春", "沈阳", "呼和浩特", "乌鲁木齐", "拉萨", "西宁", "银川",
            "兰州", "郑州", "合肥", "福州", "南昌", "济南", "昆明", "贵阳", "南宁",
            "海口", "太原", "石家庄", "天津", "台北", "香港", "澳门").forEach {
            assertTrue(CityCatalog.find(it) != null, "Missing capital: $it")
        }
        val hangzhou = CityCatalog.find("杭州")!!
        assertTrue(hangzhou.latitude in 30.2..30.4)
        assertTrue(hangzhou.longitude in 120.0..120.3)
        assertTrue(CityCatalog.find("乌鲁木齐")!!.longitude < CityCatalog.find("北京")!!.longitude)
        assertEquals(CityCatalog.majorCities.size, CityCatalog.majorCities.map { it.name }.distinct().size)
        assertTrue(CityCatalog.majorCities.all { it.latitude in 16.0..54.0 && it.longitude in 73.0..136.0 })
    }

    @Test fun selectedCityFiltersAliasesAndExcludesOtherCitiesAndBlankSelection() {
        val rows = listOf("杭州", "杭州市", "上海", "", "未知")
        assertEquals(listOf("杭州", "杭州市"), CityCatalog.filterByCity(rows, "杭州") { it })
        assertEquals(listOf("上海"), CityCatalog.filterByCity(rows, "上海市") { it })
        assertTrue(CityCatalog.filterByCity(rows, null) { it }.isEmpty())
        assertTrue(CityCatalog.filterByCity(rows, "未知") { it }.isEmpty())
    }
}
