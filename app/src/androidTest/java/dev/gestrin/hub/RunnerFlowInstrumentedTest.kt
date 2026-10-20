package dev.gestrin.hub

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gestrin.hub.data.Game
import dev.gestrin.hub.runner.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RunnerFlowInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun unsupportedProjectsAndMissingSourcesNeverOpenRunnerHome() = runBlocking {
        var dispatched = 0
        val launcher = RunnerLauncher(context) { dispatched++ }
        for (platform in listOf("WINDOWS", "XP3", "VITA")) {
            val result = launcher.launch(Game(title = "fixture", platform = platform, uri = "content://missing/game", isDirectory = true), "com.android.settings")
            assertFalse(result.handedOff)
        }
        val missing = launcher.launch(Game(title = "missing", platform = "PSP", uri = "content://missing/file.iso", isDirectory = false), "com.android.settings")
        assertFalse(missing.handedOff)
        assertFalse(launcher.launchVita("PCSG00000", "fixture", "com.android.settings").handedOff)
        assertEquals(0, dispatched)
    }

    @Test fun installedAppBindingPersistsWithoutStartingOrDownloadingAnything() {
        val local = LocalRunners(context)
        val prefs = context.getSharedPreferences("local_runners", 0)
        val previous = prefs.getString("WINDOWS", null)
        val app = local.list().first()
        try {
            local.bind("WINDOWS", app.packageName)
            assertEquals(app.packageName, LocalRunners(context).selected("WINDOWS")?.packageName)
            local.bind("WINDOWS", null)
            assertFalse(prefs.contains("WINDOWS"))
        } finally { prefs.edit().apply { if (previous == null) remove("WINDOWS") else putString("WINDOWS", previous) }.commit() }
    }

    @Test fun localApkWorksOfflineAndRetainsItsProvenanceAcrossInstallerRecreation() = runBlocking {
        val prefs = context.getSharedPreferences("runner_downloads_v1", 0)
        val previous = prefs.getString("KIRIKIROID2", null)
        val folder = File(context.cacheDir, "packages").apply { mkdirs() }
        val source = File(folder, "local-test-${UUID.randomUUID()}.apk")
        File(context.applicationInfo.sourceDir).copyTo(source)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", source)
        var backend = Installer(context)
        try {
            // The Hub APK is a signed local fixture with a different package from Kirikiroid2.
            // No system installation, network connection or external app launch is performed.
            backend.importApk(RunnerId.KIRIKIROID2, uri)
            assertEquals(RunnerPhase.READY, backend.states.value[RunnerId.KIRIKIROID2]?.phase)
            assertEquals(context.packageName, backend.states.value[RunnerId.KIRIKIROID2]?.packageName)
            assertNull(backend.states.value[RunnerId.KIRIKIROID2]?.downloadId)
            backend.close()
            backend = Installer(context)
            assertFalse(backend.prepareInstall(RunnerId.KIRIKIROID2) is InstallAction.Unavailable)
            assertEquals(RunnerPhase.READY, backend.states.value[RunnerId.KIRIKIROID2]?.phase)
        } finally {
            backend.cancel(RunnerId.KIRIKIROID2); backend.close(); source.delete()
            prefs.edit().apply { if (previous == null) remove("KIRIKIROID2") else putString("KIRIKIROID2", previous) }.commit()
        }
    }
}
