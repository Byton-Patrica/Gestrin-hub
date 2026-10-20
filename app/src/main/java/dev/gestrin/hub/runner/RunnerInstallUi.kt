package dev.gestrin.hub.runner

import android.app.Activity
import android.content.Context
import dev.gestrin.hub.ui.RunnerItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Foreground orchestration only. Network transfers themselves belong to DownloadManager. */
class RunnerInstallUi(private val app: Context) {
    val local = LocalRunners(app)
    private val backend = Installer(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableItems = MutableStateFlow<List<RunnerItem>>(emptyList())
    val items = mutableItems.asStateFlow()
    private var polling: Job? = null
    private var foreground = true
    private var activity: Activity? = null
    private var pending: RunnerId? = null
    private var installerDispatched = false
    private var dispatching = false
    private var waitingForPermission = false
    private var requestGeneration = 0L
    private var notify: ((String) -> Unit)? = null
    private val platform = mapOf(RunnerId.WINLATOR to "WINDOWS", RunnerId.KIRIKIROID2 to "XP3", RunnerId.PPSSPP to "PSP", RunnerId.VITA3K to "VITA", RunnerId.EDEN to "SWITCH")
    private val descriptions = mapOf(
        RunnerId.WINLATOR to "Wine 与 Box 兼容环境。容器和游戏快捷方式在 Winlator 内配置；当前提供运行器入口。",
        RunnerId.KIRIKIROID2 to "官方 1.3.9 面向 API 11，Android 14 及以上不能新安装。请绑定兼容的本机应用，或选择已适配系统的本地 APK。",
        RunnerId.PPSSPP to "PSP 运行器。支持尝试交接所选游戏文件，实际表现按设备和游戏验证。",
        RunnerId.VITA3K to "PS Vita 实验性运行器。先在其中安装游戏，再关联标题 ID。",
        RunnerId.EDEN to "Switch 运行器。使用上游标准 Android 构建；初始化与用户已有文件在 Eden 内配置。"
    )
    fun start() {
        if (polling != null) return
        scope.launch { backend.states.collect { rebuild() } }
        polling = scope.launch {
            while (isActive) {
                if (foreground) {
                    try { backend.refresh(); rebuild(); maybeInstall() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
                delay(1200)
            }
        }
    }
    fun refresh() {
        foreground = true
        if (installerDispatched) { pending = null; installerDispatched = false }
        if (waitingForPermission) {
            waitingForPermission = false
            if (!app.packageManager.canRequestPackageInstalls()) {
                pending = null
                notify?.invoke("尚未允许安装应用。安装包已保留，需要时可再次点击继续安装。")
            }
        }
        scope.launch { backend.refresh(); rebuild(); maybeInstall() }
    }
    fun pause() { foreground = false }
    private fun installed(spec: RunnerSpec): Pair<String, String>? {
        for (pkg in spec.expectedPackages) {
            try { val info = app.packageManager.getPackageInfo(pkg, 0); return pkg to (info.versionName ?: "未知版本") } catch (_: android.content.pm.PackageManager.NameNotFoundException) { }
        }
        return null
    }
    fun packageFor(category: String): String? = local.selected(category)?.packageName
    fun bind(category: String, pkg: String?) { local.bind(category, pkg); requestGeneration++; pending = null; rebuild() }
    private fun rebuild() {
        mutableItems.value = Catalog.runners.map { spec ->
            val state = backend.states.value[spec.id] ?: RunnerState()
            val status = when (state.phase) {
                RunnerPhase.IDLE -> "按需下载 · 未内置运行器"
                RunnerPhase.RESOLVING -> "正在查询上游发行版…"
                RunnerPhase.DOWNLOADING -> "正在下载 ${state.releaseName.orEmpty()} · ${state.downloadedBytes / 1048576} MB" + if (state.totalBytes > 0) " / ${state.totalBytes / 1048576} MB" else ""
                RunnerPhase.PAUSED -> "下载已暂停，等待网络或系统资源"
                RunnerPhase.VERIFYING -> "正在检查安装包身份与完整性…"
                RunnerPhase.READY -> "安装包已就绪 · " + if (state.hashVerified) "SHA-256 与上游一致" else if (state.releaseName == "Local APK") "本地 APK 已检查身份与设备要求；发布者哈希未验证" else "上游未提供可核验哈希；已检查包名，更新时核对已安装签名"
                RunnerPhase.CANCELLED -> "已取消，可重新下载"
                RunnerPhase.FAILED -> "下载或校验失败：${state.error ?: "请重试或查看上游"}"
            }
            RunnerItem(spec.id.name, spec.name, platform.getValue(spec.id), installed(spec)?.second, descriptions.getValue(spec.id), status,
                if (state.totalBytes > 0) (state.downloadedBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else null,
                state.phase in setOf(RunnerPhase.RESOLVING, RunnerPhase.DOWNLOADING, RunnerPhase.PAUSED, RunnerPhase.VERIFYING), state.phase == RunnerPhase.READY, state.computedSha256,
                local.selected(platform.getValue(spec.id))?.let { "${it.label} · ${it.version}" })
        }
    }
    fun install(host: Activity, id: String, forceUpdate: Boolean = false, onMessage: (String) -> Unit) {
        requestGeneration++
        activity = host; notify = onMessage; pending = RunnerId.valueOf(id); installerDispatched = false; waitingForPermission = false
        scope.launch {
            try {
                val runner = pending ?: return@launch
                if (forceUpdate) backend.retry(runner) else when (backend.states.value[runner]?.phase) {
                    RunnerPhase.READY -> maybeInstall()
                    RunnerPhase.FAILED, RunnerPhase.CANCELLED -> backend.retry(runner)
                    else -> backend.download(runner)
                }
                rebuild(); maybeInstall()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { onMessage(e.message ?: "下载安装未完成，请重试。") }
        }
    }
    fun importApk(host: Activity, id: String, uri: android.net.Uri, onMessage: (String) -> Unit) {
        requestGeneration++
        activity = host; notify = onMessage; pending = RunnerId.valueOf(id)
        installerDispatched = false; waitingForPermission = false
        val runner = RunnerId.valueOf(id)
        scope.launch {
            try { backend.importApk(runner, uri); rebuild(); maybeInstall() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { pending = null; onMessage(e.message ?: "无法读取安装包") }
        }
    }
    private suspend fun maybeInstall() {
        val runner = pending ?: return
        val host = activity ?: return
        if (!foreground || dispatching || installerDispatched || host.isFinishing || host.isDestroyed || backend.states.value[runner]?.phase != RunnerPhase.READY) return
        dispatching = true
        val generation = requestGeneration
        try {
            val action = backend.prepareInstall(runner)
            if (!foreground || activity !== host || pending != runner || requestGeneration != generation || host.isFinishing || host.isDestroyed) return
            when (action) {
                is InstallAction.Launch -> { installerDispatched = true; host.startActivity(action.intent) }
                is InstallAction.UnknownSources -> { waitingForPermission = true; foreground = false; host.startActivity(action.intent) }
                is InstallAction.Unavailable -> { pending = null; notify?.invoke(action.reason) }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { pending = null; notify?.invoke(e.message ?: "系统安装器暂不可用。") }
        finally { dispatching = false }
    }
    fun cancel(id: String) { val runner = RunnerId.valueOf(id); requestGeneration++; if (pending == runner) pending = null; scope.launch { backend.cancel(runner); rebuild() } }
    fun open(host: Activity, id: String, onMessage: (String) -> Unit) {
        val pkg = packageFor(LocalRunners.platformFor(RunnerId.valueOf(id)))
        val intent = pkg?.let(app.packageManager::getLaunchIntentForPackage)
        if (intent == null) onMessage("运行器未安装或没有可用入口")
        else try { host.startActivity(intent) } catch (_: Exception) { onMessage("运行器未能打开，请检查系统状态") }
    }
    fun close() { requestGeneration++; pending = null; activity = null; notify = null; scope.cancel(); backend.close() }
}
