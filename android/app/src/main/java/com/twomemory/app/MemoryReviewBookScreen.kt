package com.twomemory.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.twomemory.designsystem.*
import com.twomemory.model.BlockType
import com.twomemory.timeline.EntryBlockUi
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import org.json.JSONObject

/** Flipping a page is read-only; existing records remain the source of truth. */
@Composable
fun MemoryReviewScreen(route: MemoryReviewRoute, memories: List<ReviewMemoryUi>, onBack: () -> Unit,
    onOpenEntry: (String) -> Unit, today: LocalDate = LocalDate.now(), onOpenBook: (() -> Unit)? = null) {
    var past by rememberSaveable(route) { mutableStateOf(route == MemoryReviewRoute.PAST_TODAY) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    val months = remember(memories) { memories.mapNotNull { it.date?.let(YearMonth::from) }.distinct().sortedDescending() }
    var selectedMonth by rememberSaveable { mutableStateOf(months.firstOrNull()?.toString().orEmpty()) }
    var choosingMonth by remember { mutableStateOf(false) }
    var choosingWeek by remember { mutableStateOf(false) }
    LaunchedEffect(months) {
        if (months.none { it.toString() == selectedMonth }) selectedMonth = months.firstOrNull()?.toString().orEmpty()
    }
    val weeks = remember(memories) { memories.mapNotNull { it.date?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .distinct().sortedDescending() }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var selectedWeek by rememberSaveable { mutableStateOf((thisWeek.takeIf { it in weeks } ?: weeks.firstOrNull() ?: thisWeek).toString()) }
    LaunchedEffect(weeks) {
        if (weeks.isNotEmpty() && weeks.none { it.toString()==selectedWeek }) selectedWeek=weeks.first().toString()
    }
    val weekStart = LocalDate.parse(selectedWeek)
    val shown = remember(memories, past, monthly, selectedMonth, today, weekStart) {
        when {
            past -> historicalToday(memories, today)
            monthly -> memories.filter { it.date?.let(YearMonth::from)?.toString() == selectedMonth }
            else -> memories.filter { page -> page.date?.let { it >= weekStart && it < weekStart.plusDays(7) } ?: page.id.startsWith("week:") }
        }
    }
    MoonLetterPaperScaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp, vertical=8.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onBack) { Icon(TwoMemoryIcons.Close, "返回我们", Modifier.size(18.dp)); Text("返回") }
                Spacer(Modifier.weight(1f))
                Text(if (past) "过去的今天" else "回忆手书", style=TwoMemoryTypography.caption, color=TwoMemoryColors.WarmBeigeMuted)
            }
            Text(if (past) "再遇见这一天" else "翻翻我们的回忆", style=TwoMemoryTypography.display,
                modifier=Modifier.padding(top=6.dp, bottom=4.dp))
            Text(if (past) "同一天，曾经写下的心情。" else "把普通的日子，一页一页留住。", style=TwoMemoryTypography.caption,
                color=TwoMemoryColors.WarmBeigeMuted)
            if (!past) Row(Modifier.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                FilterChip(selected=!monthly, onClick={ monthly=false }, label={Text("按周")})
                Spacer(Modifier.width(8.dp))
                FilterChip(selected=monthly, onClick={ monthly=true }, label={Text("按月")})
                Spacer(Modifier.width(10.dp))
                if (monthly) TextButton(onClick={choosingMonth=true}, enabled=months.isNotEmpty(),
                    modifier=Modifier.semantics { contentDescription="选择回忆月份" }) {
                    Text(months.firstOrNull { it.toString()==selectedMonth }?.let { "${it.year}年${it.monthValue}月" } ?: "选择月份")
                } else TextButton(onClick={choosingWeek=true}, enabled=weeks.isNotEmpty(),
                    modifier=Modifier.semantics { contentDescription="选择回忆周次" }) {
                    Text("${weekStart.monthValue}.${weekStart.dayOfMonth} — ${weekStart.plusDays(6).monthValue}.${weekStart.plusDays(6).dayOfMonth}",
                        style=TwoMemoryTypography.caption)
                }
            } else Spacer(Modifier.height(14.dp))
            if (shown.isEmpty()) {
                Column(Modifier.weight(1f).padding(28.dp), verticalArrangement=Arrangement.Center,
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(TwoMemoryIcons.Time, null, Modifier.size(32.dp), tint=TwoMemoryColors.WarmBeigeAccent)
                    Text(if (past) "这一天，还在等回忆慢慢长大" else "这一册，等我们慢慢写", style=TwoMemoryTypography.title,
                        modifier=Modifier.padding(vertical=16.dp))
                    if (past) TextButton(onClick={if(onOpenBook!=null) onOpenBook() else past=false}) { Text("翻翻其他日子") }
                }
            } else key(past, monthly, selectedMonth, weekStart) {
                val pager=rememberPagerState { shown.size }
                val scope=rememberCoroutineScope()
                HorizontalPager(state=pager, modifier=Modifier.weight(1f).fillMaxWidth(), key={shown[it].id}) { index ->
                    MemoryBookPage(shown[index], onOpenEntry, Modifier.graphicsLayer {
                        val offset=((pager.currentPage-index)+pager.currentPageOffsetFraction).coerceIn(-1f,1f)
                        rotationY=offset*8f
                        scaleX=1f-offset.absoluteValue*.025f
                        scaleY=1f-offset.absoluteValue*.025f
                        cameraDistance=24f*density
                    })
                }
                Row(Modifier.fillMaxWidth().padding(vertical=6.dp), horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={scope.launch { pager.animateScrollToPage(pager.currentPage-1) }}, enabled=pager.currentPage>0) { Text("上一页") }
                    Text("${pager.currentPage+1} / ${shown.size}", style=TwoMemoryTypography.caption, color=TwoMemoryColors.WarmBeigeMuted)
                    TextButton(onClick={scope.launch { pager.animateScrollToPage(pager.currentPage+1) }}, enabled=pager.currentPage<shown.lastIndex) { Text("下一页") }
                }
                if (past) TextButton(onClick={if(onOpenBook!=null) onOpenBook() else past=false}) { Text("翻翻其他日子") }
            }
        }
    }
    if (choosingMonth) AlertDialog(onDismissRequest={choosingMonth=false}, title={Text("翻到哪一月")}, text={
        LazyColumn { items(months, key={it.toString()}) { month ->
            TextButton(onClick={selectedMonth=month.toString(); choosingMonth=false}, modifier=Modifier.fillMaxWidth()) {
                Text("${month.year}年${month.monthValue}月")
            }
        } }
    }, confirmButton={TextButton(onClick={choosingMonth=false}) {Text("合上")}})
    if (choosingWeek) AlertDialog(onDismissRequest={choosingWeek=false}, title={Text("翻到哪一周")}, text={
        LazyColumn { items(weeks, key={it.toString()}) { start ->
            TextButton(onClick={selectedWeek=start.toString(); choosingWeek=false}, modifier=Modifier.fillMaxWidth()) {
                val end=start.plusDays(6)
                Text("${start.year}年${start.monthValue}月${start.dayOfMonth}日 — ${end.monthValue}月${end.dayOfMonth}日")
            }
        } }
    }, confirmButton={TextButton(onClick={choosingWeek=false}) {Text("合上")}})
}

