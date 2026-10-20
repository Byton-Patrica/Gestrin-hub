package dev.gestrin.hub.ui

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/** App-owned copy only. User titles, log excerpts, paths and upstream errors remain verbatim. */
object AppText {
    fun language(context: Context): String = context.getSharedPreferences("hub", 0).getString("language", "zh") ?: "zh"
    val english = mapOf(
        "无法读取本机应用，请稍后重试。" to "Cannot read installed apps. Try again later.",
        "官方 Kirikiroid2 1.3.9 面向 API 11，Android 14 及以上无法新安装。请绑定兼容的本机应用，或导入已适配系统的 APK。" to "Official Kirikiroid2 1.3.9 targets API 11; Android 14+ blocks new installation. Bind a compatible installed app or import an updated APK.",
        "官方 1.3.9 面向 API 11，Android 14 及以上不能新安装。请绑定兼容的本机应用，或选择已适配系统的本地 APK。" to "Official 1.3.9 targets API 11 and cannot be newly installed on Android 14+. Bind a compatible installed app or select an updated local APK.",
        "本地 APK 已检查身份与设备要求；发布者哈希未验证" to "Local APK identity and device requirements checked; publisher hash unverified",
        "启动方式" to "Launch options", "启动结果" to "Launch result", "运行器入口" to "Runner entry",
        "本机应用" to "Local app", "下载安装" to "Download and install", "本地 APK" to "Local APK",
        "选择本机应用" to "Choose installed app", "搜索应用" to "Search apps", "清除绑定" to "Clear binding",
        "尚未选择本机运行器" to "No local runner selected",
        "交接只发送启动请求，不代表游戏运行成功。" to "A handoff sends a request; it does not confirm that the game runs.",
        "交给已选运行器" to "Send to selected runner", "选择接收此文件的应用" to "Choose app for this file",
        "仅打开本地运行器" to "Open local runner only",
        "此项目需在运行器内选择游戏；不会自动跳转。" to "Select this game inside the runner. No automatic redirect.",
        "仅建立平台关联，不会立即启动；应用需自行支持游戏格式。" to "This binds the platform without launching. The app must support the game format.",
        "未找到可启动的本机应用" to "No launchable installed apps found",
        "所选应用已不可用，请重新选择。" to "The app is no longer available. Select it again.",
        "该应用没有已验证的 Vita 标题启动接口，请在运行器内选择游戏。" to "This app has no verified Vita title API. Select the game inside it.",
        "该项目没有已验证的直接启动接口，请明确选择打开本地运行器，再在其中选择游戏。" to "No verified direct launch API. Choose to open the local runner and select the game there.",
        "已请求交接文件；若返回主页或无响应，请使用本地运行器手动选择游戏。" to "File handoff requested. If it returns home or does nothing, select the game inside the local runner.",
        "没有应用声明接收此文件。可绑定本机运行器，在其中选择游戏。" to "No app declares support for this file. Bind a local runner and select the game inside it.",
        "已打开系统应用选择器；选择或取消不代表游戏启动成功。" to "System app chooser opened. Selecting or cancelling does not confirm game launch.",
        "仅打开运行器，请在其中选择游戏。" to "Runner opened only. Select the game inside it.",
        "游戏库" to "Library", "运行器" to "Runners", "设置" to "Settings",
        "添加游戏" to "Add game", "暂无游戏" to "No games", "没有匹配的游戏" to "No matches",
        "搜索游戏" to "Search games", "全部" to "All", "我的收藏" to "Favorites", "游戏仓库" to "Games",
        "只看收藏" to "Favorites only", "最近" to "Recent", "名称" to "Name",
        "添加来源" to "Add source", "选择目录" to "Choose folder", "选择文件" to "Choose file",
        "目录需包含完整游戏资源。" to "Keep the complete game resources in the folder.",
        "正在导入…" to "Importing…", "取消" to "Cancel", "游戏详情" to "Game details", "关闭详情" to "Close details",
        "未验证兼容性" to "Compatibility unverified", "保存修改" to "Save", "更换封面" to "Change cover",
        "项目目录" to "Project folder", "游戏文件" to "Game file", "Vita 已安装标题 ID（可选）" to "Installed Vita title ID (optional)",
        "例如 PCSG00000" to "e.g. PCSG00000", "保存标题关联" to "Save title ID", "尝试启动" to "Try launch", "打开运行器" to "Open runner",
        "在运行器内选择游戏。" to "Select the game in the runner.", "请先在 Vita3K 安装该标题。" to "Install this title in Vita3K first.",
        "诊断" to "Diagnostics", "检查启动条件" to "Check launch setup", "导入日志" to "Import log", "测试记录" to "Test results",
        "无法启动" to "Failed to start", "能启动" to "Started", "可交互" to "Interactive", "从仓库移除" to "Remove from library",
        "移除这款游戏？" to "Remove this game?", "只移除仓库记录，保留原始游戏文件。" to "Remove the library record only. Keep the original files.",
        "移除" to "Remove", "打开" to "Open", "一键安装" to "Install", "继续安装" to "Continue install", "检查更新" to "Check updates",
        "取消下载" to "Cancel download", "上游" to "Source", "详情" to "Details", "收起" to "Hide details", "清理缓存" to "Clear cache",
        "校验信息" to "Verification", "关闭" to "Close", "未取得" to "Unavailable", "语言" to "Language",
        "安装包已就绪" to "Package ready", "下载失败，点击详情查看。" to "Download failed. See details.",
        "外观" to "Appearance", "深色主题" to "Dark theme", "减少动画" to "Reduce motion", "备份" to "Backup",
        "导出备份" to "Export backup", "恢复备份" to "Restore backup", "本地诊断" to "Local diagnostics",
        "规则诊断；模型未启用。" to "Rule-based diagnostics. Model disabled.", "关于" to "About",
        "备份仅含游戏库，不含文件、授权、测试记录或标题关联。" to "Backups contain the library only, excluding files, grants, test results and title bindings.",
        "运行器和依赖按需下载。" to "Runners and dependencies download on demand.",
        "安装需由 Android 系统确认。" to "Confirm installation in Android's system installer.",
        "知道了" to "OK", "运行器安装" to "Runner installation", "选择平台" to "Choose platform",
        "无法仅凭文件名确定平台，请手动选择。" to "Choose a platform for this source.", "加入仓库" to "Add to library", "跳过" to "Skip",
        "先到运行器中心安装对应应用" to "Install the corresponding runner first",
        "操作未完成" to "Operation incomplete", "已有导入任务正在进行" to "An import is already running", "导入已取消" to "Import cancelled",
        "导入未完成" to "Import incomplete", "请检查目录授权。" to "Check folder access.",
        "；部分来源需重新授权或手动检查" to "; some sources need renewed access or review",
        "已保存" to "Saved", "已移除库记录，原文件保留" to "Record removed. Original files kept.",
        "标题 ID 应为四个大写字母和五位数字" to "Use four uppercase letters followed by five digits",
        "已保存标题关联" to "Title ID saved", "未知版本" to "Unknown version", "已记录本次观察结果" to "Test result recorded",
        "本地规则诊断 · 未使用 AI 模型" to "Local rule diagnostics · No AI model",
        "备份已合并；来源目录可能需要重新授权" to "Backup merged. Source access may need to be granted again.",
        "仅记录交接，结果未验证" to "Handoff only; outcome unverified",
        "按需下载 · 未内置运行器" to "Download on demand", "正在查询上游发行版…" to "Checking upstream releases…",
        "下载已暂停，等待网络或系统资源" to "Download paused; waiting for network or system resources",
        "正在检查安装包身份与完整性…" to "Verifying package…", "SHA-256 与上游一致" to "SHA-256 matches upstream",
        "上游未提供可核验哈希；已检查包名，更新时核对已安装签名" to "No publisher checksum. Package ID checked; updates require matching installed signatures.",
        "已取消，可重新下载" to "Cancelled. Ready to retry.", "请重试或查看上游" to "Retry or check the upstream source",
        "尚未允许安装应用。安装包已保留，需要时可再次点击继续安装。" to "Installation permission was not granted. The downloaded package is kept; choose Continue install to retry.",
        "下载安装未完成，请重试。" to "Download or installation incomplete. Please retry.", "系统安装器暂不可用。" to "The system installer is unavailable.",
        "运行器未安装或没有可用入口" to "Runner not installed or no launch entry available",
        "运行器未能打开，请检查系统状态" to "Could not open the runner. Check system status.",
        "Wine 与 Box 兼容环境。容器和游戏快捷方式在 Winlator 内配置；当前提供运行器入口。" to "Wine and Box environment. Configure containers and game shortcuts in Winlator. Hub currently opens the runner.",
        "KiriKiri 项目运行器。请保留完整项目；原版较旧，现代 Android 与 ABI 兼容性待验证。" to "KiriKiri runner. Keep the complete project. The original release is old; modern Android and ABI compatibility remain unverified.",
        "PSP 运行器。支持尝试交接所选游戏文件，实际表现按设备和游戏验证。" to "PSP runner. File handoff is available to try; verify each device and game.",
        "PS Vita 实验性运行器。先在其中安装游戏，再关联标题 ID。" to "Experimental PS Vita runner. Install games in Vita3K, then bind their title IDs.",
        "Switch 运行器。使用上游标准 Android 构建；初始化与用户已有文件在 Eden 内配置。" to "Switch runner using the upstream standard Android build. Configure initial setup and your files in Eden.",
        "首次安装的发布者证书尚未独立锁定；系统安装器会校验 APK 签名。" to "The first-install publisher certificate is not independently pinned. Android verifies the APK signature during installation.",
        "已下载文件的 SHA-256：" to "Downloaded file SHA-256:",
        "标题 ID 格式不正确。" to "Invalid title ID format.", "该版本未开放标题启动入口。" to "This version does not expose a title launch entry.",
        "Vita3K 标题入口不可用，请核对安装版本及标题 ID。" to "Vita3K title entry unavailable. Check the installed version and title ID.",
        "运行器未安装，或该版本没有可用启动入口。请到运行器中心安装。" to "Runner not installed or no launch entry available. Install it in Runners.",
        "项目目录无法读取，请重新选择并授权。" to "Cannot read the project folder. Select it again and grant access.",
        "文件无法读取，请检查来源和权限。" to "Cannot read the file. Check its source and access permissions.",
        "来源不可读：文件可能已移动，或授权已失效。请重新导入。" to "Source unreadable. It may have moved or access may have expired. Import it again.",
        "请使用系统文件选择器重新导入，以便向运行器授予读取权限。" to "Import through the system file picker to grant the runner read access.",
        "此运行器版本未声明接收该游戏文件。请打开运行器并在其内部选择游戏。" to "This runner version does not accept this file. Open the runner and select the game there.",
        "已将文件交给运行器；游戏是否可运行仍需实测。" to "File handed to the runner. Playability remains unverified.",
        "已打开运行器，请在其中选择或初始化游戏。" to "Runner opened. Select or initialize the game there.",
        "来源不可用" to "Source unavailable", "文件提供方无法确认所选来源仍然存在。" to "The provider could not confirm that the source still exists.",
        "请使用系统选择器重新选择来源，并确认文件或目录未被移动。" to "Select the source again and check that it has not moved.",
        "无法读取来源" to "Source unreadable", "所选文档未向 Hub 提供读取权限。" to "The document does not grant Hub read access.",
        "请通过系统选择器重新授予读取权限。" to "Grant read access again through the system picker.",
        "来源类型已变化" to "Source type changed", "文件提供方报告的文件或目录类型与游戏库记录不一致。" to "The provider's file type differs from the library record.",
        "请重新选择正确的文件或完整项目目录。" to "Select the correct file or complete project folder.",
        "可以访问项目目录" to "Project folder accessible", "Hub 可以读取目录信息；尚未验证目录内容是否完整。" to "Hub can read folder metadata. Contents have not been verified.",
        "请保留脚本、归档及相邻资源，并在运行器中单独授予访问权限。" to "Keep scripts, archives and adjacent resources together. Grant the runner its own access.",
        "来源文件为空" to "Source file empty", "读取来源时未获得任何字节，文件流已结束。" to "The source stream ended without returning any bytes.",
        "请检查所选文件，或等待下载完成。" to "Check the selected file or wait for its download to finish.",
        "Hub 可以读取来源" to "Source readable by Hub", "已成功打开来源并读取一个字节；格式与兼容性仍未验证。" to "The source opened and one byte was read. Format and compatibility remain unverified.",
        "外部运行器可能仍需单独选择授权或导入此来源。" to "The runner may still need its own picker grant or import.",
        "请核对所选平台" to "Check selected platform", "请确认平台分类。扩展名仅供参考，不能证明真实文件格式。" to "Confirm the category. An extension is only a hint, not format verification.",
        "来源访问被拒绝" to "Source access denied", "Android 拒绝了 Hub 对来源的访问。" to "Android denied Hub access to the source.",
        "请使用系统选择器重新选择来源。" to "Select the source again through the system picker.",
        "无法打开来源" to "Cannot open source", "文件提供方无法打开来源文件流。" to "The provider could not open the source stream.",
        "请确认文档已下载到本地、仍然存在且可以读取。" to "Check that the document is local, present and readable.",
        "来源 URI 无效" to "Invalid source URI", "已保存的来源不是受支持的本地文档 URI。" to "The saved source is not a supported local document URI.",
        "尚未选择运行器" to "No runner selected", "本次检查未提供运行器包名。" to "No runner package was provided for this check.",
        "请为此游戏选择运行器并完成初始化。" to "Choose a runner and complete its setup.",
        "运行器包名无效" to "Invalid runner package", "提供的包名不符合应用包名格式。" to "The package name has an invalid format.",
        "请选择已登记的运行器。" to "Choose a registered runner.", "运行器已停用" to "Runner disabled",
        "Android 报告此运行器应用已被停用。" to "Android reports that the runner is disabled.", "请在 Android 应用设置中启用运行器。" to "Enable it in Android app settings.",
        "可以查询运行器信息" to "Runner information available", "Android 已返回应用包信息；尚未测试启动或指定游戏的接入。" to "Android returned package metadata. Launch and game handoff have not been tested.",
        "请完成运行器设置，并通过经过验证的适配器交接游戏。" to "Complete runner setup and use a verified adapter for game handoff.",
        "未找到运行器或运行器不可见" to "Runner missing or not visible", "Android 未向 Hub 提供此应用包的信息；这不能证明运行器尚未安装。" to "Android did not expose this package. This does not prove that it is not installed.",
        "请检查安装状态，以及 Hub 的 Android 应用包可见性声明。" to "Check installation and Hub's package visibility declarations.",
        "运行器检查被阻止" to "Runner check blocked", "Android 拒绝了此次应用包信息查询。" to "Android denied the package query.",
        "请检查安装状态与应用包可见性。" to "Check installation and package visibility.",
        "尚未导入用户日志" to "No log imported", "目前只能检查本地来源与运行器信息。" to "Only the source and runner metadata can be checked.",
        "可选择运行器导出的文本日志，进行本地规则检查。" to "Import a text log exported by the runner for local rule analysis.",
        "日志已截断" to "Log truncated", "仅检查了所选日志的前 256 KiB。" to "Only the first 256 KiB of the log was checked.",
        "如相关错误出现在后面，请导出仅包含本次失败过程的较小日志。" to "If the error occurs later, export a smaller log for that attempt.",
        "未匹配到诊断规则" to "No matching rule", "在已导入的日志片段中未发现已配置的错误特征。" to "No configured error signature was found in the imported log.",
        "这既不能证明运行成功，也不能确定原因。请结合完整日志和运行器文档判断。" to "This does not prove success or identify a cause. Review the full log and runner documentation.",
        "分享前请检查脱敏内容" to "Review redaction before sharing", "已在本地遮盖常见路径、URI、邮箱、网络地址及凭据字段。" to "Common paths, URIs, email addresses, network addresses and credentials were masked locally.",
        "自动脱敏可能遗漏信息。分享前请检查姓名、游戏标题及其他敏感内容。" to "Redaction may miss information. Review names, titles and other private details before sharing.",
        "日志访问被拒绝" to "Log access denied", "Android 拒绝了对所选日志的访问。" to "Android denied access to the log.",
        "请通过系统选择器重新选择日志。" to "Select the log again through the system picker.", "无法读取日志" to "Cannot read log",
        "文件提供方无法读取所选日志。" to "The provider could not read the log.", "请将日志导出为本地纯文本文件后重试。" to "Export a local plain-text log and retry.",
        "日志来源无效" to "Invalid log source", "所选日志不是受支持的本地 URI。" to "The log is not a supported local URI.",
        "请通过系统选择器选择纯文本日志。" to "Choose a plain-text log through the system picker.",
        "可玩性仍未验证" to "Playability unverified", "文件访问、应用包信息和日志特征不能代替实际交互测试。" to "File access, package metadata and log signatures do not replace interactive testing.",
        "请根据用户对当前游戏与运行器组合的实际观察记录结果。" to "Record observations for this game and runner combination.",
        "日志提到访问权限错误" to "Log reports an access error", "请检查运行器自身的文件授权及所选项目目录。" to "Check the runner's file permissions and project folder.",
        "日志提到文件缺失" to "Log reports a missing file", "请检查项目内容是否完整，以及运行器内配置的路径是否正确。" to "Check project completeness and the paths configured in the runner.",
        "日志提到内存分配失败" to "Log reports an allocation failure", "请检查运行器的内存要求；可尝试关闭其他应用或降低图形设置。" to "Check memory requirements. Try closing other apps or reducing graphics settings.",
        "日志提到图形初始化错误" to "Log reports a graphics error", "请对照运行器文档，检查当前设备及所选图形后端是否符合要求。" to "Check device and graphics backend requirements in the runner documentation.",
        "日志提到运行库缺失" to "Log reports a missing library", "请查阅运行器文档，确认运行环境设置及项目依赖。" to "Check runtime setup and project dependencies in the runner documentation.",
        "日志提到格式错误" to "Log reports a format error", "请确认所选格式，并通过可信来源核对原始文件。" to "Confirm the format and verify the original file using a trusted source.",
        "日志包含崩溃特征" to "Log contains a crash signature", "请先核对时间与进程是否属于本次游戏会话，再记录崩溃结果。" to "Check the time and process before recording a crash for this session.",
        "仅凭此特征无法确定根本原因。" to "This signature alone does not establish the root cause."
    )

