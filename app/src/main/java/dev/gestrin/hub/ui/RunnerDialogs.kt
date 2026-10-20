package dev.gestrin.hub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gestrin.hub.runner.LaunchPolicy
import dev.gestrin.hub.runner.LocalRunner

@Composable
fun LaunchDialog(game: GameItem, runner: LocalRunner?, send: () -> Unit, chooser: () -> Unit,
                 open: () -> Unit, bind: () -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("启动方式") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(game.title, translated = false)
            if (runner != null) Text(runner.label, translated = false) else Text("尚未选择本机运行器")
            if (LaunchPolicy.hasVerifiedContract(game.platform, game.directory, game.titleId)) {
                Text("交接只发送启动请求，不代表游戏运行成功。", style = MaterialTheme.typography.bodySmall)
                if (runner != null) Button(onClick = send, modifier = Modifier.fillMaxWidth()) { Text("交给已选运行器") }
            } else Text("此项目需在运行器内选择游戏；不会自动跳转。", style = MaterialTheme.typography.bodySmall)
            if (!game.directory) OutlinedButton(onClick = chooser, modifier = Modifier.fillMaxWidth()) { Text("选择接收此文件的应用") }
            if (runner != null) OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text("仅打开本地运行器") }
            TextButton(onClick = bind) { Text("选择本机应用") }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("关闭") } })
}

@Composable
fun LocalRunnerDialog(apps: List<LocalRunner>, loading: Boolean, choose: (String?) -> Unit, close: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("选择本机应用") }, text = {
        Column {
            Text("仅建立平台关联，不会立即启动；应用需自行支持游戏格式。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(filter, { filter = it }, label = { Text("搜索应用") }, singleLine = true)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!loading && apps.isEmpty()) Text("未找到可启动的本机应用")
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(apps.filter { it.label.contains(filter, true) || it.packageName.contains(filter, true) }, key = { it.packageName }) { app ->
                    TextButton(onClick = { choose(app.packageName) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) { Text(app.label, translated = false); Text(app.packageName, style = MaterialTheme.typography.labelSmall, translated = false) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("关闭") } }, dismissButton = { TextButton(onClick = { choose(null) }) { Text("清除绑定") } })
}
