package com.twomemory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CityCatalogExpansionTest {
    @Test fun includesPrefectureCitiesAndAutonomousPrefectures() {
        assertTrue(CityCatalog.majorCities.size >= 300)
        listOf("绍兴" to "浙江", "湖州" to "浙江", "绵阳" to "四川",
            "大理白族自治州" to "云南", "阿坝藏族羌族自治州" to "四川",
            "锡林郭勒盟" to "内蒙古").forEach { (name, province) ->
            assertEquals(province, assertNotNull(CityCatalog.find(name)).province)
        }
        assertEquals(CityCatalog.find("绍兴"), CityCatalog.find(" 绍兴市 "))
        assertTrue(CityCatalog.find("绍兴")!!.latitude in 29.9..30.1)
        assertTrue(CityCatalog.find("绍兴")!!.longitude in 120.5..120.7)
        assertTrue(CityCatalog.find("湖州")!!.latitude in 30.8..30.9)
        assertTrue(CityCatalog.find("绵阳")!!.longitude in 104.7..104.8)
    }

    @Test fun preservesAllLegacySelectionsAndAnchors() {
        val legacyNames = listOf("北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "成都",
            "重庆", "武汉", "西安", "厦门", "青岛", "长沙", "大理", "天津", "石家庄", "太原",
            "呼和浩特", "沈阳", "长春", "哈尔滨", "合肥", "福州", "南昌", "济南", "郑州",
            "南宁", "海口", "贵阳", "昆明", "拉萨", "兰州", "西宁", "银川", "乌鲁木齐",
            "台北", "香港", "澳门", "宁波", "三亚", "丽江", "桂林", "大连")
        assertEquals(legacyNames, CityCatalog.majorCities.take(44).map { it.name })
        assertEquals(CityOption("大理", "云南", 25.6065, 100.2676), CityCatalog.find("大理市"))
        assertEquals(CityOption("杭州", "浙江", 30.2741, 120.1551), CityCatalog.find("杭州"))
        assertEquals("澳门", CityCatalog.find("澳门特别行政区")?.name)
    }

    @Test fun namesAndAnchorsAreUniqueAndGeographicallyReasonable() {
        val cities = CityCatalog.majorCities
        assertEquals(cities.size, cities.map { it.name }.distinct().size)
        assertEquals(cities.size, cities.map { it.latitude to it.longitude }.distinct().size)
        assertTrue(cities.all { it.name.isNotBlank() && !it.name.endsWith("市") && it.province.isNotBlank() })
        assertTrue(cities.all { it.latitude in 16.0..54.0 && it.longitude in 73.0..136.0 })
        assertNotNull(CityCatalog.find("三沙"))
    }

    @Test fun unknownNamesNeverMatchOrExposeStories() {
        listOf(null, "", "未知城市", "绍", "浙江绍兴").forEach {
            assertNull(CityCatalog.find(it))
            assertTrue(CityCatalog.filterByCity(listOf("绍兴", "湖州"), it) { row -> row }.isEmpty())
        }
        assertEquals(listOf("绍兴", "绍兴市"),
            CityCatalog.filterByCity(listOf("绍兴", "绍兴市", "湖州", "未知"), "绍兴市") { it })
    }

    @Test fun nearestUsesRealKilometresAndTheRequestedRadius() {
        assertEquals("绍兴", CityCatalog.nearest(29.997117, 120.582112)?.name)
        assertEquals("湖州", CityCatalog.nearest(30.868, 120.103, 1.0)?.name)
        assertEquals("绵阳", CityCatalog.nearest(31.46402, 104.741722, 0.0)?.name)
        // 0.5 degrees north of Beijing is about 55.6 km; longitude/latitude are not kilometres.
        assertNull(CityCatalog.nearest(40.4042, 116.4074, 50.0))
        assertEquals("北京", CityCatalog.nearest(40.4042, 116.4074, 60.0)?.name)
        assertEquals("杭州", CityCatalog.nearest(30.2741, 120.1551, 0.0)?.name)
        assertNull(CityCatalog.nearest(30.2742, 120.1551, 0.0))
    }

    @Test fun distantOrInvalidCoordinatesDoNotBecomeACity() {
        listOf(0.0 to 0.0, 51.5074 to -0.1278, 40.7128 to -74.0060,
            35.6762 to 139.6503, 0.0 to 110.0).forEach { (latitude, longitude) ->
            assertNull(CityCatalog.nearest(latitude, longitude))
        }
        assertNull(CityCatalog.nearest(Double.NaN, 120.0))
        assertNull(CityCatalog.nearest(30.0, Double.POSITIVE_INFINITY))
        assertNull(CityCatalog.nearest(91.0, 120.0))
        assertNull(CityCatalog.nearest(30.0, 181.0))
        assertNull(CityCatalog.nearest(30.0, 120.0, -1.0))
        assertNull(CityCatalog.nearest(30.0, 120.0, Double.NaN))
        assertNull(CityCatalog.nearest(30.0, 120.0, Double.POSITIVE_INFINITY))
    }
}
