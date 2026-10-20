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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

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
            var launchTarget by rememberSaveable { mutableStateOf<String?>(null) }
            var bindingPlatform by rememberSaveable { mutableStateOf<String?>(null) }
            var apkTarget by rememberSaveable { mutableStateOf<String?>(null) }
            var localApps by remember { mutableStateOf<List<LocalRunner>>(emptyList()) }
            var appsLoading by remember { mutableStateOf(false) }
            var localRevision by remember { mutableIntStateOf(0) }
            LaunchedEffect(bindingPlatform) {
                if (bindingPlatform != null) {
                    appsLoading = true
                    localApps = emptyList()
                    try { localApps = withContext(Dispatchers.IO) { installers.local.list() } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { vm.dialog.value = "操作未完成" to "无法读取本机应用，请稍后重试。" }
                    finally { appsLoading = false }
                }
            }
            val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val id = apkTarget; apkTarget = null
                if (uri != null && id != null) installers.importApk(this@MainActivity, id, uri) { vm.dialog.value = "运行器安装" to it }
            }
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
                launch = { item -> launchTarget = item.id },
                bindTitle = vm::bindTitle,
                record = { item, outcome -> vm.record(item, outcome, installers.packageFor(item.platform), runners.find { it.platform == item.platform }?.version) },
                diagnose = { vm.diagnose(it, installers.packageFor(it.platform)) }, log = { logTarget = it; logPicker.launch(arrayOf("text/*", "application/octet-stream")) },
                install = { installers.install(this@MainActivity, it) { message -> vm.dialog.value = "运行器安装" to message } },
                updateRunner = { installers.install(this@MainActivity, it, forceUpdate = true) { message -> vm.dialog.value = "运行器安装" to message } },
                cancelDownload = installers::cancel,
                openRunner = { id -> installers.open(this@MainActivity, id) { vm.tell(it) } },
                official = { id -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Catalog.get(RunnerId.valueOf(id)).officialPage))) },
                export = { exportPicker.launch("gestrin-library.json") }, restore = { restorePicker.launch(arrayOf("application/json", "text/*")) }, toggleDark = vm::toggleDark, toggleMotion = vm::toggleMotion, selectLanguage = vm::selectLanguage,
                chooseLocal = { bindingPlatform = it }, localApk = { apkTarget = it; apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
            )
            HubScreen(state, actions)
            CompositionLocalProvider(LocalLanguage provides state.language) { GestrinTheme(state.dark) {
                state.games.find { it.id == launchTarget }?.let { game ->
                    val selected = remember(game.platform, runners, localRevision) { installers.local.selected(game.platform) }
                    if (bindingPlatform == null) LaunchDialog(game, selected,
                        send = { launchTarget = null; selected?.let { vm.launch(game, it.packageName) } },
                        chooser = { launchTarget = null; vm.chooseFile(game) },
                        open = { launchTarget = null; selected?.let { vm.openRunner(it.packageName) } },
                        bind = { bindingPlatform = game.platform }, close = { launchTarget = null })
                }
                bindingPlatform?.let { platform -> LocalRunnerDialog(localApps, appsLoading,
                    choose = { pkg ->
                        try { installers.bind(platform, pkg); localRevision++ }
                        catch (_: Exception) { vm.dialog.value = "操作未完成" to "所选应用已不可用，请重新选择。" }
                        bindingPlatform = null
                    }, close = { bindingPlatform = null }) }
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
