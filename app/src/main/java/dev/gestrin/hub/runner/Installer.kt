package dev.gestrin.hub.runner

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

enum class RunnerPhase { IDLE, RESOLVING, DOWNLOADING, PAUSED, VERIFYING, READY, CANCELLED, FAILED }

data class RunnerState(
    val phase: RunnerPhase = RunnerPhase.IDLE,
    val downloadId: Long? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val releaseName: String? = null,
    val assetName: String? = null,
    val packageName: String? = null,
    val computedSha256: String? = null,
    val hashVerified: Boolean = false,
    val hashStatus: String = "Not checked",
    val error: String? = null,
)

sealed interface InstallAction {
    data class Launch(val intent: Intent) : InstallAction
    data class UnknownSources(val intent: Intent) : InstallAction
    data class Unavailable(val reason: String) : InstallAction
}

/**
 * One application-scoped instance is recommended. All suspend methods are main-safe.
 * download/retry are explicit user actions; construction and refresh never start network work.
 * Parent owns polling (e.g. refresh every second while visible), settings return and intent launch.
 * READY means a validated download, not an installed app. Launch is not proof of installation.
 * close cancels resolution only; DownloadManager survives process death and is recovered by refresh.
 *
 * DownloadManager has no per-request hard byte limit or redirect interception API. We check
 * every preflight redirect and declared size, enqueue the stable publisher URL, stop oversized
 * transfers when polled, and enforce the cap again before copying/using any completed file.
 * The system download service controls subsequent redirects and background transfer behavior.
 */
