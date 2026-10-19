package dev.gestrin.hub.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job

internal object DataLimits {
    const val MAX_FILES = 5_000
    const val MAX_DEPTH = 6
    const val MAX_ENTRIES = 10_000
    const val MAX_LIBRARY_BYTES = 8 * 1024 * 1024
    const val MAX_LOG_BYTES = 256 * 1024
    const val MAX_TITLE = 512
    const val MAX_URI = 8192
    const val MAX_NOTE = 4096
}

internal fun validatedUri(value: String): Uri {
    require(value.length in 1..DataLimits.MAX_URI && value.none { it.isISOControl() }) {
        "URI is empty, too long, or contains control characters."
    }
    val uri = Uri.parse(value).normalizeScheme()
    require(
        (uri.scheme == "content" && !uri.authority.isNullOrBlank()) ||
            (uri.scheme == "file" && uri.path?.startsWith('/') == true &&
                uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null)
    ) { "Only local content:// and absolute file:// URIs are supported."
    }
    return uri
}

/** Different tree/document spellings of the same SAF document have one identity. */
internal fun sourceIdentity(value: String): String {
    val uri = validatedUri(value)
    if (uri.scheme == "content") {
        val segments = uri.pathSegments
        val documentIndex = segments.indexOf("document")
        val treeIndex = segments.indexOf("tree")
        val id = when {
            documentIndex >= 0 && documentIndex + 1 < segments.size -> segments[documentIndex + 1]
            treeIndex >= 0 && treeIndex + 1 < segments.size -> segments[treeIndex + 1]
            else -> null
        }
        if (id != null) return DocumentsContract.buildDocumentUri(uri.authority!!, id).toString()
    }
    return uri.toString()
}

internal fun validateGame(game: Game): Game {
    require(game.id.length == 36 && UUID.fromString(game.id).toString().equals(game.id, true)) {
        "Game id must be a canonical UUID."
    }
    require(game.title.isNotBlank() && game.title.length <= DataLimits.MAX_TITLE &&
        game.title.none { it.isISOControl() }) { "Invalid game title."
    }
    require(Platform.entries.any { it.name == game.platform }) { "Unknown platform."
    }
    require(game.lastOpened >= 0) { "Invalid lastOpened."
    }
    val uri = validatedUri(game.uri).toString()
    val cover = game.coverUri?.let { validatedUri(it).toString() }
    return game.copy(id = game.id.lowercase(Locale.ROOT), uri = uri, coverUri = cover)
}

internal fun safeTitle(name: String): String = name.map {
    if (it.isISOControl()) ' ' else it
}.joinToString("").trim().take(DataLimits.MAX_TITLE).ifBlank { "未命名游戏" }

internal interface SourceDocument {
    val name: String?
    val isDirectory: Boolean
    fun canRead(): Boolean
    fun exists(): Boolean
}

private class DocumentSource(private val document: DocumentFile) : SourceDocument {
    override val name: String? get() = document.name
    override val isDirectory: Boolean get() = document.isDirectory
    override fun canRead(): Boolean = document.canRead()
    override fun exists(): Boolean = document.exists()
}

internal fun sourceDocument(context: Context, uri: Uri): SourceDocument? = when {
    uri.scheme == "file" -> DocumentSource(DocumentFile.fromFile(java.io.File(uri.path!!)))
    DocumentsContract.isTreeUri(uri) && "document" !in uri.pathSegments ->
        DocumentFile.fromTreeUri(context, uri)?.let(::DocumentSource)
    DocumentsContract.isDocumentUri(context, uri) -> DocumentFile.fromSingleUri(context, uri)?.let(::DocumentSource)
    else -> ReadOnlyContentFile(context, uri)
}

