package dev.gestrin.hub

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gestrin.hub.data.*
import dev.gestrin.hub.runner.RunnerLauncher
import dev.gestrin.hub.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = HubRepository(app)
    private val prefs = app.getSharedPreferences("hub", 0)
    private val launcher = RunnerLauncher(app)
    val state = MutableStateFlow(HubState(dark = prefs.getBoolean("dark", true), reduceMotion = prefs.getBoolean("motion", false), language = prefs.getString("language", "zh") ?: "zh"))
    val dialog = MutableStateFlow<Pair<String, String>?>(null)
    val unknown = MutableStateFlow<List<ImportCandidate>>(emptyList())
    private var importJob: Job? = null
    private var rawGames = emptyList<Game>()
    private var sessions = emptyList<Session>()
    private var messageId = 0
    init {
        viewModelScope.launch { combine(repo.games, repo.sessions) { games, history -> games to history }.collect { (games, history) -> rawGames = games; sessions = history; refreshGames() } }
    }
    private fun refreshGames() {
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        state.update { it.copy(games = rawGames.map { g ->
            GameItem(g.id, g.title, g.platform, g.uri, g.isDirectory, g.favorite, g.coverUri, g.lastOpened,
                prefs.getString("title:${g.id}", "") ?: "",
                sessions.filter { s -> s.gameId == g.id }.take(5).map { s -> "${date.format(Date(s.startedAt))} · ${outcomeLabel(s.outcome)}\n${s.note.orEmpty()}" })
        }) }
    }
    private fun outcomeLabel(value: String) = when (value) { "FAILED_TO_START" -> "无法启动"; "STARTED" -> "能启动"; "PLAYABLE" -> "可交互"; else -> "仅记录交接，结果未验证" }
    fun tell(message: String) { state.update { it.copy(message = "${++messageId} · $message") } }
    fun task(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { dialog.value = "操作未完成" to (e.message ?: e.javaClass.simpleName) } } }
    fun importSource(uri: Uri, tree: Boolean) {
        if (importJob?.isActive == true) { tell("已有导入任务正在进行"); return }
        importJob = viewModelScope.launch {
            state.update { it.copy(loading = true) }
            try { showImport(if (tree) repo.scanTree(uri) else repo.importFile(uri)) }
            catch (e: CancellationException) { tell("导入已取消"); throw e }
            catch (e: Exception) { dialog.value = "导入未完成" to (e.message ?: "请检查目录授权。") }
            finally { state.update { it.copy(loading = false) } }
        }
    }
    private fun showImport(result: ImportResult) {
        unknown.value = unknown.value + result.needsPlatformSelection
        tell("已加入 ${result.added} 款，跳过 ${result.skipped} 个重复来源" + if (result.warnings.isNotEmpty()) "；部分来源需重新授权或手动检查" else "")
    }
    fun selectPlatform(platform: String?) {
        val candidate = unknown.value.firstOrNull() ?: return
        unknown.update { it.drop(1) }
        if (platform != null) task { showImport(repo.importFile(Uri.parse(candidate.uri), Platform.valueOf(platform))) }
    }
    fun cancelImport() { importJob?.cancel() }
    fun favorite(item: GameItem) = task { repo.setFavorite(item.id, !item.favorite) }
    fun save(item: GameItem, title: String, platform: String) = task { repo.getGame(item.id)?.let { repo.saveGame(it.copy(title = title.trim(), platform = platform)) }; tell("已保存") }
    fun delete(item: GameItem) = task { repo.deleteGame(item.id); prefs.edit().remove("title:${item.id}").apply(); tell("已移除库记录，原文件保留") }
    fun cover(item: GameItem, uri: Uri) = task {
        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        repo.getGame(item.id)?.let { repo.saveGame(it.copy(coverUri = uri.toString())) }
    }
    fun bindTitle(item: GameItem, titleId: String) {
        if (titleId.isNotEmpty() && !Regex("[A-Z]{4}[0-9]{5}").matches(titleId)) { tell("标题 ID 应为四个大写字母和五位数字"); return }
        prefs.edit().putString("title:${item.id}", titleId).apply(); refreshGames(); tell("已保存标题关联")
    }
    fun launch(item: GameItem, packageName: String) = task {
        val game = repo.getGame(item.id) ?: return@task
        val result = if (game.platform == "VITA" && item.titleId.isNotBlank()) launcher.launchVita(item.titleId, game.title, packageName) else launcher.launch(game, packageName)
        if (result.handedOff) repo.markOpened(game.id, packageName)
        tell(result.message)
    }
    fun record(item: GameItem, label: String, runner: String?, version: String?) = task {
        val outcome = when(label) { "无法启动" -> SessionOutcome.FAILED_TO_START; "能启动" -> SessionOutcome.STARTED; else -> SessionOutcome.PLAYABLE }
        val id = repo.markOpened(item.id, runner)
        repo.finishSession(id, "用户记录 · ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} / ${Build.HARDWARE} · 运行器 ${version ?: "未知版本"} · 未进行自动兼容性认证", outcome)
        tell("已记录本次观察结果")
    }
    fun diagnose(item: GameItem, packageName: String?, log: Uri? = null) = task {
        val game = repo.getGame(item.id) ?: return@task
        val report = HubDiagnostics(getApplication()).inspect(game, packageName, log)
        dialog.value = "本地规则诊断 · 未使用 AI 模型" to report.findings.joinToString("\n\n") { "${it.title}\n依据：${it.evidence}\n建议：${it.suggestion}" }
    }
    fun export(uri: Uri) = task { tell("已导出 ${repo.exportLibrary(uri)} 条游戏记录") }
    fun restore(uri: Uri) = task { showImport(repo.importLibrary(uri)); tell("备份已合并；来源目录可能需要重新授权") }
    fun toggleDark() { state.update { it.copy(dark = !it.dark) }; prefs.edit().putBoolean("dark", state.value.dark).apply() }
    fun toggleMotion() { state.update { it.copy(reduceMotion = !it.reduceMotion) }; prefs.edit().putBoolean("motion", state.value.reduceMotion).apply() }
    fun selectLanguage(language: String) {
        if (language !in setOf("zh", "en")) return
        prefs.edit().putString("language", language).apply()
        state.update { it.copy(language = language, message = "") }
    }
}