class Installer(context: Context) {
    private val app = context.applicationContext
    private val manager = requireNotNull(app.getSystemService(DownloadManager::class.java)) { "DownloadManager is unavailable" }
    private val preferences = app.getSharedPreferences("runner_downloads_v1", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val active = mutableMapOf<RunnerId, Deferred<Unit>>()
    private val records = RunnerId.entries.associateWith { restore(it) }.toMutableMap()
    private val mutableStates = MutableStateFlow(records.mapValues { it.value.state })
    val states: StateFlow<Map<RunnerId, RunnerState>> = mutableStates.asStateFlow()
    @Volatile private var closed = false

    suspend fun download(id: RunnerId): Unit = withContext(Dispatchers.IO) {
        val task = mutex.withLock {
            check(!closed) { "Installer is closed" }
            active[id]?.let { return@withLock it }
            if (records.getValue(id).state.phase in setOf(RunnerPhase.DOWNLOADING, RunnerPhase.PAUSED, RunnerPhase.VERIFYING, RunnerPhase.READY)) {
                return@withLock null
            }
            cleanup(id, records.getValue(id))
            val record = Record(UUID.randomUUID().toString(), RunnerState(phase = RunnerPhase.RESOLVING))
            update(id, record)
            scope.async { resolveAndEnqueue(id, record.token) }.also { active[id] = it }
        }
        task?.await()
        Unit
    }

    /** Explicit retry re-resolves upstream, so expired CDN links and changed releases are replaced. */
    suspend fun retry(id: RunnerId) {
        cancel(id)
        download(id)
    }

    /** A user-selected standalone APK follows the same size, ABI, identity and signer checks. */
    suspend fun importApk(id: RunnerId, uri: Uri): Unit = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Select a local APK through the system file picker" }
        cancel(id)
        mutex.withLock {
            val record = Record(UUID.randomUUID().toString(), RunnerState(phase = RunnerPhase.VERIFYING, releaseName = "Local APK"))
            update(id, record)
            val target = packageFile(id, record.token)
            try {
                target.parentFile!!.mkdirs()
                app.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > RunnerSecurity.MAX_APK_BYTES) throw IOException("APK exceeds size limit")
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                } ?: throw IOException("Cannot read selected APK")
                val hash = hashFile(target, target.length())
                val info = validateArchive(id, target, allowLocalPackage = true)
                update(id, record.copy(localSource = true, expectedSize = target.length(), state = record.state.copy(
                    phase = RunnerPhase.READY, downloadedBytes = target.length(), totalBytes = target.length(),
                    packageName = info.packageName, computedSha256 = hash, hashStatus = "Local APK; publisher hash not verified"
                )))
            } catch (e: Exception) {
                target.delete()
                fail(id, e)
                throw e
            }
        }
    }

    suspend fun cancel(id: RunnerId): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            active.remove(id)?.cancel()
            val record = records.getValue(id)
            try {
                cleanup(id, record)
                update(id, Record(record.token, RunnerState(phase = RunnerPhase.CANCELLED)))
            } catch (e: Exception) {
                fail(id, e)
            }
        }
    }

    /** Restores persistent IDs, reports real DownloadManager failures, and validates completions. */
    suspend fun refresh(): Unit = withContext(Dispatchers.IO) {
        for (id in RunnerId.entries) mutex.withLock {
            try {
                refreshLocked(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e)
            }
        }
    }

    /**
     * Revalidates bytes, package and installed signer on EVERY call, including after settings.
     * No activity is launched here. Parent must launch the returned explicit system intent.
     */
    suspend fun prepareInstall(id: RunnerId): InstallAction = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = records.getValue(id)
            if (record.state.phase != RunnerPhase.READY) return@withLock InstallAction.Unavailable("Download is not ready")
            try {
                val file = packageFile(id, record.token)
                val hash = hashFile(file, record.expectedSize)
                if (hash != record.state.computedSha256) throw IOException("APK changed since verification; download it again")
                val info = validateArchive(id, file, allowLocalPackage = record.localSource)
                if (info.packageName != record.state.packageName) throw IOException("APK package changed since verification")
                if (!app.packageManager.canRequestPackageInstalls()) {
                    return@withLock InstallAction.UnknownSources(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")),
                    )
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("Runner APK", uri)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                @Suppress("DEPRECATION")
                val handlers = app.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                val handler = handlers.firstOrNull {
                    (it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0 &&
                        it.activityInfo.exported && it.activityInfo.enabled
                } ?: return@withLock InstallAction.Unavailable("No system package installer is available")
                intent.setClassName(handler.activityInfo.packageName, handler.activityInfo.name)
                InstallAction.Launch(intent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e)
                InstallAction.Unavailable(message(e))
            }
        }
    }

    fun close() {
        closed = true
        scope.cancel()
    }

    private suspend fun resolveAndEnqueue(id: RunnerId, token: String) {
        try {
            val apk = ReleaseResolver.resolve(id, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT)
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                val old = records.getValue(id)
                if (old.token != token || old.state.phase != RunnerPhase.RESOLVING) return@withLock
                // Commit metadata BEFORE enqueue. A crash between enqueue and storing its ID can
                // be recovered by matching the unique destination URI in refreshLocked.
                val ready = old.copy(
                    expectedSize = apk.size, expectedHash = apk.sha256,
                    state = old.state.copy(releaseName = apk.releaseName, assetName = apk.assetName, totalBytes = apk.size),
                )
                update(id, ready)
                val destination = downloadFile(id, token)
                if (!destination.parentFile!!.isDirectory && !destination.parentFile!!.mkdirs()) throw IOException("Cannot create download directory")
                val request = DownloadManager.Request(Uri.parse(apk.url))
                    .addRequestHeader("User-Agent", "GestrinHub/0.1 Android")
                    .addRequestHeader("Accept", "application/octet-stream, */*")
                    .setTitle("${Catalog.get(id).name} ${apk.releaseName}")
                    .setDescription("User-requested runner download from the official project")
                    .setMimeType("application/octet-stream")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, destination.name)
                val downloadId = manager.enqueue(request)
                try {
                    update(id, ready.copy(state = ready.state.copy(phase = RunnerPhase.DOWNLOADING, downloadId = downloadId)))
                } catch (e: Exception) {
                    manager.remove(downloadId)
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutex.withLock {
                if (records.getValue(id).token == token && records.getValue(id).state.phase == RunnerPhase.RESOLVING) fail(id, e)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { if (records.getValue(id).token == token) active.remove(id) }
            }
        }
    }

    private suspend fun refreshLocked(id: RunnerId) {
        var record = records.getValue(id)
        if (record.state.phase == RunnerPhase.READY) {
            if (!packageFile(id, record.token).isFile) throw IOException("Validated APK was removed; retry the download")
            return
        }
        if (record.state.phase !in setOf(RunnerPhase.RESOLVING, RunnerPhase.DOWNLOADING, RunnerPhase.PAUSED, RunnerPhase.VERIFYING)) return
        if (record.state.phase == RunnerPhase.RESOLVING && active[id]?.isActive == true) return
        var downloadId = record.state.downloadId
        if (downloadId == null) {
            downloadId = recoverId(id, record.token)
            if (downloadId == null) throw IOException("Release resolution was interrupted; retry to start a download")
            record = record.copy(state = record.state.copy(downloadId = downloadId, phase = RunnerPhase.DOWNLOADING))
            update(id, record)
        }
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) throw IOException("DownloadManager no longer has download $downloadId; retry")
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (bytes > RunnerSecurity.MAX_APK_BYTES || total > RunnerSecurity.MAX_APK_BYTES ||
                (record.expectedSize > 0 && (bytes > record.expectedSize || (total > 0 && total != record.expectedSize)))) {
                manager.remove(downloadId)
                throw IOException("Download exceeded the approved APK size")
            }
            val state = record.state.copy(downloadedBytes = bytes.coerceAtLeast(0), totalBytes = if (total > 0) total else record.expectedSize)
            when (status) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING ->
                    update(id, record.copy(state = state.copy(phase = RunnerPhase.DOWNLOADING, error = null)))
                DownloadManager.STATUS_PAUSED -> update(id, record.copy(state = state.copy(phase = RunnerPhase.PAUSED, error = pauseReason(reason))))
                DownloadManager.STATUS_FAILED -> throw IOException("DownloadManager failure $reason: ${downloadFailure(reason)}")
                DownloadManager.STATUS_SUCCESSFUL -> {
                    update(id, record.copy(state = state.copy(phase = RunnerPhase.VERIFYING, error = null)))
                    verifyDownload(id, records.getValue(id))
                }
                else -> throw IOException("Unknown DownloadManager status $status (reason $reason)")
            }
        }
    }

    private fun recoverId(id: RunnerId, token: String): Long? {
        val expected = Uri.fromFile(downloadFile(id, token)).toString()
        manager.query(DownloadManager.Query()).use { cursor ->
            if (cursor == null) return null
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) == expected) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                }
            }
        }
        return null
    }

    private suspend fun verifyDownload(id: RunnerId, record: Record) {
        val source = downloadFile(id, record.token)
        val expectedSize = RunnerSecurity.completedSize(record.expectedSize, source.length())
        val target = packageFile(id, record.token)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        if (!target.parentFile!!.isDirectory && !target.parentFile!!.mkdirs()) throw IOException("Cannot create private package directory")
        try {
            RunnerSecurity.checkSize(source.length())
            if (source.length() != expectedSize) throw IOException("Downloaded APK size does not match approved size")
            source.inputStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var countTotal = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        countTotal += count
                        if (countTotal > expectedSize || countTotal > RunnerSecurity.MAX_APK_BYTES) throw IOException("APK grew beyond the approved size")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val hash = hashFile(temporary, expectedSize)
            if (record.expectedHash != null && hash != record.expectedHash) throw IOException("SHA-256 mismatch with the official publisher checksum")
            val archive = validateArchive(id, temporary)
            if (!temporary.renameTo(target)) throw IOException("Cannot finalize validated APK")
            val verified = record.expectedHash != null
            update(id, record.copy(expectedSize = expectedSize, state = record.state.copy(
                phase = RunnerPhase.READY, downloadedBytes = target.length(), totalBytes = target.length(),
                packageName = archive.packageName, computedSha256 = hash, hashVerified = verified,
                hashStatus = if (verified) "SHA-256 verified against the official publisher checksum"
                    else "UNVERIFIED publisher hash: no SHA-256 published; computed checksum only",
                error = null,
            )))
            // The app-private verified copy is now durable. No raw APK remains in Downloads.
            record.state.downloadId?.let { manager.remove(it) }
        } finally {
            temporary.delete()
        }
    }

    private suspend fun hashFile(file: File, expectedSize: Long): String {
        RunnerSecurity.checkSize(file.length())
        if (file.length() != expectedSize) throw IOException("APK size changed or file is incomplete")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > expectedSize || total > RunnerSecurity.MAX_APK_BYTES) throw IOException("APK exceeds size limit")
                digest.update(buffer, 0, count)
            }
        }
        if (total != expectedSize) throw IOException("APK is truncated")
        return digest.digest().toHex()
    }

    @Suppress("DEPRECATION")
    private fun validateArchive(id: RunnerId, file: File, allowLocalPackage: Boolean = false): PackageInfo {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("File is not a readable signed Android APK")
        if (!allowLocalPackage && archive.packageName !in Catalog.get(id).expectedPackages) throw IOException("Unexpected APK package: ${archive.packageName}")
        if (!archive.splitNames.isNullOrEmpty()) throw IOException("Split APKs are not supported")
        val appInfo = archive.applicationInfo ?: throw IOException("APK has no application metadata")
        if (appInfo.minSdkVersion > Build.VERSION.SDK_INT) throw IOException("APK requires Android API ${appInfo.minSdkVersion}; device is API ${Build.VERSION.SDK_INT}")
        val minimumTarget = RunnerSecurity.minimumTarget(Build.VERSION.SDK_INT)
        if (appInfo.targetSdkVersion < minimumTarget) throw IOException("Android blocks this legacy APK (target ${appInfo.targetSdkVersion}, required $minimumTarget). Select a maintained build or an already installed runner.")
        val signer = signers(archive)
        if (signer.isEmpty()) throw IOException("APK has no readable signing certificate")
        ZipFile(file).use { zip ->
            if (zip.getEntry("AndroidManifest.xml") == null) throw IOException("APK has no Android manifest")
            val nativeAbis = zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .map { it.name.split('/').getOrNull(1) }.filterNotNull().toSet()
            if (nativeAbis.isNotEmpty() && nativeAbis.none { it in Build.SUPPORTED_ABIS }) throw IOException("APK does not support this device's CPU")
        }
        val installed = try { pm.getPackageInfo(archive.packageName, flags) } catch (_: PackageManager.NameNotFoundException) { null }
        if (installed != null) {
            // Conservative: rotations and different store signing keys require manual review.
            // Android's installer remains responsible for cryptographic install-time verification.
            if (!RunnerSecurity.matchingSigners(signers(installed), signer)) throw IOException("APK signer differs from the installed app; update blocked")
            val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            val archiveVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
            if (archiveVersion < installedVersion) throw IOException("APK would downgrade the installed app; update blocked")
        }
        return archive
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun downloadFile(id: RunnerId, token: String): File {
        checkToken(token)
        val directory = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IOException("External app download storage is unavailable")
        return File(directory, "runner-${id.name.lowercase()}-$token.download")
    }

    private fun packageFile(id: RunnerId, token: String): File {
        checkToken(token)
        return File(File(app.filesDir, "packages"), "runner-${id.name.lowercase()}-$token.apk")
    }

    private fun checkToken(token: String) {
        if (!token.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) throw IOException("Invalid download record identifier")
    }

    private fun cleanup(id: RunnerId, record: Record) {
        record.state.downloadId?.let { manager.remove(it) }
        if (record.token.isNotEmpty()) {
            // Only generated files for this record, never a user-supplied path or directory.
            val archive = packageFile(id, record.token)
            if (archive.exists() && !archive.delete()) throw IOException("Cannot remove previous runner APK")
            val temporary = File(archive.parentFile, "${archive.name}.tmp")
            if (temporary.exists() && !temporary.delete()) throw IOException("Cannot remove incomplete runner APK")
            val download = downloadFile(id, record.token)
            if (download.exists() && !download.delete()) throw IOException("Cannot remove previous runner download")
        }
    }

    private fun fail(id: RunnerId, e: Exception) {
        val record = records.getValue(id)
        update(id, record.copy(state = record.state.copy(phase = RunnerPhase.FAILED, error = message(e))))
    }

    private fun message(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    private fun update(id: RunnerId, record: Record) {
        val json = JSONObject().apply {
            put("token", record.token); put("expectedSize", record.expectedSize); put("expectedHash", record.expectedHash); put("localSource", record.localSource)
            val s = record.state
            put("phase", s.phase.name); put("downloadId", s.downloadId); put("downloadedBytes", s.downloadedBytes)
            put("totalBytes", s.totalBytes); put("releaseName", s.releaseName); put("assetName", s.assetName)
            put("packageName", s.packageName); put("computedSha256", s.computedSha256)
            put("hashVerified", s.hashVerified); put("hashStatus", s.hashStatus); put("error", s.error)
        }
        if (!preferences.edit().putString(id.name, json.toString()).commit()) throw IOException("Cannot persist runner download state")
        records[id] = record
        mutableStates.value = records.mapValues { it.value.state }
    }

    private fun restore(id: RunnerId): Record {
        val text = preferences.getString(id.name, null) ?: return Record()
        return try {
            val json = JSONObject(text)
            fun optional(name: String): String? = if (json.isNull(name)) null else json.optString(name).takeIf { it.isNotEmpty() }
            val token = json.getString("token")
            if (token.isNotEmpty()) checkToken(token)
            val hash = optional("expectedHash")?.let { RunnerSecurity.sha256(it) }
            Record(token, RunnerState(
                phase = RunnerPhase.valueOf(json.getString("phase")),
                downloadId = if (json.isNull("downloadId")) null else json.getLong("downloadId"),
                downloadedBytes = json.optLong("downloadedBytes", 0), totalBytes = json.optLong("totalBytes", -1),
                releaseName = optional("releaseName"), assetName = optional("assetName"), packageName = optional("packageName"),
                computedSha256 = optional("computedSha256"), hashVerified = json.optBoolean("hashVerified"),
                hashStatus = json.optString("hashStatus", "Not checked"), error = optional("error"),
            ), json.optLong("expectedSize", -1), hash, json.optBoolean("localSource", false))
        } catch (e: Exception) {
            Record(state = RunnerState(phase = RunnerPhase.FAILED, error = "Cannot restore runner download: ${message(e)}"))
        }
    }

    private data class Record(
        val token: String = "",
        val state: RunnerState = RunnerState(),
        val expectedSize: Long = -1,
        val expectedHash: String? = null,
        val localSource: Boolean = false,
    )

    private fun pauseReason(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "DownloadManager is waiting to retry"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        else -> "Download paused (DownloadManager reason $reason)"
    }

    private fun downloadFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume; retry to resolve a fresh official URL"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Destination file already exists"
        DownloadManager.ERROR_FILE_ERROR -> "Download file I/O failed"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data transfer failed"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many HTTP redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response"
        DownloadManager.ERROR_UNKNOWN -> "Unknown system download error"
        in 400..599 -> "HTTP $reason"
        else -> "System download error $reason"
    }
}
