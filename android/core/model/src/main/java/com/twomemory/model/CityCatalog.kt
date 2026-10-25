package com.twomemory.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline city-centre anchors for the geographic map and editor picker.
 * Coordinates are city-centre anchors, not a promise of live positioning.
 */
data class CityOption(
    val name: String,
    val province: String,
    val latitude: Double,
    val longitude: Double,
)

object CityCatalog {
    val majorCities: List<CityOption> = listOf(
        CityOption("北京", "北京", 39.9042, 116.4074),
        CityOption("上海", "上海", 31.2304, 121.4737),
        CityOption("广州", "广东", 23.1291, 113.2644),
        CityOption("深圳", "广东", 22.5431, 114.0579),
        CityOption("杭州", "浙江", 30.2741, 120.1551),
        CityOption("南京", "江苏", 32.0603, 118.7969),
        CityOption("苏州", "江苏", 31.2989, 120.5853),
        CityOption("成都", "四川", 30.5728, 104.0668),
        CityOption("重庆", "重庆", 29.5630, 106.5516),
        CityOption("武汉", "湖北", 30.5928, 114.3055),
        CityOption("西安", "陕西", 34.3416, 108.9398),
        CityOption("厦门", "福建", 24.4798, 118.0894),
        CityOption("青岛", "山东", 36.0671, 120.3826),
        CityOption("长沙", "湖南", 28.2282, 112.9388),
        CityOption("大理", "云南", 25.6065, 100.2676),
        CityOption("天津", "天津", 39.0842, 117.2009),
        CityOption("石家庄", "河北", 38.0428, 114.5149),
        CityOption("太原", "山西", 37.8706, 112.5489),
        CityOption("呼和浩特", "内蒙古", 40.8426, 111.7492),
        CityOption("沈阳", "辽宁", 41.8057, 123.4315),
        CityOption("长春", "吉林", 43.8171, 125.3235),
        CityOption("哈尔滨", "黑龙江", 45.8038, 126.5349),
        CityOption("合肥", "安徽", 31.8206, 117.2272),
        CityOption("福州", "福建", 26.0745, 119.2965),
        CityOption("南昌", "江西", 28.6829, 115.8582),
        CityOption("济南", "山东", 36.6512, 117.1201),
        CityOption("郑州", "河南", 34.7466, 113.6254),
        CityOption("南宁", "广西", 22.8170, 108.3665),
        CityOption("海口", "海南", 20.0440, 110.1999),
        CityOption("贵阳", "贵州", 26.6470, 106.6302),
        CityOption("昆明", "云南", 25.0389, 102.7183),
        CityOption("拉萨", "西藏", 29.6520, 91.1721),
        CityOption("兰州", "甘肃", 36.0611, 103.8343),
        CityOption("西宁", "青海", 36.6171, 101.7782),
        CityOption("银川", "宁夏", 38.4872, 106.2309),
        CityOption("乌鲁木齐", "新疆", 43.8256, 87.6168),
        CityOption("台北", "台湾", 25.0330, 121.5654),
        CityOption("香港", "香港", 22.3193, 114.1694),
        CityOption("澳门", "澳门", 22.1987, 113.5439),
        CityOption("宁波", "浙江", 29.8683, 121.5440),
        CityOption("三亚", "海南", 18.2528, 109.5119),
        CityOption("丽江", "云南", 26.8550, 100.2278),
        CityOption("桂林", "广西", 25.2742, 110.2991),
        CityOption("大连", "辽宁", 38.9140, 121.6147),
    ).let { legacyCities ->
        // Keep persisted names, picker order and original anchors for all 44 legacy cities.
        (legacyCities + dataVCities).distinctBy { it.name }
    }

    /**
     * Closest catalog anchor within [maxDistanceKm] kilometres (inclusive), using Haversine.
     * This is proximity to a city centre, not administrative containment or live positioning.
     * Distant coordinates and invalid coordinates/radii return null.
     */
    fun nearest(latitude: Double, longitude: Double, maxDistanceKm: Double = 120.0): CityOption? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0 ||
            !maxDistanceKm.isFinite() || maxDistanceKm < 0.0
        ) return null

        var nearestCity: CityOption? = null
        var nearestDistance = maxDistanceKm
        val latitudeRadians = Math.toRadians(latitude)
        for (city in majorCities) {
            val halfLatitudeDelta = Math.toRadians(city.latitude - latitude) / 2.0
            val halfLongitudeDelta = Math.toRadians(city.longitude - longitude) / 2.0
            val latitudeSin = sin(halfLatitudeDelta)
            val longitudeSin = sin(halfLongitudeDelta)
            val haversine = latitudeSin * latitudeSin +
                cos(latitudeRadians) * cos(Math.toRadians(city.latitude)) * longitudeSin * longitudeSin
            val distance = 2.0 * 6371.0088 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
            if (distance <= maxDistanceKm && (nearestCity == null || distance < nearestDistance)) {
                nearestCity = city
                nearestDistance = distance
            }
        }
        return nearestCity
    }

    /** Persisted location blocks may contain a municipality suffix. Never fuzzy-match. */
    fun find(name: String?): CityOption? {
        val normalized = name?.trim()?.removeSuffix("特别行政区")?.removeSuffix("市")
        return majorCities.firstOrNull { it.name == normalized }
    }

    /** A blank or unknown selection must not accidentally expose every city's stories. */
    fun <T> filterByCity(items: List<T>, selectedCity: String?, cityOf: (T) -> String?): List<T> {
        val selected = find(selectedCity) ?: return emptyList()
        return items.filter { find(cityOf(it)) == selected }
    }
}
