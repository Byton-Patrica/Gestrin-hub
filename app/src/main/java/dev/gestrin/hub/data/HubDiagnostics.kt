package dev.gestrin.hub.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Deterministic local observations only. Reads only the source metadata and a user-selected log.
 * Never executes log instructions, invokes a model, launches apps or changes runner settings.
 * Package inspection is constrained by Android package visibility; configure runner <queries>
 * in the app manifest where needed. A missing package result is deliberately inconclusive.
 */
class HubDiagnostics(context: Context) {
    private val appContext = context.applicationContext

    suspend fun inspect(
        game: Game,
        runnerPackage: String? = null,
        importedLogUri: Uri? = null
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<DiagnosticFinding>()
        fun add(code: String, severity: DiagnosticSeverity, title: String, evidence: String, suggestion: String) {
            findings.add(DiagnosticFinding(code, severity, title, evidence, suggestion))
        }
        currentCoroutineContext().ensureActive()
        try {
            val uri = validatedUri(game.uri)
            val document = sourceDocument(appContext, uri)
            when {
                document == null || !document.exists() -> add(
                    "SOURCE_UNAVAILABLE", DiagnosticSeverity.ERROR, "来源不可用",
                    "文件提供方无法确认所选来源仍然存在。",
                    "请使用系统选择器重新选择来源，并确认文件或目录未被移动。"
                )
                !document.canRead() -> add(
                    "SOURCE_PERMISSION", DiagnosticSeverity.ERROR, "无法读取来源",
                    "所选文档未向 Hub 提供读取权限。",
                    "请通过系统选择器重新授予读取权限。"
                )
                document.isDirectory != game.isDirectory -> add(
                    "SOURCE_KIND_CHANGED", DiagnosticSeverity.ERROR, "来源类型已变化",
                    "文件提供方报告的文件或目录类型与游戏库记录不一致。",
                    "请重新选择正确的文件或完整项目目录。"
                )
                game.isDirectory -> add(
                    "DIRECTORY_ACCESS", DiagnosticSeverity.INFO, "可以访问项目目录",
                    "Hub 可以读取目录信息；尚未验证目录内容是否完整。",
                    "请保留脚本、归档及相邻资源，并在运行器中单独授予访问权限。"
                )
                else -> {
                    // Confirm that an actual stream can be opened, without reading game content.
                    val firstByte = (appContext.contentResolver.openInputStream(uri)
                        ?: throw IOException("Source stream unavailable.")).use { it.read() }
                    if (firstByte == -1) add(
                        "SOURCE_EMPTY", DiagnosticSeverity.WARNING, "来源文件为空",
                        "读取来源时未获得任何字节，文件流已结束。",
                        "请检查所选文件，或等待下载完成。"
                    ) else add(
                        "SOURCE_READABLE", DiagnosticSeverity.INFO, "Hub 可以读取来源",
                        "已成功打开来源并读取一个字节；格式与兼容性仍未验证。",
                        "外部运行器可能仍需单独选择授权或导入此来源。"
                    )
                    val detected = SafTreeScanner.detectPlatform(document.name ?: "")
                    if (detected != null && detected.name != game.platform) add(
                        "PLATFORM_EXTENSION", DiagnosticSeverity.WARNING, "请核对所选平台",
                        "文件扩展名提示可能属于 ${detected.label}，与游戏库中手动选择的分类不同。",
                        "请确认平台分类。扩展名仅供参考，不能证明真实文件格式。"
                    )
                }
            }
        } catch (_: SecurityException) {
            add("SOURCE_PERMISSION", DiagnosticSeverity.ERROR, "来源访问被拒绝",
                "Android 拒绝了 Hub 对来源的访问。", "请使用系统选择器重新选择来源。")
        } catch (_: IOException) {
            add("SOURCE_IO", DiagnosticSeverity.ERROR, "无法打开来源",
                "文件提供方无法打开来源文件流。",
                "请确认文档已下载到本地、仍然存在且可以读取。")
        } catch (_: IllegalArgumentException) {
            add("SOURCE_INVALID", DiagnosticSeverity.ERROR, "来源 URI 无效",
                "已保存的来源不是受支持的本地文档 URI。",
                "请使用系统选择器重新选择来源。")
        }

        currentCoroutineContext().ensureActive()
        when {
            runnerPackage == null -> add(
                "RUNNER_NOT_SELECTED", DiagnosticSeverity.WARNING, "尚未选择运行器",
                "本次检查未提供运行器包名。", "请为此游戏选择运行器并完成初始化。"
            )
            !isPackageName(runnerPackage) -> add(
                "RUNNER_INVALID", DiagnosticSeverity.ERROR, "运行器包名无效",
                "提供的包名不符合应用包名格式。", "请选择已登记的运行器。"
            )
            else -> {
                try {
                    @Suppress("DEPRECATION")
                    val info = appContext.packageManager.getPackageInfo(runnerPackage, 0)
                    if (info.applicationInfo?.enabled == false) add(
                        "RUNNER_DISABLED", DiagnosticSeverity.WARNING, "运行器已停用",
                        "Android 报告此运行器应用已被停用。", "请在 Android 应用设置中启用运行器。"
                    ) else add(
                        "RUNNER_VISIBLE", DiagnosticSeverity.INFO, "可以查询运行器信息",
                        "Android 已返回应用包信息；尚未测试启动或指定游戏的接入。",
                        "请完成运行器设置，并通过经过验证的适配器交接游戏。"
                    )
                } catch (_: PackageManager.NameNotFoundException) {
                    add("RUNNER_UNAVAILABLE", DiagnosticSeverity.WARNING, "未找到运行器或运行器不可见",
                        "Android 未向 Hub 提供此应用包的信息；这不能证明运行器尚未安装。",
                        "请检查安装状态，以及 Hub 的 Android 应用包可见性声明。")
                } catch (_: SecurityException) {
                    add("RUNNER_ACCESS", DiagnosticSeverity.WARNING, "运行器检查被阻止",
                        "Android 拒绝了此次应用包信息查询。", "请检查安装状态与应用包可见性。")
                }
            }
        }

        var redactedLog: String? = null
        var truncated = false
        if (importedLogUri == null) {
            add("NO_LOG", DiagnosticSeverity.INFO, "尚未导入用户日志",
                "目前只能检查本地来源与运行器信息。",
                "可选择运行器导出的文本日志，进行本地规则检查。")
        } else {
            try {
                val uri = validatedUri(importedLogUri.toString())
                val bounded = (appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Log stream unavailable.")).use { it.readBounded(DataLimits.MAX_LOG_BYTES) }
                truncated = bounded.truncated
                val text = bounded.bytes.toString(Charsets.UTF_8)
                redactedLog = redact(text)
                if (truncated) add("LOG_TRUNCATED", DiagnosticSeverity.WARNING, "日志已截断",
                    "仅检查了所选日志的前 256 KiB。",
                    "如相关错误出现在后面，请导出仅包含本次失败过程的较小日志。")
                val matched = inspectLog(text, findings)
                if (!matched) add("LOG_NO_MATCH", DiagnosticSeverity.INFO, "未匹配到诊断规则",
                    "在已导入的日志片段中未发现已配置的错误特征。",
                    "这既不能证明运行成功，也不能确定原因。请结合完整日志和运行器文档判断。")
                add("REDACTION_REVIEW", DiagnosticSeverity.INFO, "分享前请检查脱敏内容",
                    "已在本地遮盖常见路径、URI、邮箱、网络地址及凭据字段。",
                    "自动脱敏可能遗漏信息。分享前请检查姓名、游戏标题及其他敏感内容。")
            } catch (_: SecurityException) {
                add("LOG_PERMISSION", DiagnosticSeverity.WARNING, "日志访问被拒绝",
                    "Android 拒绝了对所选日志的访问。", "请通过系统选择器重新选择日志。")
            } catch (_: IOException) {
                add("LOG_IO", DiagnosticSeverity.WARNING, "无法读取日志",
                    "文件提供方无法读取所选日志。", "请将日志导出为本地纯文本文件后重试。")
            } catch (_: IllegalArgumentException) {
                add("LOG_INVALID", DiagnosticSeverity.WARNING, "日志来源无效",
                    "所选日志不是受支持的本地 URI。", "请通过系统选择器选择纯文本日志。")
            }
        }
        add("PLAYABILITY_UNVERIFIED", DiagnosticSeverity.INFO, "可玩性仍未验证",
            "文件访问、应用包信息和日志特征不能代替实际交互测试。",
            "请根据用户对当前游戏与运行器组合的实际观察记录结果。")
        DiagnosticReport(findings = findings, redactedLog = redactedLog, logTruncated = truncated)
    }

    private data class LogRule(
        val code: String, val title: String, val needles: List<String>, val suggestion: String
    )

    private suspend fun inspectLog(text: String, findings: MutableList<DiagnosticFinding>): Boolean {
        val matched = hashSetOf<String>()
        text.lineSequence().forEachIndexed { index, line ->
            currentCoroutineContext().ensureActive()
            for (rule in rules) {
                if (rule.code !in matched && rule.needles.any { line.contains(it, ignoreCase = true) }) {
                    matched.add(rule.code)
                    // Redact the complete line before taking a prefix, so a cut cannot expose half a secret.
                    val excerpt = redact(line).take(240)
                    findings.add(DiagnosticFinding(
                        rule.code, DiagnosticSeverity.WARNING, rule.title,
                        "导入日志第 ${index + 1} 行：$excerpt",
                        rule.suggestion + " 仅凭此特征无法确定根本原因。"
                    ))
                }
            }
        }
        return matched.isNotEmpty()
    }

    companion object {
        private val rules = listOf(
            LogRule("LOG_PERMISSION", "日志提到访问权限错误",
                listOf("permission denied", "securityexception", "eacces"),
                "请检查运行器自身的文件授权及所选项目目录。"),
            LogRule("LOG_MISSING_FILE", "日志提到文件缺失",
                listOf("no such file", "filenotfoundexception", "file not found"),
                "请检查项目内容是否完整，以及运行器内配置的路径是否正确。"),
            LogRule("LOG_MEMORY", "日志提到内存分配失败",
                listOf("outofmemoryerror", "out of memory", "vk_error_out_of_device_memory"),
                "请检查运行器的内存要求；可尝试关闭其他应用或降低图形设置。"),
            LogRule("LOG_GRAPHICS", "日志提到图形初始化错误",
                listOf("vk_error_incompatible_driver", "failed to create vulkan", "egl_bad_match"),
                "请对照运行器文档，检查当前设备及所选图形后端是否符合要求。"),
            LogRule("LOG_LIBRARY", "日志提到运行库缺失",
                listOf("dll not found", "could not load library", "unsatisfiedlinkerror"),
                "请查阅运行器文档，确认运行环境设置及项目依赖。"),
            LogRule("LOG_FORMAT", "日志提到格式错误",
                listOf("unsupported format", "invalid magic", "corrupt archive"),
                "请确认所选格式，并通过可信来源核对原始文件。"),
            LogRule("LOG_CRASH_SIGNATURE", "日志包含崩溃特征",
                listOf("sigsegv", "fatal signal", "fatal exception"),
                "请先核对时间与进程是否属于本次游戏会话，再记录崩溃结果。")
        )

        /** Public for previewing local diagnostic text before a user chooses to share it. */
        fun redact(text: String): String {
            var result = text.map { if (it.isISOControl() && it != '\n' && it != '\t') ' ' else it }.joinToString("")
            // Mask credential values first, including quoted values containing whitespace.
            result = result.replace(Regex(
                "(?i)\\b(?:authorization|password|passwd|token|api[_-]?key|secret|cookie|access[_-]?key)\\b[\\\"']?\\s*[:=]\\s*(?:\\\"[^\\\"\\r\\n]*\\\"|'[^'\\r\\n]*'|[^\\r\\n,;]+)"
            ), "[REDACTED_CREDENTIAL]")
            result = result.replace(Regex("(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9+/_.=-]+"), "[REDACTED_CREDENTIAL]")
            result = result.replace(Regex("(?i)\\b(?:content|file|https?|ftp)://[^\\s\\\"'<>]+"), "[REDACTED_URI]")
            result = result.replace(Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"), "[REDACTED_EMAIL]")
            result = result.replace(Regex("(?i)(?:[A-Z]:[\\\\/]|\\\\\\\\)[^\\r\\n\\\"<>|]*"), "[REDACTED_PATH]")
            result = result.replace(Regex("(?<![A-Za-z0-9])/(?:[^\\s\\\"'<>]+)"), "[REDACTED_PATH]")
            result = result.replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "[REDACTED_IP]")
            result = result.replace(Regex("(?i)\\b(?:[0-9a-f]{1,4}:){2,}[0-9a-f:]{0,39}(?:%[A-Za-z0-9]+)?"), "[REDACTED_IP]")
            return result
        }
    }
}
