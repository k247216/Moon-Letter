package com.twomemory.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twomemory.model.CityCatalog
import com.twomemory.model.CityOption
import com.twomemory.model.CityMapLevels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Both styles select the same real cities; changing a city never creates a record. */
@Composable
fun CitySelectionMap(
    selectedCity: String?,
    counts: Map<String, Int> = emptyMap(),
    onCitySelected: (CityOption) -> Unit,
    modifier: Modifier = Modifier,
    fitStoriesRequest: Int = 0,
) {
    var streets by rememberSaveable { mutableStateOf(false) }
    var showingStyles by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var focusRequest by rememberSaveable { mutableIntStateOf(0) }
    Box(modifier.clipToBounds()) {
        if (streets) StreetCityMap(selectedCity, counts, onCitySelected, Modifier.fillMaxSize(), focusRequest, fitStoriesRequest)
        else GeographicJournalMap(selectedCity, counts, onCitySelected, Modifier.fillMaxSize(), focusRequest, fitStoriesRequest)
        Surface(
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
            color = Color(0xFFFDF9EF).copy(alpha = .92f), shape = RoundedCornerShape(22.dp),
        ) {
            Row {
                IconButton(onClick = { searching = true }) {
                    Icon(TwoMemoryIcons.Search, "搜索城市", Modifier.size(20.dp), tint = TwoMemoryColors.WarmBeigeInk)
                }
                IconButton(onClick = { showingStyles = true }) {
                    Icon(TwoMemoryIcons.Layers, "地图样式", Modifier.size(20.dp), tint = TwoMemoryColors.WarmBeigeInk)
                }
            }
        }
    }
    if (showingStyles) AlertDialog(
        onDismissRequest = { showingStyles = false },
        title = { Text("地图样式", style = TwoMemoryTypography.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "地理手账", true to "街道地图").forEach { (streetStyle, label) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        streets = streetStyle
                        showingStyles = false
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = streets == streetStyle, onClick = {
                            streets = streetStyle
                            showingStyles = false
                        })
                        Column {
                            Text(label, style = TwoMemoryTypography.body)
                            Text(if (streetStyle) "联网加载街道细节，不上传故事" else "随时可用的城市地理底图",
                                style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showingStyles = false }) { Text("关闭") } },
    )
    if (searching) CitySearchDialog(onDismiss = { searching = false }, onSelect = {
        focusRequest++
        onCitySelected(it)
        searching = false
    })
}

