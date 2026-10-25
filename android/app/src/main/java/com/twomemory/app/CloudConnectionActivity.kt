package com.twomemory.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twomemory.designsystem.TwoMemoryTheme
import com.twomemory.designsystem.TwoMemoryTypography
import com.twomemory.designsystem.TwoMemoryColors
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Existing binding flow, reachable without uninstalling or erasing the old database. */
class CloudConnectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fun returnToSpace() {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        setContent {
            TwoMemoryTheme {
                BackHandler { returnToSpace() }
                val model = remember {
                    SetupViewModel().apply {
                        updateServerUrl(ServerEndpointMigration.PUBLIC_ORIGIN)
                        updateDisplayName(SyncSession.loadNames(this@CloudConnectionActivity).own)
                    }
                }
                val scope = rememberCoroutineScope()
                var confirming by remember { mutableStateOf(false) }
                var switching by remember { mutableStateOf(false) }
                var switchError by remember { mutableStateOf<String?>(null) }
                BackHandler(enabled = switching) { /* Keep the binding form during verification. */ }
                val session = SyncSession.load(this@CloudConnectionActivity)
                val names = SyncSession.loadNames(this@CloudConnectionActivity)
                Column(Modifier.fillMaxSize().background(TwoMemoryColors.WarmBeigeBackground)
                    .windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TextButton(enabled = !switching, onClick = { returnToSpace() }) { Text("返回月笺") }
                    // An existing space needs transport verification, not a second
                    // simultaneous bootstrap/pair request that replaces its identity.
                    if (session == null) {
                        Text("连接云端空间", style = TwoMemoryTypography.title, modifier = Modifier.padding(horizontal = 24.dp))
                        Box(Modifier.weight(1f)) { SetupScreen(viewModel = model, onBound = { returnToSpace() }) }
                    } else {
                        CloudConnectionJournal(session, names, switching, switchError,
                            onSwitch = { confirming = true }, onReturn = { returnToSpace() })
                    }
                }
                if (confirming) AlertDialog(onDismissRequest = { confirming = false },
                    containerColor = TwoMemoryColors.WarmBeigeSurface,
                    shape = RoundedCornerShape(26.dp),
                    title = { Text("一起写下日常", style = TwoMemoryTypography.title) },
                    text = { Text("打开只属于你们的小空间。", style = TwoMemoryTypography.body) },
                    confirmButton = { TextButton(onClick = {
                        confirming = false; switching = true; switchError = null
                        scope.launch {
                            try {
                                ServerEndpointMigration.migrate(this@CloudConnectionActivity, ServerEndpointMigration.PUBLIC_ORIGIN)
                                runCatching { SyncSession.installProcessHooks(this@CloudConnectionActivity) }
                                returnToSpace()
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { switchError = "暂时没能连接，请稍后再试。" }
                            finally { switching = false }
                        }
                    }) { Text("打开手账") } }, dismissButton = { TextButton(onClick = { confirming = false }) { Text("再等等") } })
            }
        }
    }
}
