package dev.gestrin.hub.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Local library boundary. Construct once with an application Context, then collect the Flows.
 * SAF calls expect picker-granted URIs. No method deletes source files or starts a runner.
 * I/O/security/validation errors propagate to the caller; coroutine cancellation is never swallowed.
 */
class HubRepository(
    context: Context,
    private val database: HubDatabase = HubDatabase.getInstance(context)
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val gameDao = database.gameDao()
    private val sessionDao = database.sessionDao()

    val games: Flow<List<Game>> = gameDao.observeAll()
    val favorites: Flow<List<Game>> = gameDao.observeFavorites()
    val sessions: Flow<List<Session>> = sessionDao.observeAll()

    fun game(id: String): Flow<Game?> = gameDao.observeById(id)
    fun gamesForPlatform(platform: Platform): Flow<List<Game>> = gameDao.observePlatform(platform.name)
    fun sessionsForGame(gameId: String): Flow<List<Session>> = sessionDao.observeForGame(gameId)
    suspend fun getGame(id: String): Game? = gameDao.getById(id)

    /** Unknown extensions return a candidate. Call again with a platform to confirm it. */
    suspend fun importFile(uri: Uri, platform: Platform? = null): ImportResult = withContext(Dispatchers.IO) {
        val validUri = validatedUri(uri.toString())
        val warnings = mutableListOf<String>()
        persistReadPermission(resolver, validUri)?.let(warnings::add)
        val document = sourceDocument(appContext, validUri)
        require(document != null && document.exists() && document.canRead()) {
            "The selected source is unavailable or permission was revoked."
        }
        val isDirectory = document.isDirectory
        val name = document.name ?: "导入的游戏"
        val title = safeTitle(if (isDirectory) name else name.substringBeforeLast('.', name))
        val detected = platform ?: if (isDirectory) null else SafTreeScanner.detectPlatform(name)
        val source = ScannedSource(ImportCandidate(title, validUri.toString(), isDirectory), detected)
        insertSources(listOf(source), warnings)
    }

    /** Root depth is 0; reads files in directories through depth 6, at most 5,000 files. */
    suspend fun scanTree(uri: Uri, platform: Platform? = null): ImportResult = withContext(Dispatchers.IO) {
        val scan = SafTreeScanner(appContext).scan(validatedUri(uri.toString()), platform)
        insertSources(scan.sources, scan.warnings)
    }

    private suspend fun insertSources(sources: List<ScannedSource>, warnings: List<String>): ImportResult {
        val candidates = mutableListOf<ImportCandidate>()
        var added = 0
        var skipped = 0
        database.withTransaction {
            val identities = gameDao.getAll().mapTo(hashSetOf()) { sourceIdentity(it.uri) }
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                if (!identities.add(sourceIdentity(source.candidate.uri))) {
                    skipped++
                } else if (source.platform == null) {
                    candidates.add(source.candidate)
                } else {
                    val game = validateGame(Game(
                        title = source.candidate.title, platform = source.platform.name,
                        uri = source.candidate.uri, isDirectory = source.candidate.isDirectory
                    ))
                    if (gameDao.insertIfAbsent(game) == -1L) skipped++ else added++
                }
            }
        }
        return ImportResult(added, skipped, candidates, warnings)
    }

    /** Explicit editor save; a source already owned by a different game is rejected. */
    suspend fun saveGame(game: Game) = withContext(Dispatchers.IO) {
        val valid = validateGame(game)
        database.withTransaction {
            val identity = sourceIdentity(valid.uri)
            require(gameDao.getAll().none { it.id != valid.id && sourceIdentity(it.uri) == identity }) {
                "This source is already in the library."
            }
            if (gameDao.getById(valid.id) == null) gameDao.insert(valid) else gameDao.update(valid)
        }
        Unit
    }

    /** Removes metadata and its session history only. */
    suspend fun deleteGame(id: String) {
        gameDao.deleteById(id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        require(gameDao.setFavorite(id, favorite) == 1) { "Game no longer exists."
        }
    }

    /** Record a launch request, not a successful launch. Call only when handing off to a runner. */
    suspend fun markOpened(id: String, runnerPackage: String? = null): String {
        require(runnerPackage == null || isPackageName(runnerPackage)) { "Invalid runner package."
        }
        return database.withTransaction {
            val now = System.currentTimeMillis()
            require(gameDao.markOpened(id, now) == 1) { "Game no longer exists."
            }
            val session = Session(gameId = id, startedAt = now, runnerPackage = runnerPackage)
            sessionDao.insert(session)
            session.id
        }
    }

    /** Outcome is a user's observation. Recording it does not automatically close the session. */
    suspend fun recordSessionOutcome(id: String, outcome: SessionOutcome, note: String? = null) {
        validateNote(note)
        database.withTransaction {
            val session = requireNotNull(sessionDao.getById(id)) { "Session no longer exists."
            }
            sessionDao.update(session.copy(outcome = outcome.name, note = note ?: session.note))
        }
    }

    /** Explicit user completion only; the first end time is retained on repeated calls. */
    suspend fun finishSession(id: String, note: String? = null, outcome: SessionOutcome? = null) {
        validateNote(note)
        database.withTransaction {
            val session = requireNotNull(sessionDao.getById(id)) { "Session no longer exists."
            }
            sessionDao.update(session.copy(
                endedAt = session.endedAt ?: maxOf(session.startedAt, System.currentTimeMillis()),
                note = note ?: session.note,
                outcome = outcome?.name ?: session.outcome
            ))
        }
    }

    /**
     * Version-1 UTF-8 metadata only: no source bytes, logs, grants, or session history.
     * UI label: “导出游戏库（不含会话历史）”. This is not a full application backup.
     */
    suspend fun exportLibrary(uri: Uri): Int = withContext(Dispatchers.IO) {
        val destination = validatedUri(uri.toString())
        val snapshot = gameDao.getAll()
        val bytes = LibraryJson.encode(snapshot)
        currentCoroutineContext().ensureActive()
        (resolver.openOutputStream(destination, "wt") ?: throw IOException("Cannot open export destination.")).use {
            var offset = 0
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(8192, bytes.size - offset)
                it.write(bytes, offset, count)
                offset += count
            }
            it.flush()
        }
        snapshot.size
    }

    /** Validates the whole bounded document before a single transactional merge. Existing URIs win. */
    suspend fun importLibrary(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val source = validatedUri(uri.toString())
        val bytes = (resolver.openInputStream(source) ?: throw IOException("Cannot open library JSON.")).use {
            it.readBounded(DataLimits.MAX_LIBRARY_BYTES)
        }
        require(!bytes.truncated) { "Library JSON exceeds the 8 MiB limit."
        }
        val imported = LibraryJson.decode(bytes.bytes)
        var added = 0
        var skipped = 0
        var remappedIds = 0
        database.withTransaction {
            val existing = gameDao.getAll()
            val identities = existing.mapTo(hashSetOf()) { sourceIdentity(it.uri) }
            val ids = existing.mapTo(hashSetOf()) { it.id }
            for (game in imported) {
                currentCoroutineContext().ensureActive()
                if (!identities.add(sourceIdentity(game.uri))) {
                    skipped++
                    continue
                }
                val insert = if (ids.add(game.id)) game else {
                    remappedIds++
                    game.copy(id = java.util.UUID.randomUUID().toString()).also { ids.add(it.id) }
                }
                if (gameDao.insertIfAbsent(insert) == -1L) skipped++ else added++
            }
        }
        val warnings = mutableListOf("仅恢复游戏库元数据，不含会话历史。来源和封面可能需要重新选择授权，JSON 无法转移访问权限。")
        if (remappedIds > 0) warnings.add("$remappedIds 个冲突的游戏 ID 已分配新的 UUID。")
        ImportResult(added = added, skipped = skipped, warnings = warnings)
    }

    private fun validateNote(note: String?) {
        require(note == null || note.length <= DataLimits.MAX_NOTE) { "Session note exceeds 4,096 characters."
        }
    }
}

internal fun isPackageName(value: String): Boolean = value.length <= 255 &&
    value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