@Composable
private fun CitySearchDialog(onDismiss: () -> Unit, onSelect: (CityOption) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) {
        CityCatalog.majorCities.filter {
            query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
                it.province.contains(query.trim(), ignoreCase = true)
        }.take(60)
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("去哪个城市？", style = TwoMemoryTypography.title) },
        text = {
            Column {
                OutlinedTextField(query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("搜索城市或省份") },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "城市名称或省份" })
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 290.dp)) {
                    if (results.isEmpty()) item {
                        Text("暂未找到这个城市，请试试附近的地级市。", Modifier.padding(vertical = 18.dp),
                            style = TwoMemoryTypography.caption, color = TwoMemoryColors.WarmBeigeMuted)
                    }
                    items(results, key = { it.name }) { city ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(city) }
                            .padding(horizontal = 4.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(city.name, Modifier.weight(1f), style = TwoMemoryTypography.body)
                            Text(city.province, style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun GeographicJournalMap(
    selectedCity: String?, counts: Map<String, Int>, onCitySelected: (CityOption) -> Unit,
    modifier: Modifier, focusRequest: Int, fitStoriesRequest: Int,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val geography by produceState<Result<Pair<List<CityMapRegion>, List<CityMapRegion>>>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val provinces = context.assets.open("map/china-provinces.geojson").bufferedReader().use {
                    readCityMapRegions(it.readText())
                }
                val cities = runCatching {
                    context.assets.open("map/china-cities.geojson").bufferedReader().use { readCityMapRegions(it.readText()) }
                }.getOrDefault(emptyList())
                provinces to cities
            }
        }
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val selected = CityCatalog.find(selectedCity)
    val cityAnchors = remember { CityCatalog.majorCities.associateWith { cityMapPoint(it.longitude, it.latitude) } }
    var lastFocused by remember { mutableStateOf<String?>(null) }
    var lastFocusRequest by remember { mutableIntStateOf(-1) }
    var lastFitRequest by remember { mutableIntStateOf(-1) }
    var lastFitCities by remember { mutableStateOf<Set<String>?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val cityCounts = remember(counts) {
        counts.entries.mapNotNull { (name, count) -> CityCatalog.find(name)?.let { it.name to count.coerceAtLeast(0) } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    }
    val mapWidth = 63f
    val storyCityNames = remember(cityCounts) { cityCounts.filterValues { it > 0 }.keys.toSet() }
    val mapHeight = cityMapMercator(54.0) - cityMapMercator(3.0)
    val padding = with(density) { 16.dp.toPx() }
    val baseScale = min((viewport.width - padding * 2).coerceAtLeast(1f) / mapWidth,
        (viewport.height - padding * 2).coerceAtLeast(1f) / mapHeight)
    val scale = baseScale * zoom
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    val worldCenter = Offset(mapWidth / 2f, mapHeight / 2f)
    fun screenPoint(city: CityOption) =
        center + pan + (cityAnchors.getValue(city) - worldCenter) * scale
    fun constrainPan(value: Offset, atZoom: Float): Offset {
        val x = mapWidth * baseScale * atZoom / 2 + viewport.width / 4f
        val y = mapHeight * baseScale * atZoom / 2 + viewport.height / 4f
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    fun changeZoom(next: Float) {
        val clamped = next.coerceIn(1f, 24f)
        pan = constrainPan(pan * (clamped / zoom), clamped)
        zoom = clamped
    }
    LaunchedEffect(viewport, geography, selected?.name, focusRequest, fitStoriesRequest, storyCityNames) {
        if (viewport.width <= 0 || geography?.isSuccess != true) return@LaunchedEffect
        if (fitStoriesRequest > 0 && selected == null &&
            (lastFitRequest != fitStoriesRequest || lastFitCities != storyCityNames)) {
            val points = cityCounts.filterValues { it > 0 }.keys.mapNotNull(CityCatalog::find)
                .map { cityAnchors.getValue(it) }
            if (points.isEmpty()) { zoom = 1f; pan = Offset.Zero }
            else {
                val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
                // Reserve space for the labels and the right-side zoom controls, not just the anchors.
                val usableWidth = (viewport.width - with(density) { 220.dp.toPx() }).coerceAtLeast(48f)
                val usableHeight = (viewport.height - with(density) { 168.dp.toPx() }).coerceAtLeast(48f)
                zoom = min(usableWidth / ((maxX - minX).coerceAtLeast(3f) * baseScale),
                    usableHeight / ((maxY - minY).coerceAtLeast(3f) * baseScale)).coerceIn(1f, 10f)
                val middle = Offset((minX + maxX) / 2, (minY + maxY) / 2)
                pan = (worldCenter - middle) * baseScale * zoom +
                    Offset(with(density) { (-50).dp.toPx() }, with(density) { 36.dp.toPx() })
            }
            lastFitRequest = fitStoriesRequest
            lastFitCities = storyCityNames
            lastFocused = selected?.name
            lastFocusRequest = focusRequest
            return@LaunchedEffect
        }
        if (selected != null) lastFitRequest = fitStoriesRequest
        if (selected == null) {
            if (lastFocused != null || lastFocusRequest == -1) { zoom = 1f; pan = Offset.Zero }
            lastFocused = null; lastFocusRequest = focusRequest
            return@LaunchedEffect
        }
        if (viewport.width > 0 && geography?.isSuccess == true &&
            (lastFocused != selected.name || lastFocusRequest != focusRequest)) {
            val focus = selected
            // Search results outside the view recenter; visible pin taps keep the map steady.
            val point = screenPoint(focus)
            if (lastFocusRequest != focusRequest || lastFocused == null || point.x !in 40f..(viewport.width - 40f) || point.y !in 70f..(viewport.height - 50f)) {
                // Regional framing separates nearby city-centre touch targets
                // (e.g. Hangzhou/Shanghai) without moving geographic anchors.
                zoom = 10f
                pan = (worldCenter - cityAnchors.getValue(focus)) * baseScale * zoom +
                    Offset(viewport.width * .08f, viewport.height * .04f)
            }
            lastFocused = focus.name
            lastFocusRequest = focusRequest
        }
    }
    val topClearance = with(density) { 104.dp.toPx() }
    val bottomClearance = with(density) { 34.dp.toPx() }
    val pinPositions = mutableListOf<Pair<CityOption, Offset>>()
    val occupiedTargets = mutableListOf<Rect>()
    val controls = with(density) { Rect(viewport.width - 60.dp.toPx(), viewport.height - 172.dp.toPx(),
        viewport.width.toFloat(), viewport.height.toFloat()) }
    val detailLevel = if (zoom < 1.8f) 0 else if (zoom < 3.5f) 1 else 2
    val orderedCities = remember(selected, cityCounts) {
        CityCatalog.majorCities.sortedWith(compareByDescending<CityOption> { it == selected }
            .thenByDescending { cityCounts[it.name] ?: 0 }.thenBy { CityMapLevels.level(it) })
    }
    // Text measurements do not depend on pan/zoom: cache them across every gesture frame.
    val labelSizes = remember(orderedCities, selected, cityCounts, density, textMeasurer) {
        orderedCities.associateWith { city ->
            val count = cityCounts[city.name] ?: 0
            textMeasurer.measure(if (count > 0) "${city.name} · $count" else city.name,
                style = TwoMemoryTypography.metadata.copy(fontSize = if (city == selected) 13.sp else 12.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = with(density) { 96.dp.roundToPx() })).size
        }
    }
    orderedCities.forEach { city ->
        if (!CityMapLevels.visible(city, detailLevel, selected?.name, cityCounts)) return@forEach
        val point = screenPoint(city)
        if (point.y !in topClearance..(viewport.height - bottomClearance)) return@forEach
        val count = cityCounts[city.name] ?: 0
        val chosen = city == selected
        val labelSize = labelSizes.getValue(city)
        val targetWidth = with(density) { (labelSize.width + 14.dp.roundToPx()).coerceAtLeast(48.dp.roundToPx()) }
        val targetHeight = with(density) { (labelSize.height +
            8.dp.roundToPx()).coerceAtLeast(48.dp.roundToPx()) }
        val target = with(density) { Rect(point.x + 8.dp.toPx(), point.y - 24.dp.toPx(),
            point.x + 8.dp.toPx() + targetWidth,
            point.y - 24.dp.toPx() + targetHeight) }
        if (point.x in 20f..(viewport.width - 20f) && point.y in topClearance..(viewport.height - bottomClearance) &&
            target.left >= 0f && target.right <= viewport.width && target.bottom <= viewport.height &&
            !target.overlaps(controls) && occupiedTargets.none { it.overlaps(target.inflate(with(density) { 4.dp.toPx() })) }) {
            pinPositions.add(city to point)
            occupiedTargets.add(target)
        }
    }
    Box(modifier.clipToBounds().background(Color(0xFFD6E4E1)).onSizeChanged { viewport = it }
        .semantics { contentDescription = "真实中国城市地图，双指缩放、拖动，点击地图或城市选择" }) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(viewport, baseScale) {
                detectTransformGestures { centroid, drag, gestureZoom, _ ->
                    val next = (zoom * gestureZoom).coerceIn(1f, 24f)
                    pan = constrainPan((pan + center - centroid) * (next / zoom) + centroid - center + drag, next)
                    zoom = next
                }
            }
            .pointerInput(viewport, scale, pan) {
                detectTapGestures { tap ->
                    val world = (tap - center - pan) / scale + worldCenter
                    val longitude = world.x + 73.0
                    val mercator = cityMapMercator(54.0) - world.y
                    val latitude = (2 * atan(exp(mercator * PI / 180)) - PI / 2) * 180 / PI
                    CityCatalog.nearest(latitude, longitude)?.let(onCitySelected)
                }
            }) {
            withTransform({
                translate(center.x + pan.x, center.y + pan.y)
                scale(scale, scale, pivot = Offset.Zero)
                translate(-worldCenter.x, -worldCenter.y)
            }) {
                geography?.getOrNull()?.let { (provinces, cities) ->
                    provinces.forEachIndexed { index, region ->
                        if (!region.boundaryOnly) drawPath(region.path,
                            if (index % 3 == 0) Color(0xFFE4EAD9) else Color(0xFFF3F0E5))
                        drawPath(region.path, Color(0xFFA6B3A0), style = Stroke(width = .8.dp.toPx() / scale))
                    }
                    if (zoom >= 2.5f) cities.forEachIndexed { index, region ->
                        if (!region.boundaryOnly) drawPath(region.path,
                            listOf(Color(0xFFF4F1E6), Color(0xFFE9EDDD), Color(0xFFEEF0E3))[index % 3])
                        drawPath(region.path, Color(0xFFB3BEA9), style = Stroke(width = .55.dp.toPx() / scale))
                    }
                }
            }
            // Keep the actual memory anchors visible even if nearby labels must be omitted.
            orderedCities.forEach { city ->
                if (CityMapLevels.visible(city, detailLevel, selected?.name, cityCounts)) {
                    val point = screenPoint(city)
                    if (point.x in 0f..size.width && point.y in topClearance..(size.height - bottomClearance)) {
                        val hasMemory = (cityCounts[city.name] ?: 0) > 0
                        val chosen = city == selected
                        if (hasMemory) drawCircle(TwoMemoryColors.WarmBeigeAccent.copy(alpha = .12f), radius = 8.dp.toPx(), center = point)
                        drawCircle(if (hasMemory) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeMuted.copy(alpha = .42f),
                            radius = (if (hasMemory) 4.dp else 2.5.dp).toPx(), center = point)
                        if (chosen) drawCircle(TwoMemoryColors.WarmBeigeInk.copy(alpha = .78f), radius = 10.dp.toPx(),
                            center = point, style = Stroke(width = 1.2.dp.toPx()))
                    }
                }
            }
        }
        if (geography?.isSuccess == true) pinPositions.asReversed().forEach { (city, point) ->
            key(city.name) {
                val isSelected = city == selected
                val count = cityCounts[city.name] ?: 0
                Surface(onClick = { onCitySelected(city) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.offset { IntOffset(point.x.roundToInt() + with(density) { 8.dp.roundToPx() },
                        point.y.roundToInt() - with(density) { 24.dp.roundToPx() }) }
                        .widthIn(min = 48.dp, max = 110.dp).heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "${city.name}，${count}篇记录${if (isSelected) "，已选择" else ""}"
                            stateDescription = when {
                                isSelected -> "当前选中的城市${if (count > 0) "，有故事" else ""}"
                                count > 0 -> "有故事的城市"
                                else -> "普通城市"
                            }
                        }) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 7.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Text(if (count > 0) "${city.name} · $count" else city.name,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = TwoMemoryTypography.metadata.copy(fontSize = if (isSelected) 13.sp else 12.sp),
                            color = if (isSelected) TwoMemoryColors.WarmBeigeInk else if (count > 0) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeMuted)
                    }
                }
            }
        } else Text(if (geography == null) "正在打开地图…" else "地图未能打开，请重新进入",
            Modifier.align(Alignment.Center).padding(24.dp), color = TwoMemoryColors.WarmBeigeInk)
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 24.dp),
            color = Color(0xFFFDF9EF).copy(alpha = .93f), shape = RoundedCornerShape(18.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { changeZoom(zoom * 1.6f) }, enabled = zoom < 24f) {
                    Icon(TwoMemoryIcons.Add, "放大地图", Modifier.size(18.dp), tint = TwoMemoryColors.WarmBeigeInk)
                }
                TextButton(onClick = { changeZoom(zoom / 1.6f) }, enabled = zoom > 1f,
                    modifier = Modifier.semantics { contentDescription = "缩小地图" }) { Text("−", color = TwoMemoryColors.WarmBeigeInk) }
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) {
                    Text("全国", style = TwoMemoryTypography.metadata, color = TwoMemoryColors.WarmBeigeInk)
                }
            }
        }
        Text("地理数据 © DataV / 阿里云", Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 9.dp),
            style = TwoMemoryTypography.metadata.copy(fontSize = 9.sp), color = TwoMemoryColors.WarmBeigeInk.copy(alpha = .60f))
    }
}

