package com.twomemory.app

import com.twomemory.model.CityCatalog
import com.twomemory.model.CityOption
import kotlin.random.Random

internal data class CityMemory(val city: CityOption, val stories: List<CityStoryUi>) {
    val first: CityStoryUi get() = stories.last()
    val latest: CityStoryUi get() = stories.first()
    val cover: CityStoryUi get() = latest
}

/** Only mapped records, never a claim of physical visits or travel routes. */
internal class CityMemoryIndex(stories: List<CityStoryUi>) {
    val cities: List<CityMemory> = stories.mapNotNull { story ->
        CityCatalog.find(story.city)?.let { story.copy(city = it.name) }
    }.distinctBy { it.entryId to it.city }
        .sortedWith(compareByDescending<CityStoryUi> { it.occurredAtEpochMillis }.thenByDescending { it.entryId })
        .groupBy { it.city }.map { (city, records) -> CityMemory(CityCatalog.find(city)!!, records) }
        .sortedWith(compareByDescending<CityMemory> { it.latest.occurredAtEpochMillis }.thenBy { it.city.name })
    val provinceCount = cities.map { it.city.province }.distinct().size
    val recordCount = cities.flatMap { it.stories }.map { it.entryId }.distinct().size
    val counts = cities.associate { it.city.name to it.stories.size }
    fun initialCity(lastBrowsed: String?): CityOption? = CityCatalog.find(lastBrowsed) ?: cities.firstOrNull()?.city
    fun randomCity(current: String?, random: Random = Random.Default): CityOption? {
        val choices = cities.map { it.city }.filter { it.name != current }.ifEmpty { cities.map { it.city } }
        return choices.randomOrNull(random)
    }
}