    private val templates = listOf(
        "%s · 已安装 %s" to "%s · Installed %s", "%s · 未安装" to "%s · Not installed",
        "%s · 无法启动" to "%s · Failed to start", "%s · 能启动" to "%s · Started", "%s · 可交互" to "%s · Interactive",
        "%s · 仅记录交接，结果未验证" to "%s · Handoff only; outcome unverified",
        "收藏 %s" to "Favorite %s", "%s 安装包" to "%s package",
        "已加入 %s 款，跳过 %s 个重复来源" to "Added %s games; skipped %s duplicates",
        "已导出 %s 条游戏记录" to "Exported %s game records",
        "安装包已就绪 · %s" to "Ready · %s", "下载或校验失败：%s" to "Download or verification failed: %s",
        "正在下载 %s · %s MB / %s MB" to "Downloading %s · %s MB / %s MB", "正在下载 %s · %s MB" to "Downloading %s · %s MB",
        "依据：%s" to "Evidence: %s", "建议：%s" to "Suggestion: %s",
        "导入日志第 %s 行：%s" to "Imported log line %s: %s",
        "文件扩展名提示可能属于 %s，与游戏库中手动选择的分类不同。" to "The extension suggests %s, which differs from the chosen category.",
        "已请求 Vita3K 启动已安装标题 %s；结果仍需实测。" to "Requested Vita3K title %s. Results remain unverified.",
        "交接失败（%s），请检查运行器和文件授权。" to "Handoff failed (%s). Check the runner and file access.",
        "用户记录 · %s · 运行器 %s · 未进行自动兼容性认证" to "User report · %s · Runner %s · Not automatically certified"
    ).map { (zh, en) -> Triple(zh.split("%s").joinToString("(.+?)") { Regex.escape(it) }.toRegex(), en, zh) }

