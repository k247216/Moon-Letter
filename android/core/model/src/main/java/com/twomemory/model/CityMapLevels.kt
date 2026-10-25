package com.twomemory.model

/** Display prominence only; not population rankings or administrative containment. */
object CityMapLevels {
    private val national = setOf("北京", "上海", "广州", "深圳", "成都", "重庆", "武汉", "西安", "沈阳", "哈尔滨", "昆明", "乌鲁木齐", "拉萨", "海口", "台北", "香港")
    private val regional = CityCatalog.majorCities.take(44).map { it.name }.toSet() + setOf(
        "湖州", "绍兴", "嘉兴", "温州", "金华", "台州", "无锡", "南通", "常州", "徐州",
        "唐山", "保定", "洛阳", "开封", "襄阳", "宜昌", "岳阳", "衡阳", "株洲", "泉州",
        "漳州", "莆田", "佛山", "东莞", "惠州", "珠海", "中山", "汕头", "柳州", "北海",
        "绵阳", "德阳", "乐山", "宜宾", "遵义", "曲靖", "宝鸡", "咸阳", "天水", "延吉",
    )
    fun level(city: CityOption): Int = if (city.name in national) 0 else if (city.name in regional) 1 else 2
    fun visible(city: CityOption, detailLevel: Int, selected: String?, counts: Map<String, Int>): Boolean =
        city.name == selected || (counts[city.name] ?: 0) > 0 || level(city) <= detailLevel
}
