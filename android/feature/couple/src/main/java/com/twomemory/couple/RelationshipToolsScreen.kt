package com.twomemory.couple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryIcons
import com.twomemory.designsystem.TwoMemoryTypography

enum class CoupleToolRoute { ANNIVERSARY, CAPSULE, EXPORT }

enum class ExportScope(val label: String, val description: String) {
    LOCAL_CACHE("本机已缓存的记录", "不依赖服务器，先导出这台手机当前能读到的内容"),
    DATE_RANGE("按时间范围", "由服务端生成可读文档、JSON 和所引用媒体"),
    ALL("全部回忆", "由服务端生成完整导出包，保留作者、版本和时间"),
}

data class AnniversaryDraft(val name: String, val date: String, val repeatsYearly: Boolean)
data class CapsuleDraft(val title: String, val body: String, val unlockDate: String)

@Composable
fun RelationshipToolsScreen(
    route: CoupleToolRoute,
    onBack: () -> Unit,
    onSaveAnniversary: (AnniversaryDraft) -> Unit = {},
    onSaveCapsule: (CapsuleDraft) -> Unit = {},
    onStartExport: (ExportScope) -> Unit = {},
    initialAnniversaryName: String = "我们的中秋",
    initialAnniversaryDate: String = "农历八月十五",
    initialAnniversaryRepeats: Boolean = true,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Icon(TwoMemoryIcons.Close, contentDescription = "返回我们")
                    Text("返回")
                }
                Text(
                    text = when (route) {
                        CoupleToolRoute.ANNIVERSARY -> "纪念日与倒计时"
                        CoupleToolRoute.CAPSULE -> "时间胶囊"
                        CoupleToolRoute.EXPORT -> "数据与导出"
                    },
                    style = TwoMemoryTypography.display,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            when (route) {
                CoupleToolRoute.ANNIVERSARY -> AnniversaryTool(
                    onSave = onSaveAnniversary,
                    initialName = initialAnniversaryName,
                    initialDate = initialAnniversaryDate,
                    initialRepeats = initialAnniversaryRepeats,
                )
                CoupleToolRoute.CAPSULE -> CapsuleTool(onSaveCapsule)
                CoupleToolRoute.EXPORT -> ExportTool(onStartExport)
            }
        }
    }
}

@Composable
private fun AnniversaryTool(
    onSave: (AnniversaryDraft) -> Unit,
    initialName: String,
    initialDate: String,
    initialRepeats: Boolean,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var date by rememberSaveable(initialDate) { mutableStateOf(initialDate) }
    var repeats by rememberSaveable(initialRepeats) { mutableStateOf(initialRepeats) }
    var saved by rememberSaveable { mutableStateOf(false) }
    ToolCard {
        Text("中秋，是你们的纪念日", style = TwoMemoryTypography.title)
        Text(
            "日期按农历规则计算，不把中秋硬编码成某一个公历日期。倒计时会使用空间和设备的真实时区。",
            style = TwoMemoryTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        )
        OutlinedTextField(value = name, onValueChange = { name = it; saved = false }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = date, onValueChange = { date = it; saved = false }, label = { Text("日期规则") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("每年重复", style = TwoMemoryTypography.body)
                Text("保存规则，不保存每天变化的剩余天数", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
            Switch(checked = repeats, onCheckedChange = { repeats = it; saved = false })
        }
        Button(onClick = { onSave(AnniversaryDraft(name.trim(), date.trim(), repeats)); saved = true }, enabled = name.isNotBlank() && date.isNotBlank()) {
            Text(if (saved) "已保存到本机 · 等待同步" else "保存纪念日")
        }
        if (saved) {
            Text("倒计时将在服务端规则接通后显示，不在这里伪造剩余天数。", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CapsuleTool(onSave: (CapsuleDraft) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var unlockDate by rememberSaveable { mutableStateOf("") }
    var locked by rememberSaveable { mutableStateOf(false) }
    ToolCard {
        Text("给未来的我们留一封信", style = TwoMemoryTypography.title)
        Text("开启日期前，正文不会在任何设备的详情页、通知或导出结果中返回。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        if (locked) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(TwoMemoryIcons.Capsule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("时间胶囊已锁定", style = TwoMemoryTypography.title)
                    Text("开启日期：$unlockDate", style = TwoMemoryTypography.body)
                    Text("已提交保存请求；正文已隐藏，等待服务端确认后再进入到期解锁流程。", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("信件标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("写给未来的正文") }, minLines = 7, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = unlockDate, onValueChange = { unlockDate = it }, label = { Text("开启日期，例如 2027-10-06") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(CapsuleDraft(title.trim(), body, unlockDate.trim())); locked = true }, enabled = title.isNotBlank() && body.isNotBlank() && unlockDate.isNotBlank()) {
                Text("保存并锁定")
            }
        }
    }
}

@Composable
private fun ExportTool(onStartExport: (ExportScope) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(ExportScope.LOCAL_CACHE.name) }
    var requested by rememberSaveable { mutableStateOf(false) }
    ToolCard {
        Text("把回忆带走", style = TwoMemoryTypography.title)
        Text("导出不包含登录令牌、配对密钥或长期下载地址。完整导出需要服务端生成 ZIP。", style = TwoMemoryTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        ExportScope.entries.forEach { scope ->
            FilterChip(
                selected = selected == scope.name,
                onClick = { selected = scope.name; requested = false },
                label = { Text(scope.label) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(scope.description, style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        }
        Button(onClick = { onStartExport(ExportScope.valueOf(selected)); requested = true }) {
            Text(if (requested) "已提交 · 等待生成" else "开始导出")
        }
        if (requested) Text("导出任务状态会在服务端回执后更新；当前页面不会伪造下载完成。", style = TwoMemoryTypography.caption, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ToolCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}