@Composable
private fun MemoryBookPage(memory: ReviewMemoryUi, onOpenEntry: (String) -> Unit, modifier: Modifier=Modifier) {
    var photo by remember(memory.id) { mutableStateOf<EntryBlockUi?>(null) }
    val visiblePhoto=memory.blocks.firstOrNull { it.id==photo?.id && it.type==BlockType.IMAGE }
    LaunchedEffect(visiblePhoto) { if(visiblePhoto==null) photo=null }
    Box(modifier.fillMaxSize().padding(horizontal=5.dp,vertical=7.dp), contentAlignment=Alignment.TopCenter) {
        Box(Modifier.widthIn(max=640.dp).fillMaxWidth().fillMaxHeight()) {
        BookPaperEdges(Modifier.matchParentSize())
        Surface(Modifier.fillMaxSize().padding(end=5.dp,bottom=5.dp).semantics {contentDescription="回忆册书页"},
            shape=RoundedCornerShape(5.dp,18.dp,18.dp,5.dp), shadowElevation=2.dp,
            color=Color(0xFFFFFCF5), border=BorderStroke(1.dp, TwoMemoryColors.WarmBeigeLine.copy(alpha=.55f))) {
            LazyColumn(Modifier.padding(start=24.dp,end=18.dp), contentPadding=PaddingValues(vertical=20.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        BookDateStamp(memory.dateLabel, memory.timeLabel, memory.shared)
                        Text(memory.title ?: "这一天的回忆", style=TwoMemoryTypography.display)
                        Text(if (memory.shared) "一起留下的这一页" else memory.author, style=TwoMemoryTypography.caption,
                            color=TwoMemoryColors.WarmBeigeAccent)
                        BookHandDrawnRule()
                    }
                }
                if (memory.blocks.isEmpty()) item { Text(memory.body, style=TwoMemoryTypography.body) }
                itemsIndexed(memory.blocks, key={_,it->it.id}) { index,block ->
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if (index==0 || memory.blocks[index-1].authorId!=block.authorId ||
                            (block.authorId==null && memory.blocks[index-1].author!=block.author)) block.author?.let {
                            Text(it, style=TwoMemoryTypography.caption,
                                color=if(block.mine) TwoMemoryColors.WarmBeigeAccent else TwoMemoryColors.WarmBeigeSage)
                        }
                        when(block.type) {
                            BlockType.TEXT -> block.text?.let { BookHandwriting(it, block.mine) }
                            BlockType.IMAGE -> BookPhotoFrame(index) {
                                EntryPhoto(block.localPath, block.assetId,
                                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable {photo=block},
                                    "翻看回忆里的照片", contentScale=ContentScale.Fit, cornerRadius=4.dp, reading=true)
                            }
                            BlockType.AUDIO -> AudioMemoryCard(block.localPath, block.assetId, Modifier.fillMaxWidth(),
                                title = runCatching { JSONObject(block.payload.orEmpty()).optString("title") }.getOrDefault(""))
                            BlockType.VIDEO -> VideoMemoryCard(block.localPath, block.assetId, Modifier.fillMaxWidth())
                            BlockType.MUSIC -> {
                                val json=runCatching {JSONObject(block.payload.orEmpty())}.getOrElse {JSONObject()}
                                MusicMemoryCard(json.optString("title"), json.optString("artist"),
                                    json.optString("url").ifBlank {json.optString("link")}, Modifier.fillMaxWidth())
                            }
                            BlockType.LOCATION -> {
                                val json=runCatching {JSONObject(block.payload.orEmpty())}.getOrElse {JSONObject()}
                                val city=json.optString("city").ifBlank {json.optString("cityName")}.ifBlank {json.optString("name")}
                                if(city.isNotBlank()) Text(city, style=TwoMemoryTypography.caption, color=TwoMemoryColors.WarmBeigeMuted)
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        BookHandDrawnRule()
                        TextButton(onClick={onOpenEntry(memory.id.substringAfter(':'))}, modifier=Modifier.fillMaxWidth()) { Text("读完整这一页") }
                    }
                }
            }
        }
        }
    }
    visiblePhoto?.let { image -> Dialog(onDismissRequest={photo=null}, properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(), color=Color.Black) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                TextButton(onClick={photo=null}) { Text("合上照片", color=Color.White) }
                ZoomablePhoto(image.id, Modifier.weight(1f).fillMaxWidth()) {
                    EntryPhoto(image.localPath, image.assetId, Modifier.fillMaxSize(), "回忆照片原图", contentScale=ContentScale.Fit, cornerRadius=0.dp, original=true, useNaturalAspectRatio=false)
                }
            }
        }
    } }
}