private data class CityMapRegion(val path: Path, val boundaryOnly: Boolean)

private fun cityMapMercator(latitude: Double): Float =
    (ln(tan(PI / 4 + latitude.coerceIn(-85.0, 85.0) * PI / 360)) * 180 / PI).toFloat()

private fun cityMapPoint(longitude: Double, latitude: Double): Offset =
    Offset((longitude - 73).toFloat(), cityMapMercator(54.0) - cityMapMercator(latitude))

private fun readCityMapRegions(json: String): List<CityMapRegion> {
    val features = JSONObject(json).getJSONArray("features")
    return (0 until features.length()).map { index ->
        val feature = features.getJSONObject(index)
        val geometry = feature.getJSONObject("geometry")
        val coordinates = geometry.getJSONArray("coordinates")
        val path = Path().apply { fillType = PathFillType.EvenOdd }
        fun addPolygon(rings: JSONArray) {
            for (r in 0 until rings.length()) {
                val ring = rings.getJSONArray(r)
                for (p in 0 until ring.length()) {
                    val coordinate = ring.getJSONArray(p)
                    val point = cityMapPoint(coordinate.getDouble(0), coordinate.getDouble(1))
                    if (p == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
            }
        }
        when (geometry.getString("type")) {
            "Polygon" -> addPolygon(coordinates)
            "MultiPolygon" -> for (p in 0 until coordinates.length()) addPolygon(coordinates.getJSONArray(p))
            else -> error("Unsupported published geometry")
        }
        CityMapRegion(path, feature.getJSONObject("properties").optString("adchar") == "JD")
    }
}
