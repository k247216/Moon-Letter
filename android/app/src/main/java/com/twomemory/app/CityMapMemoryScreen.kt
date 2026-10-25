package com.twomemory.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twomemory.designsystem.*
import com.twomemory.model.CityCatalog
import com.twomemory.model.CityOption

/** A map of memories, not an inferred travel tracker. */
@Composable
fun CityMapPreviewScreen(
    stories: List<CityStoryUi> = emptyList(),
    onOpenEntry: (String) -> Unit = {},
    onSaveCitySnapshot: (String) -> Unit = {},
    snapshotStatus: String? = null,
    onCreateInCity: (CityOption) -> Unit = {},
    lastBrowsedCity: String? = null,
    memoryScope: String = "local",
    onRememberCity: (String) -> Unit = {},
) {
    val index = remember(stories) { CityMemoryIndex(stories) }
    var selectedName by rememberSaveable(memoryScope) { mutableStateOf(CityCatalog.find(lastBrowsedCity)?.name) }
    var overview by rememberSaveable(memoryScope) { mutableStateOf(false) }
    var fitRequest by rememberSaveable(memoryScope) { mutableIntStateOf(0) }
    var citySelectionRequest by remember(memoryScope) { mutableIntStateOf(0) }
    val storyListState = key(memoryScope) { rememberLazyListState() }
    val selectedCity = CityCatalog.find(selectedName)
    val memory = index.cities.firstOrNull { it.city == selectedCity }
    LaunchedEffect(index.cities, memoryScope) {
        if (selectedName == null && !overview) selectedName = index.initialCity(lastBrowsedCity)?.name
    }
    LaunchedEffect(selectedName, memoryScope) { selectedName?.let(onRememberCity) }
    LaunchedEffect(citySelectionRequest, memoryScope) {
        if (citySelectionRequest > 0) storyListState.scrollToItem(0)
    }
    fun selectCity(city: CityOption) {
        selectedName = city.name
        overview = false
        citySelectionRequest++ // Selecting the same city is still a fresh browse request.
    }
    BoxWithConstraints(Modifier.fillMaxSize().paperTexture(TwoMemoryColors.WarmBeigeBackground)) {
        val mapHeight = (maxHeight * .43f).coerceIn(260.dp, 460.dp)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                CitySelectionMap(if (overview) null else selectedName, index.counts, ::selectCity,
                    Modifier.fillMaxSize(), fitStoriesRequest = fitRequest)
                Column(Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp, end = 118.dp)
                    .background(Brush.verticalGradient(listOf(TwoMemoryColors.WarmBeigeBackground.copy(alpha = .92f),
                        TwoMemoryColors.WarmBeigeBackground.copy(alpha = .0f)))).padding(end = 8.dp, bottom = 12.dp)) {
                    Text("我们的城市", style = TwoMemoryTypography.title.copy(fontSize = 24.sp))
                    PaperUnderline(Modifier.width(84.dp), color = TwoMemoryColors.WarmBeigeAccent)
                    Text("把回忆，留在地图上", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { overview = true; fitRequest++ }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Icon(TwoMemoryIcons.Location, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("查看全部足迹")
                    }
                    TextButton(onClick = { index.randomCity(selectedName)?.let(::selectCity) }, enabled = index.cities.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 0.dp)) { Text("随机翻一座城市") }
                }
                PaperRule(Modifier.fillMaxWidth())
            }
            if (overview || selectedCity == null) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("全国回忆地图", style = TwoMemoryTypography.title.copy(fontSize = 22.sp))
                        Text("标记的是有故事的城市，不代表实际到访。", style = TwoMemoryTypography.caption,
                            color = TwoMemoryColors.WarmBeigeMuted)
                    }
                    if (index.cities.isEmpty()) item {
                        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("先选一座城市", style = TwoMemoryTypography.title)
                            Text("在地图上点选，或用右上角搜索，再写下这里的故事。",
                                style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeMuted)
                        }
                    }
                    items(index.cities, key = { it.city.name }) { city ->
                        CityMemoryCover(city, Modifier.testTag("city-memory-${city.city.name}").clickable { selectCity(city.city) })
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${selectedCity.name}的故事", style = TwoMemoryTypography.title.copy(fontSize = 22.sp))
                        if (memory != null) {
                            Text("第一次留下故事 · ${memory.first.dateLabel}", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                            Text("最近一次回忆 · ${memory.latest.dateLabel}", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                        } else Text("还没有在这里留下故事", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                    }
                    TextButton(onClick = { onCreateInCity(selectedCity) }) { Text("写故事") }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = storyListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (memory == null) item {
                        Text("把这里的照片和心情，写进一篇回忆。", Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeMuted)
                    }
                    memory?.stories?.groupBy { it.dateLabel }?.forEach { (date, dayStories) ->
                        item(key = "city-date-$date") {
                            Text(date, Modifier.padding(start = 42.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                                style = TwoMemoryTypography.body, color = TwoMemoryColors.WarmBeigeSage)
                        }
                        items(dayStories, key = { it.id }) { story -> CityStoryCard(story, onOpenEntry) }
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CityMemoryCover(memory: CityMemory, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = TwoMemoryColors.WarmBeigeSurface, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (memory.cover.photoLocalPath != null || memory.cover.photoAssetId != null) {
                Box(Modifier.size(72.dp)) {
                    EntryPhoto(memory.cover.photoLocalPath, memory.cover.photoAssetId, Modifier.fillMaxSize(),
                        contentDescription = "${memory.city.name}的代表照片", contentScale = ContentScale.Fit, cornerRadius = 6.dp)
                }
            } else Icon(TwoMemoryIcons.Location, null, Modifier.size(32.dp), tint = TwoMemoryColors.WarmBeigeAccent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(memory.city.name, style = TwoMemoryTypography.title)
                val latestText = memory.latest.title?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: memory.latest.preview.trim()
                if (latestText.isNotEmpty()) Text(latestText, style = TwoMemoryTypography.body,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = TwoMemoryColors.WarmBeigeInk)
                Text("${memory.stories.size} 篇记录 · ${memory.city.province}", style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                Text("${memory.first.dateLabel} — ${memory.latest.dateLabel}", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
            }
            Text("›", color = TwoMemoryColors.WarmBeigeAccent, fontSize = 22.sp)
        }
    }
}
