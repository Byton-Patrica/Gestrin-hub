package dev.gestrin.hub.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import dev.gestrin.hub.R

val platformNames = linkedMapOf("WINDOWS" to "Windows", "XP3" to "XP3", "PSP" to "PSP", "VITA" to "PS Vita", "SWITCH" to "Switch")
private val platformColors = mapOf("WINDOWS" to Color(0xFF8BB6DB), "XP3" to Color(0xFFD5ACCD), "PSP" to Color(0xFFB9D993), "VITA" to Color(0xFF9CB6E8), "SWITCH" to Color(0xFFE6AA91))
data class GameItem(val id: String, val title: String, val platform: String, val uri: String, val directory: Boolean, val favorite: Boolean, val cover: String?, val lastOpened: Long, val titleId: String = "", val history: List<String> = emptyList())
data class RunnerItem(val id: String, val name: String, val platform: String, val version: String?, val description: String, val status: String = "", val progress: Float? = null, val busy: Boolean = false, val ready: Boolean = false, val checksum: String? = null, val localApp: String? = null)
data class HubState(val games: List<GameItem> = emptyList(), val runners: List<RunnerItem> = emptyList(), val loading: Boolean = false, val message: String = "", val dark: Boolean = true, val reduceMotion: Boolean = false, val language: String = "zh")
data class HubActions(
    val importFile: () -> Unit, val importDirectory: () -> Unit, val cancelImport: () -> Unit,
    val favorite: (GameItem) -> Unit, val save: (GameItem, String, String) -> Unit, val delete: (GameItem) -> Unit,
    val cover: (GameItem) -> Unit, val launch: (GameItem) -> Unit, val bindTitle: (GameItem, String) -> Unit,
    val record: (GameItem, String) -> Unit, val diagnose: (GameItem) -> Unit, val log: (GameItem) -> Unit,
    val install: (String) -> Unit, val updateRunner: (String) -> Unit, val cancelDownload: (String) -> Unit, val openRunner: (String) -> Unit, val official: (String) -> Unit,
    val export: () -> Unit, val restore: () -> Unit, val toggleDark: () -> Unit, val toggleMotion: () -> Unit, val selectLanguage: (String) -> Unit,
    val chooseLocal: (String) -> Unit, val localApk: (String) -> Unit
)

@Composable
fun GestrinTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(primary = Color(0xFFC6F18B), onPrimary = Color(0xFF1D2C0C), secondaryContainer = Color(0xFF34412A), onSecondaryContainer = Color(0xFFD0EBAA), background = Color(0xFF101310), surface = Color(0xFF181D18), surfaceVariant = Color(0xFF252C25), onSurface = Color(0xFFEAEFE6), onSurfaceVariant = Color(0xFFA7B0A3))
    else lightColorScheme(primary = Color(0xFF426526), onPrimary = Color.White, secondaryContainer = Color(0xFFD8E9C5), onSecondaryContainer = Color(0xFF2A401D), background = Color(0xFFF6F8F1), surface = Color(0xFFEDF1E7), surfaceVariant = Color(0xFFE2E9DA), onSurface = Color(0xFF182116))
    MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(state: HubState, actions: HubActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { if (state.message.isNotBlank()) snackbar.showSnackbar(AppText.render(state.language, state.message.substringAfter(" · "))) }
    CompositionLocalProvider(LocalLanguage provides state.language) {
    GestrinTheme(state.dark) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val wide = maxWidth >= 840.dp
            Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.background) { NavItems(tab) { tab = it } }
            }, floatingActionButton = {
                if (tab == 0 && state.games.isNotEmpty()) ExtendedFloatingActionButton(onClick = { importing = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("添加游戏") }, containerColor = MaterialTheme.colorScheme.primary)
            }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxHeight().padding(top = 22.dp)) { NavRailItems(tab) { tab = it } }
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (wide) 32.dp else 20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ic_hub), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Text("Gestrin Hub", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (state.loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  正在导入…", Modifier.weight(1f)); TextButton(onClick = actions.cancelImport) { Text("取消") } }
                        }
                        AnimatedContent(tab, transitionSpec = { fadeIn(tween(if (state.reduceMotion) 0 else 180)) togetherWith fadeOut(tween(if (state.reduceMotion) 0 else 120)) }, label = "navigation") { page ->
                            when (page) {
                                0 -> Library(state, onSelect = { selected = it.id }, favorite = actions.favorite, onImport = { importing = true })
                                1 -> RunnerCenter(state.runners, actions)
                                else -> Settings(state, actions)
                            }
                        }
                    }
                    if (wide && tab == 0 && selected != null) {
                        state.games.find { it.id == selected }?.let { game ->
                            Surface(Modifier.width(340.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) { GameDetails(game, actions, onClose = { selected = null }) }
                        }
                    }
                }
            }
            if (!wide && selected != null) {
                state.games.find { it.id == selected }?.let { game -> ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { GameDetails(game, actions, onClose = { selected = null }) } }
            }
            if (importing) AlertDialog(onDismissRequest = { importing = false }, title = { Text("添加来源") }, text = { Text("目录需包含完整游戏资源。") }, confirmButton = { TextButton(onClick = { importing = false; actions.importDirectory() }) { Text("选择目录") } }, dismissButton = { TextButton(onClick = { importing = false; actions.importFile() }) { Text("选择文件") } })
        }
    }
    }
}

