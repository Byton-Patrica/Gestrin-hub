package dev.gestrin.hub.runner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LocalRunner(val packageName: String, val label: String, val version: String)

/** Installed apps are user-selected; a binding never implies support for a game's format. */
class LocalRunners(private val context: Context) {
    private val prefs = context.getSharedPreferences("local_runners", Context.MODE_PRIVATE)
    fun list(): List<LocalRunner> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).mapNotNull { info ->
        if (!info.activityInfo.exported || !info.activityInfo.enabled || info.activityInfo.packageName == context.packageName) null
        else get(info.activityInfo.packageName)
    }.distinctBy { it.packageName }.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))

    fun get(pkg: String): LocalRunner? = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(pkg, 0)
        if (pm.getLaunchIntentForPackage(pkg) == null) null
        else LocalRunner(pkg, info.applicationInfo?.loadLabel(pm)?.toString() ?: pkg, info.versionName ?: "?")
    } catch (_: PackageManager.NameNotFoundException) { null }

    fun selected(platform: String): LocalRunner? {
        val saved = prefs.getString(platform, null)
        // A missing explicit binding must not silently select a different app.
        if (saved != null) return get(saved)
        val spec = Catalog.runners.firstOrNull { platformFor(it.id) == platform } ?: return null
        return spec.expectedPackages.firstNotNullOfOrNull(::get)
    }

    fun bind(platform: String, pkg: String?) {
        require(platform in RunnerId.entries.map(::platformFor))
        require(pkg == null || get(pkg) != null) { "Selected application is no longer installed" }
        prefs.edit().apply { if (pkg == null) remove(platform) else putString(platform, pkg) }.apply()
    }

    companion object {
        fun platformFor(id: RunnerId) = when (id) {
            RunnerId.WINLATOR -> "WINDOWS"; RunnerId.KIRIKIROID2 -> "XP3"; RunnerId.PPSSPP -> "PSP"
            RunnerId.VITA3K -> "VITA"; RunnerId.EDEN -> "SWITCH"
        }
    }
}

internal object LaunchPolicy {
    fun hasVerifiedContract(platform: String, directory: Boolean, titleId: String): Boolean =
        (!directory && platform in setOf("PSP", "SWITCH")) ||
            (platform == "VITA" && Regex("[A-Z]{4}[0-9]{5}").matches(titleId))
}
