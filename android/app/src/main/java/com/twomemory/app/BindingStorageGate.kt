package com.twomemory.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Existing credentials that cannot be read are not a new installation. */
@Composable internal fun bindingStorageAvailable(): Boolean {
    val context=LocalContext.current
    var retry by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var issued by remember { mutableStateOf<SyncSession.Session?>(null) }
    val scope=rememberCoroutineScope()
    val available=retry.let { SyncSession.load(context)!=null || !SyncSession.sessionPrefs(context).contains("token") }
    if(!available) Column(Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),verticalArrangement=Arrangement.Center) {
        Text("你的空间与本机记录已保留")
        Text("设备安全存储暂时无法读取。请先解锁手机后重试；不要卸载、清数据或重新初始化。",modifier=Modifier.padding(vertical=16.dp))
        TextButton(onClick={retry++},enabled=!busy) { Text("重新读取") }
        Text("若密钥已失效，可让对方在原空间生成找回配对码。只有身份核验一致才恢复，不移动或清空本机内容。")
        OutlinedTextField(value=code,onValueChange={code=it},label={Text("原空间的找回配对码")},enabled=!busy && issued==null)
        TextButton(enabled=!busy && (code.isNotBlank() || issued!=null),onClick={scope.launch {
            busy=true; recoveryError=null
            try {
                val session=issued ?: BindingRecovery.request(context,code).also { issued=it }
                BindingRecovery.save(context,session)
                retry++
            } catch (cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error:Exception) { recoveryError=error.message ?: "找回未完成，原内容仍保留" }
            finally { busy=false }
        }}) { Text(if(busy) "核验中…" else if(issued!=null) "重试保存原身份" else "找回原身份") }
        recoveryError?.let { Text(it) }
    }
    return available
}