/** FileProvider and other openable URIs do not expose DocumentsContract document_id columns. */
private class ReadOnlyContentFile(private val context: Context, private val source: Uri) : SourceDocument {
    private fun column(name: String): String? = try {
        context.contentResolver.query(source, arrayOf(name), null, null, null)?.use {
            if (it.moveToFirst() && it.getColumnIndex(name) >= 0) it.getString(it.getColumnIndexOrThrow(name)) else null
        }
    } catch (_: Exception) { null }
    override val name: String? get() = column(OpenableColumns.DISPLAY_NAME)
    override val isDirectory: Boolean get() = false
    override fun canRead(): Boolean = try { context.contentResolver.openInputStream(source)?.use { true } ?: false } catch (_: Exception) { false }
    override fun exists(): Boolean = canRead()
}

internal fun persistReadPermission(resolver: ContentResolver, uri: Uri): String? {
    if (uri.scheme != "content") return null
    return try {
        val grantUri = if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.buildTreeDocumentUri(uri.authority!!, DocumentsContract.getTreeDocumentId(uri))
        } else uri
        if (resolver.persistedUriPermissions.any { it.isReadPermission && it.uri == grantUri }) return null
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        null
    } catch (_: SecurityException) {
        "此文件提供方未保存长期读取授权；重启后如无法访问，请重新选择来源。"
    }
}

internal data class BoundedBytes(val bytes: ByteArray, val truncated: Boolean)

/** Reads at most limit + one sentinel byte, checking cancellation between chunks. */
internal suspend fun InputStream.readBounded(limit: Int): BoundedBytes {
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (output.size() <= limit) {
        currentCoroutineContext().ensureActive()
        val remaining = limit + 1 - output.size()
        if (remaining == 0) break
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        if (count == 0) {
            val single = read()
            if (single < 0) break
            output.write(single)
        } else output.write(buffer, 0, count)
    }
    val bytes = output.toByteArray()
    return BoundedBytes(if (bytes.size > limit) bytes.copyOf(limit) else bytes, bytes.size > limit)
}

internal data class ScannedSource(val candidate: ImportCandidate, val platform: Platform?)
internal data class TreeScan(val sources: List<ScannedSource>, val warnings: List<String>)

/** Uses a cursor rather than DocumentFile.listFiles(), which materializes an unbounded directory. */
internal class SafTreeScanner(private val context: Context) {
    private class Directory(val uri: Uri, val name: String, val depth: Int) {
        val directories = mutableListOf<Directory>()
        val files = mutableListOf<Pair<String, Uri>>()
        var hasXp3 = false
        var hasExe = false
        var subtreeXp3 = false
    }

