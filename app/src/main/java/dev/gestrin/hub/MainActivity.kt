package dev.gestrin.hub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gestrin.hub.ui.*
import dev.gestrin.hub.ui.Text
import dev.gestrin.hub.ui.Icon
import dev.gestrin.hub.runner.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: HubViewModel = viewModel()
            val base by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(base.dark) {
                val style = if (base.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
            val installers = remember { RunnerInstallUi(applicationContext) }
            val runners by installers.items.collectAsStateWithLifecycle()
            val state = base.copy(runners = runners)
            val dialog by vm.dialog.collectAsStateWithLifecycle()
            val unknown by vm.unknown.collectAsStateWithLifecycle()
            var coverTarget by remember { mutableStateOf<GameItem?>(null) }
            var logTarget by remember { mutableStateOf<GameItem?>(null) }
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importSource(it, false) } }
            val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.importSource(it, true) } }
            val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) coverTarget?.let { vm.cover(it, uri) } }
            val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) logTarget?.let { vm.diagnose(it, installers.packageFor(it.platform), uri) } }
            val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
            val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restore) }
            DisposableEffect(installers) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) installers.refresh()
                    if (event == Lifecycle.Event.ON_PAUSE) installers.pause()
                }
                lifecycle.addObserver(observer)
                installers.start()
                onDispose { lifecycle.removeObserver(observer); installers.close() }
            }
            val actions = HubActions(
                importFile = { filePicker.launch(arrayOf("*/*")) }, importDirectory = { directoryPicker.launch(null) }, cancelImport = vm::cancelImport,
                favorite = vm::favorite, save = vm::save, delete = vm::delete,
                cover = { coverTarget = it; coverPicker.launch(arrayOf("image/*")) },
                launch = { item -> val pkg = installers.packageFor(item.platform); if (pkg == null) vm.tell("先到运行器中心安装对应应用") else vm.launch(item, pkg) },
                bindTitle = vm::bindTitle,
                record = { item, outcome -> vm.record(item, outcome, installers.packageFor(item.platform), runners.find { it.platform == item.platform }?.version) },
                diagnose = { vm.diagnose(it, installers.packageFor(it.platform)) }, log = { logTarget = it; logPicker.launch(arrayOf("text/*", "application/octet-stream")) },
                install = { installers.install(this@MainActivity, it) { message -> vm.dialog.value = "运行器安装" to message } },
                updateRunner = { installers.install(this@MainActivity, it, forceUpdate = true) { message -> vm.dialog.value = "运行器安装" to message } },
                cancelDownload = installers::cancel,
                openRunner = { id -> installers.open(this@MainActivity, id) { vm.tell(it) } },
                official = { id -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.get(RunnerId.valueOf(id)).officialPage))) },
                export = { exportPicker.launch("gestrin-library.json") }, restore = { restorePicker.launch(arrayOf("application/json", "text/*")) }, toggleDark = vm::toggleDark, toggleMotion = vm::toggleMotion, selectLanguage = vm::selectLanguage
            )
            HubScreen(state, actions)
            CompositionLocalProvider(LocalLanguage provides state.language) { GestrinTheme(state.dark) {
                if (dialog != null) AlertDialog(
                    onDismissRequest = { vm.dialog.value = null },
                    title = { Text(dialog!!.first) },
                    text = { Text(dialog!!.second, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
                    confirmButton = { TextButton(onClick = { vm.dialog.value = null }) { Text("知道了") } }
                )
                if (unknown.isNotEmpty()) {
                    var choice by remember(unknown.first().uri) { mutableStateOf("PSP") }
                    AlertDialog(onDismissRequest = { vm.selectPlatform(null) }, title = { Text("选择平台") }, text = {
                        Column { Text(unknown.first().title, translated = false); Text("无法仅凭文件名确定平台，请手动选择。", style = MaterialTheme.typography.bodySmall); platformNames.forEach { (key, label) ->
                            Row { RadioButton(choice == key, { choice = key }); TextButton(onClick = { choice = key }) { Text(label) } }
                        } }
                    }, confirmButton = { TextButton(onClick = { vm.selectPlatform(choice) }) { Text("加入仓库") } }, dismissButton = { TextButton(onClick = { vm.selectPlatform(null) }) { Text("跳过") } })
                }
            }
            }
        }
    }
}