private val navLabels = listOf("游戏库", "运行器", "设置")
private val navIcons = listOf(Icons.Outlined.GridView, Icons.Outlined.Extension, Icons.Outlined.Tune)
@Composable private fun RowScope.NavItems(tab: Int, onTab: (Int) -> Unit) { navLabels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { onTab(i) }, icon = { Icon(navIcons[i], label) }, label = { Text(label) }) } }
@Composable private fun NavRailItems(tab: Int, onTab: (Int) -> Unit) { navLabels.forEachIndexed { i, label -> NavigationRailItem(selected = tab == i, onClick = { onTab(i) }, icon = { Icon(navIcons[i], label) }, label = { Text(label) }, modifier = Modifier.padding(bottom = 16.dp)) } }

@Composable
private fun Library(state: HubState, onSelect: (GameItem) -> Unit, favorite: (GameItem) -> Unit, onImport: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var recent by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(state.games, search, platform, onlyFavorites, recent) {
        state.games.filter { it.title.contains(search, true) && (platform.isBlank() || it.platform == platform) && (!onlyFavorites || it.favorite) }.let { if (recent) it.sortedByDescending { g -> g.lastOpened } else it.sortedBy { g -> g.title.lowercase() } }
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) {
        Text("游戏库", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(state.games.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } }
        if (state.games.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
        OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("搜索游戏") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = platform == "", onClick = { platform = "" }, label = { Text("全部") })
            platformNames.forEach { (key, label) -> FilterChip(selected = platform == key, onClick = { platform = key }, label = { Text(label) }) }
        }
        }
        if (state.games.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (onlyFavorites) "我的收藏" else "游戏仓库", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconToggleButton(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it }) { Icon(if (onlyFavorites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "只看收藏") }
            TextButton(onClick = { recent = !recent }) { Icon(Icons.Outlined.Sort, null, Modifier.size(16.dp)); Text(if (recent) " 最近" else " 名称") }
        }
        }
        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SportsEsports, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
                Text(if (state.games.isEmpty()) "暂无游戏" else "没有匹配的游戏", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                if (state.games.isEmpty()) FilledTonalButton(onClick = onImport, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text("添加游戏") }
            }
            }
        } else items(filtered, key = { it.id }) { game -> GameCard(game, { onSelect(game) }, { favorite(game) }) }
    }
}

