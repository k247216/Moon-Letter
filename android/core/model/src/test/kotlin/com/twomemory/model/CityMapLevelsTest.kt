package com.twomemory.model

import kotlin.test.*

class CityMapLevelsTest {
    @Test fun regionalZoomRevealsNonCapitalPrefecturesBeforeTheMostDetailedTier() {
        for (name in listOf("湖州", "绍兴", "绵阳", "宜昌", "襄阳")) {
            val city = CityCatalog.find(name)!!
            assertFalse(CityMapLevels.visible(city, 0, null, emptyMap()))
            assertTrue(CityMapLevels.visible(city, 1, null, emptyMap()), "Regional zoom should reveal $name")
        }
    }
    @Test fun nationalViewPreservesMemoriesAndSelectionButRevealsOtherPrefecturesOnlyOnZoom() {
        val wuhan = CityCatalog.find("武汉")!!; val huzhou = CityCatalog.find("湖州")!!
        assertTrue(CityMapLevels.visible(wuhan, 0, null, emptyMap()))
        assertFalse(CityMapLevels.visible(huzhou, 0, null, emptyMap()))
        assertTrue(CityMapLevels.visible(huzhou, 0, "湖州", emptyMap()))
        assertTrue(CityMapLevels.visible(huzhou, 0, null, mapOf("湖州" to 1)))
        assertTrue(CityMapLevels.visible(huzhou, 2, null, emptyMap()))
        assertFalse(CityMapLevels.visible(huzhou, 0, null, mapOf("湖州" to 0)))
    }
}