    suspend fun scan(treeUri: Uri, override: Platform?): TreeScan {
        require(treeUri.scheme == "content" && DocumentsContract.isTreeUri(treeUri)) {
            "Choose a directory with ACTION_OPEN_DOCUMENT_TREE."
        }
        val warnings = linkedSetOf<String>()
        persistReadPermission(context.contentResolver, treeUri)?.let(warnings::add)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val rootDoc = sourceDocument(context, rootUri)
        require(rootDoc != null && rootDoc.isDirectory && rootDoc.canRead()) {
            "The selected directory is unavailable or permission was revoked."
        }
        val root = Directory(rootUri, safeTitle(rootDoc.name ?: "导入的目录"), 0)
        val visited = hashSetOf(sourceIdentity(rootUri.toString()))
        var files = 0
        var entries = 0
        var stopped = false

        suspend fun visit(dir: Directory) {
            currentCoroutineContext().ensureActive()
            if (stopped) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getDocumentId(dir.uri)
            )
            val signal = CancellationSignal()
            // This child has no executing body, so parent cancellation completes it immediately
            // and interrupts an in-flight provider query without waiting for this IO block to finish.
            val cancellationWatch = Job(currentCoroutineContext()[Job])
            val cancellationHandle = cancellationWatch.invokeOnCompletion { cause ->
                if (cause != null) signal.cancel()
            }
            try {
                val cursor = context.contentResolver.query(childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null, signal) ?: throw IOException("Provider returned no directory cursor.")
                cursor.use {
                    while (it.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        if (entries >= DataLimits.MAX_ENTRIES || files >= DataLimits.MAX_FILES) {
                            warnings.add("扫描已达到上限：最多 5,000 个文件或 10,000 个目录项；结果可能不完整。")
                            stopped = true
                            break
                        }
                        entries++
                        val id = it.getString(0) ?: continue
                        val name = it.getString(1) ?: "未命名"
                        val mime = it.getString(2)
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        if (childUri.toString().length > DataLimits.MAX_URI) {
                            warnings.add("已跳过一个 URI 长度超出上限的来源。")
                            continue
                        }
                        if (!visited.add(sourceIdentity(childUri.toString()))) continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (dir.depth >= DataLimits.MAX_DEPTH) {
                                warnings.add("已跳过深度超过六层的子目录。")
                            } else dir.directories.add(Directory(childUri, safeTitle(name), dir.depth + 1))
                        } else {
                            files++
                            dir.files.add(name to childUri)
                            when (extension(name)) {
                                "xp3" -> dir.hasXp3 = true
                                "exe" -> dir.hasExe = true
                            }
                        }
                    }
                }
            } catch (_: OperationCanceledException) {
                currentCoroutineContext().ensureActive()
                throw IOException("文件提供方取消了目录查询。")
            } catch (_: SecurityException) {
                currentCoroutineContext().ensureActive()
                if (dir === root) throw SecurityException("Read access to the selected tree was revoked.")
                warnings.add("已跳过一个未获得读取权限的子目录。")
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                if (dir === root) throw IOException("The selected tree could not be enumerated.")
                warnings.add("已跳过一个无法读取的子目录。")
            } finally {
                cancellationHandle.dispose()
                cancellationWatch.complete()
            }
            for (child in dir.directories) {
                currentCoroutineContext().ensureActive()
                if (stopped) break
                visit(child)
            }
            dir.subtreeXp3 = dir.hasXp3 || dir.directories.any { it.subtreeXp3 }
        }
        visit(root)
        val results = mutableListOf<ScannedSource>()
        suspend fun collect(dir: Directory) {
            currentCoroutineContext().ensureActive()
            // A project containing XP3 plus an EXE is treated as a KiriKiri project;
            // a manual platform always wins. Descendant archives/executables stay together.
            if (dir.hasXp3 || dir.hasExe) {
                val detected = if (dir.hasXp3 || dir.subtreeXp3) Platform.XP3 else Platform.WINDOWS
                results.add(ScannedSource(
                    ImportCandidate(dir.name, dir.uri.toString(), true), override ?: detected
                ))
                return
            }
            for ((name, uri) in dir.files) {
                currentCoroutineContext().ensureActive()
                val detected = detectPlatform(name)
                if (detected != null || extension(name) in ambiguousExtensions) {
                    results.add(ScannedSource(
                        ImportCandidate(safeTitle(name.substringBeforeLast('.', name)), uri.toString(), false),
                        override ?: detected
                    ))
                }
            }
            for (child in dir.directories) collect(child)
        }
        collect(root)
        if (results.isEmpty()) {
            results.add(ScannedSource(ImportCandidate(root.name, root.uri.toString(), true), override))
            if (override == null) warnings.add("未找到可自动识别的游戏来源，请为所选目录手动指定平台。")
        }
        return TreeScan(results, warnings.toList())
    }

    companion object {
        private val ambiguousExtensions = setOf("iso", "zip", "7z", "bin", "elf", "pkg")
        private fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

        /** Extension heuristics select a library category; they do not verify format or playability. */
        fun detectPlatform(name: String): Platform? = when (extension(name)) {
            "exe" -> Platform.WINDOWS
            "xp3" -> Platform.XP3
            "cso", "pbp" -> Platform.PSP
            "vpk" -> Platform.VITA
            "nsp", "xci", "nsz", "xcz" -> Platform.SWITCH
            else -> null
        }
    }
}
