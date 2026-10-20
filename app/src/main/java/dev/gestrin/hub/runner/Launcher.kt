package dev.gestrin.hub.runner

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.gestrin.hub.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class Handoff(val message: String, val packageName: String?, val handedOff: Boolean)

/** A game handoff never falls back to opening an app's home screen. */
class RunnerLauncher(private val context: Context, private val dispatch: (Intent) -> Unit = { context.startActivity(it) }) {
    suspend fun launchVita(titleId: String, title: String, packageName: String): Handoff {
        if (packageName !in Catalog.get(RunnerId.VITA3K).expectedPackages)
            return Handoff("该应用没有已验证的 Vita 标题启动接口，请在运行器内选择游戏。", packageName, false)
        if (!Regex("[A-Z]{4}[0-9]{5}").matches(titleId)) return Handoff("标题 ID 格式不正确。", packageName, false)
        return withContext(Dispatchers.Main) {
            try {
                // Upstream Vita3K Emulator.java receives title_id and game_title.
                val component = ComponentName(packageName, "org.vita3k.emulator.Emulator")
                val info = context.packageManager.getActivityInfo(component, 0)
                if (!info.exported || !info.enabled) return@withContext Handoff("该版本未开放标题启动入口。", packageName, false)
                dispatch(Intent().setComponent(component).putExtra("title_id", titleId).putExtra("game_title", title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Handoff("已请求 Vita3K 启动已安装标题 $titleId；结果仍需实测。", packageName, true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Handoff("Vita3K 标题入口不可用，请核对安装版本及标题 ID。", packageName, false) }
        }
    }

    suspend fun launch(game: Game, packageName: String): Handoff = withContext(Dispatchers.IO) {
        if (!LaunchPolicy.hasVerifiedContract(game.platform, game.isDirectory, ""))
            return@withContext Handoff("该项目没有已验证的直接启动接口，请明确选择打开本地运行器，再在其中选择游戏。", packageName, false)
        try {
            val intent = fileIntent(game).setPackage(packageName)
            val pm = context.packageManager
            // SAF document IDs need not end in .iso; PPSSPP's manifest pathPattern can reject
            // such URIs even though its exported activity receives content URIs correctly.
            if (game.platform == "PSP" && packageName == "org.ppsspp.ppsspp") {
                val component = ComponentName(packageName, "org.ppsspp.ppsspp.PpssppActivity")
                val info = pm.getActivityInfo(component, 0)
                if (!info.exported || !info.enabled) throw IllegalStateException("PPSSPP entry is unavailable")
                intent.component = component
            } else if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                return@withContext Handoff("此运行器版本未声明接收该游戏文件。请打开运行器并在其内部选择游戏。", packageName, false)
            }
            withContext(Dispatchers.Main) { dispatch(intent) }
            Handoff("已请求交接文件；若返回主页或无响应，请使用本地运行器手动选择游戏。", packageName, true)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Handoff("交接失败（${e.javaClass.simpleName}），请检查运行器和文件授权。", packageName, false)
        }
    }

    /** The chooser is explicit and works with installed handlers without any runner download. */
    suspend fun chooseFile(game: Game): Handoff = withContext(Dispatchers.IO) {
        try {
            val intent = fileIntent(game)
            if (context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty())
                return@withContext Handoff("没有应用声明接收此文件。可绑定本机运行器，在其中选择游戏。", null, false)
            val chooser = Intent.createChooser(intent, "Gestrin Hub").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = intent.clipData
            }
            withContext(Dispatchers.Main) { dispatch(chooser) }
            Handoff("已打开系统应用选择器；选择或取消不代表游戏启动成功。", null, false)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Handoff("交接失败（${e.javaClass.simpleName}），请检查运行器和文件授权。", null, false) }
    }

    suspend fun openRunner(packageName: String): Handoff = withContext(Dispatchers.Main) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@withContext Handoff("运行器未安装或没有可用入口", packageName, false)
            dispatch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Handoff("仅打开运行器，请在其中选择游戏。", packageName, false)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Handoff("运行器未能打开，请检查系统状态", packageName, false) }
    }

    private fun fileIntent(game: Game): Intent {
        require(!game.isDirectory) { "A directory cannot be handed off as a single file" }
        val source = Uri.parse(game.uri)
        require(source.scheme == "content") { "Reimport through the system file picker" }
        context.contentResolver.openFileDescriptor(source, "r")?.use { }
            ?: throw IllegalStateException("Game source is no longer readable")
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(source, context.contentResolver.getType(source) ?: "application/octet-stream")
            clipData = ClipData.newRawUri("game", source)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