@Composable private fun GameCard(game: GameItem, select: () -> Unit, favorite: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = select)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.79f).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf((platformColors[game.platform] ?: Color.Gray).copy(alpha = 0.26f), MaterialTheme.colorScheme.surface)))) {
            if (game.cover != null) AsyncImage(model = game.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) { Text(platformNames[game.platform] ?: game.platform, fontSize = 10.sp, letterSpacing = 2.sp); Text(game.title.take(1).uppercase(), fontSize = 76.sp, fontWeight = FontWeight.ExtraLight, color = platformColors[game.platform] ?: Color.Gray) }
            Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color(0xAA101310), shape = RoundedCornerShape(8.dp)) { Text(platformNames[game.platform] ?: game.platform, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
            IconButton(onClick = favorite, modifier = Modifier.align(Alignment.TopEnd)) { Icon(if (game.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "收藏 ${game.title}", tint = if (game.favorite) Color(0xFFC6F18B) else Color.White) }
        }
        Text(game.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp), translated = false)
    }
}

@Composable private fun GameDetails(game: GameItem, actions: HubActions, onClose: () -> Unit) {
    var title by remember(game.id, game.title) { mutableStateOf(game.title) }
    var platform by remember(game.id, game.platform) { mutableStateOf(game.platform) }
    var confirmDelete by remember { mutableStateOf(false) }
    var titleId by remember(game.id, game.titleId) { mutableStateOf(game.titleId) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("游戏详情", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "关闭详情") } }
        Text("未验证兼容性", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { platformNames.forEach { (key, label) -> FilterChip(platform == key, { platform = key }, label = { Text(label) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = { actions.save(game, title, platform) }, enabled = title.isNotBlank()) { Text("保存修改") }; OutlinedButton(onClick = { actions.cover(game) }) { Text("更换封面") } }
        HorizontalDivider()
        Text(if (game.directory) "项目目录" else "游戏文件", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(game.uri, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (game.platform == "VITA") {
            OutlinedTextField(titleId, { titleId = it.uppercase().take(9) }, label = { Text("Vita 已安装标题 ID（可选）") }, placeholder = { Text("例如 PCSG00000") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { actions.bindTitle(game, titleId) }) { Text("保存标题关联") }
        }
        Button(onClick = { actions.launch(game) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PlayArrow, null); Text("启动方式") }
        if (game.platform in listOf("WINDOWS", "XP3")) Text("在运行器内选择游戏。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (game.platform == "VITA") Text("请先在 Vita3K 安装该标题。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("诊断", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { actions.diagnose(game) }) { Text("检查启动条件") }; OutlinedButton(onClick = { actions.log(game) }) { Text("导入日志") } }
        Text("测试记录", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("无法启动", "能启动", "可交互").forEach { result -> SuggestionChip(onClick = { actions.record(game, result) }, label = { Text(result) }) } }
        game.history.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, null); Text("从仓库移除") }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("移除这款游戏？") }, text = { Text("只移除仓库记录，保留原始游戏文件。") }, confirmButton = { TextButton(onClick = { actions.delete(game); confirmDelete = false; onClose() }) { Text("移除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RunnerCenter(runners: List<RunnerItem>, actions: HubActions) {
    var packageInfo by remember { mutableStateOf<RunnerItem?>(null) }
    var runnerInfo by remember { mutableStateOf<RunnerItem?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Text("运行器", fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
        items(runners, key = { it.id }) { runner ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background((platformColors[runner.platform] ?: Color.Gray).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SportsEsports, null, tint = platformColors[runner.platform] ?: Color.Gray) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(runner.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp); Text("${platformNames[runner.platform]} · ${runner.version?.let { "已安装 $it" } ?: "未安装"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (runner.localApp != null) { Text("本机应用", style = MaterialTheme.typography.labelSmall); Text(runner.localApp, style = MaterialTheme.typography.bodySmall, translated = false) }
                    if (runner.status.isNotBlank() && runner.status != "按需下载 · 未内置运行器") Text(if (runner.ready) "安装包已就绪" else runner.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    if (runner.busy) { if (runner.progress != null) LinearProgressIndicator(progress = { runner.progress }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (runner.busy) OutlinedButton(onClick = { actions.cancelDownload(runner.id) }) { Text("取消下载") }
                        else Button(onClick = { actions.install(runner.id) }) { Icon(if (runner.ready) Icons.Outlined.InstallMobile else Icons.Outlined.Download, null, Modifier.size(17.dp)); Text(if (runner.ready) "继续安装" else if (runner.version != null) "检查更新" else "下载安装") }
                        if (runner.localApp != null) OutlinedButton(onClick = { actions.openRunner(runner.id) }) { Text("打开") }
                        OutlinedButton(onClick = { actions.chooseLocal(runner.platform) }) { Text("选择本机应用") }
                        if (!runner.busy) TextButton(onClick = { actions.localApk(runner.id) }) { Text("本地 APK") }
                        TextButton(onClick = { actions.official(runner.id) }) { Text("上游") }
                        IconButton(onClick = { runnerInfo = runner }) { Icon(Icons.Outlined.Info, "详情", Modifier.size(19.dp)) }
                    }
                    if (runner.ready) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { actions.updateRunner(runner.id) }) { Text("检查更新") }
                        TextButton(onClick = { actions.cancelDownload(runner.id) }) { Text("清理缓存") }
                        TextButton(onClick = { packageInfo = runner }) { Text("校验信息") }
                    }
                }
            }
        }
    }
    if (runnerInfo != null) AlertDialog(onDismissRequest = { runnerInfo = null }, title = { Text(runnerInfo!!.name) }, text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(runnerInfo!!.description); if (runnerInfo!!.status.startsWith("下载或校验失败")) Text(runnerInfo!!.status); Text("安装需由 Android 系统确认。", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { runnerInfo = null }) { Text("关闭") } })
    if (packageInfo != null) AlertDialog(onDismissRequest = { packageInfo = null }, title = { Text("${packageInfo!!.name} 安装包") }, text = { androidx.compose.foundation.text.selection.SelectionContainer { Text("${packageInfo!!.status}\n\n已下载文件的 SHA-256：\n${packageInfo!!.checksum ?: "未取得"}\n\n首次安装的发布者证书尚未独立锁定；系统安装器会校验 APK 签名。") } }, confirmButton = { TextButton(onClick = { packageInfo = null }) { Text("关闭") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Settings(state: HubState, actions: HubActions) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("设置", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        SettingsGroup("语言") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = state.language == "zh", onClick = { actions.selectLanguage("zh") }, label = { Text("中文", translated = false) })
                FilterChip(selected = state.language == "en", onClick = { actions.selectLanguage("en") }, label = { Text("English", translated = false) })
            }
        }
        SettingsGroup("外观") {
            ToggleRow("深色主题", "", state.dark, actions.toggleDark)
            ToggleRow("减少动画", "", state.reduceMotion, actions.toggleMotion)
        }
        SettingsGroup("备份") {
            Text("备份仅含游戏库，不含文件、授权、测试记录或标题关联。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = actions.export) { Icon(Icons.Outlined.Upload, null, Modifier.size(16.dp)); Text(" 导出备份") }; OutlinedButton(onClick = actions.restore) { Text("恢复备份") } }
        }
        SettingsGroup("本地诊断") { Text("规则诊断；模型未启用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SettingsGroup("关于") { Text("Gestrin Hub · 0.1.0-preview.2", fontWeight = FontWeight.Medium); Text("运行器和依赖按需下载。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(title, fontWeight = FontWeight.SemiBold); content() } } }
@Composable private fun ToggleRow(title: String, hint: String, checked: Boolean, toggle: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title); if (hint.isNotBlank()) Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, { toggle() }) } }
