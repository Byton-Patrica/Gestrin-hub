package dev.gestrin.hub.runner

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dev.gestrin.hub.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Handoff(val message: String, val packageName: String?, val handedOff: Boolean)

/** Android intents are a handoff only. No inference about guest execution is made here. */
class RunnerLauncher(private val context: Context) {
    // Source: Vita3K b4e35ed3b71b9781add974308d83002a80e9f869 Emulator.java.
    suspend fun launchVita(titleId: String, title: String, packageName: String): Handoff {
        if (!Regex("[A-Z]{4}[0-9]{5}").matches(titleId)) return Handoff("标题 ID 格式不正确。", packageName, false)
        return withContext(Dispatchers.Main) {
            try {
                val component = android.content.ComponentName(packageName, "org.vita3k.emulator.Emulator")
                val info = context.packageManager.getActivityInfo(component, 0)
                if (!info.exported) return@withContext Handoff("该版本未开放标题启动入口。", packageName, false)
                context.startActivity(Intent().setComponent(component).putExtra("title_id", titleId).putExtra("game_title", title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                Handoff("已请求 Vita3K 启动已安装标题 $titleId；结果仍需实测。", packageName, true)
            } catch (_: Exception) { Handoff("Vita3K 标题入口不可用，请核对安装版本及标题 ID。", packageName, false) }
        }
    }
    suspend fun launch(game: Game, packageName: String): Handoff = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val home = pm.getLaunchIntentForPackage(packageName)
            ?: return@withContext Handoff("运行器未安装，或该版本没有可用启动入口。请到运行器中心安装。", null, false)
        val source = Uri.parse(game.uri)
        try {
            if (game.isDirectory) {
                val directory = if ("document" in source.pathSegments) DocumentFile.fromSingleUri(context, source) else DocumentFile.fromTreeUri(context, source)
                if (directory?.isDirectory != true || !directory.canRead())
                    return@withContext Handoff("项目目录无法读取，请重新选择并授权。", packageName, false)
            } else {
                context.contentResolver.openFileDescriptor(source, "r")?.use { }
                    ?: return@withContext Handoff("文件无法读取，请检查来源和权限。", packageName, false)
            }
        } catch (_: Exception) {
            return@withContext Handoff("来源不可读：文件可能已移动，或授权已失效。请重新导入。", packageName, false)
        }
        val canTryFile = !game.isDirectory && game.platform in setOf("PSP", "SWITCH")
        if (canTryFile && source.scheme != "content") return@withContext Handoff("请使用系统文件选择器重新导入，以便向运行器授予读取权限。", packageName, false)
        val intent = if (canTryFile) Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(source, "application/octet-stream")
            setPackage(packageName)
            clipData = ClipData.newRawUri("game", source)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        } else home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null && canTryFile)
            return@withContext Handoff("此运行器版本未声明接收该游戏文件。请打开运行器并在其内部选择游戏。", packageName, false)
        try {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            Handoff(if (canTryFile) "已将文件交给运行器；游戏是否可运行仍需实测。" else "已打开运行器，请在其中选择或初始化游戏。", packageName, true)
        } catch (e: Exception) {
            Handoff("交接失败（${e.javaClass.simpleName}），请检查运行器和文件授权。", packageName, false)
        }
    }
}