    fun render(language: String, value: String): String {
        if (language != "en") return value
        return renderEnglish(value)
    }
    private fun renderEnglish(value: String): String {
        english[value]?.let { return it }
        val trimmed = value.trim()
        english[trimmed]?.let { return value.takeWhile(Char::isWhitespace) + it + value.takeLastWhile(Char::isWhitespace) }
        val warningSuffix = "；部分来源需重新授权或手动检查"
        if (value.endsWith(warningSuffix)) return renderEnglish(value.removeSuffix(warningSuffix)) + english.getValue(warningSuffix)
        for ((regex, format, key) in templates) {
            val match = regex.matchEntire(value) ?: continue
            // Raw game titles, paths, IDs and log excerpts are never recursively translated.
            val translateArguments = key in setOf("依据：%s", "建议：%s", "安装包已就绪 · %s", "下载或校验失败：%s")
            val args = match.groupValues.drop(1).map { if (translateArguments) renderEnglish(it) else it }.toTypedArray()
            return String.format(Locale.ENGLISH, format, *args)
        }
        if ('\n' in value) return value.split('\n').joinToString("\n") { renderEnglish(it) }
        val suffix = " 仅凭此特征无法确定根本原因。"
        if (value.endsWith(suffix)) return renderEnglish(value.removeSuffix(suffix)) + " " + english.getValue(suffix.trim())
        return value
    }
}

val LocalLanguage = compositionLocalOf { "zh" }
