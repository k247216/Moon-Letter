package com.twomemory.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryColors as Colors
import com.twomemory.designsystem.TwoMemoryIcons as Icons
import com.twomemory.designsystem.TwoMemoryTypography as Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presentation only: no initialization, pairing, network checks or storage writes. */
@Composable
internal fun CloudConnectionJournal(
    session: SyncSession.Session,
    names: SyncSession.Names,
    busy: Boolean,
    error: String?,
    onSwitch: () -> Unit,
    onReturn: () -> Unit,
) {
    val canSwitch = session.baseUrl == ServerEndpointMigration.USB_ORIGIN
    var details by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Text("我们的小空间", style = Type.display, color = Colors.WarmBeigeInk)
            Text("记录彼此想念的每一天", style = Type.caption,
                color = Colors.WarmBeigeMuted, modifier = Modifier.padding(top = 7.dp))
            Row(Modifier.padding(vertical = 23.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(25.dp)) {
                ConnectionPortrait(names.own.ifBlank { "我" }, "ownAvatar")
                Surface(shape = RoundedCornerShape(18.dp), color = Colors.WarmBeigeAccentSoft.copy(alpha = .55f)) {
                    Icon(Icons.Letter, contentDescription = "连接两个人的小信封",
                        tint = Colors.WarmBeigeAccent, modifier = Modifier.padding(14.dp).size(30.dp))
                }
                ConnectionPortrait(names.partner.ifBlank { "对方" }, "partnerAvatar")
            }
            Surface(shape = RoundedCornerShape(23.dp), color = Colors.WarmBeigeSurface,
                border = BorderStroke(1.dp, Colors.WarmBeigeLine), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("给你的一页小日常", style = Type.title, color = Colors.WarmBeigeInk)
                    Text("今天的小事，也想和你分享。", style = Type.body, color = Colors.WarmBeigeMuted)
                    HorizontalDivider(color = Colors.WarmBeigeLine.copy(alpha = .6f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        JournalMotif(Icons.Edit, "随记")
                        JournalMotif(Icons.Album, "照片")
                        JournalMotif(Icons.Voice, "声音")
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            if (canSwitch) {
                Button(onClick = onSwitch, enabled = !busy, shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Colors.WarmBeigeAccent),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Colors.WarmBeigeSurface)
                        Spacer(Modifier.width(9.dp))
                    } else {
                        Icon(Icons.Letter, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(if (busy) "正在打开…" else "打开我们的手账", style = Type.button)
                }
            } else {
                Button(onClick = onReturn, enabled = !busy, shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Colors.WarmBeigeAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                    Text("打开我们的手账", style = Type.button)
                }
            }
            error?.let {
                Text(it, style = Type.caption, color = Colors.WarmBeigeAccent,
                    modifier = Modifier.padding(top = 12.dp))
            }
            TextButton(onClick = { details = !details }, enabled = !busy,
                modifier = Modifier.padding(top = 8.dp)) {
                Text(if (details) "收起详情" else "连接详情", style = Type.caption, color = Colors.WarmBeigeMuted)
                Icon(Icons.Expand, null, Modifier.padding(start = 4.dp).size(17.dp), tint = Colors.WarmBeigeMuted)
            }
            AnimatedVisibility(details) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(Colors.WarmBeigeNav).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前服务地址", style = Type.caption, color = Colors.WarmBeigeMuted)
                    Text(session.baseUrl, style = Type.caption, color = Colors.WarmBeigeInk)
                }
            }
        }
    }
}

@Composable
private fun JournalMotif(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Colors.WarmBeigeNav), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = Colors.WarmBeigeSage)
        }
        Text(title, style = Type.caption, color = Colors.WarmBeigeMuted)
    }
}

@Composable
private fun ConnectionPortrait(name: String, avatarKey: String) {
    val context = LocalContext.current
    val uri = remember(context, avatarKey) {
        context.getSharedPreferences("moon_letter_profile", Context.MODE_PRIVATE).getString(avatarKey, null)
    }
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            uri?.let { runCatching {
                context.contentResolver.openInputStream(Uri.parse(it)).use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
                }
            }.getOrNull() }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(74.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Colors.WarmBeigeNav), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!, contentDescription = "$name 的头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(name.take(1), style = Type.title, color = Colors.WarmBeigeMuted)
        }
        Text(name, style = Type.caption, color = Colors.WarmBeigeInk, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
    }
}
