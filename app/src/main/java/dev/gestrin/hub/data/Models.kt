package dev.gestrin.hub.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class Platform(val label: String) {
    WINDOWS("Windows"), XP3("XP3 / KiriKiri"), PSP("PSP"),
    VITA("PS Vita"), SWITCH("Switch")
}

@Entity(tableName = "games", indices = [Index(value = ["uri"], unique = true)])
data class Game(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** Persist Platform.name, not its display label. */
    val platform: String,
    val uri: String,
    val isDirectory: Boolean,
    val favorite: Boolean = false,
    val coverUri: String? = null,
    val lastOpened: Long = 0
)

enum class SessionOutcome { UNVERIFIED, FAILED_TO_START, STARTED, PLAYABLE, CRASHED, FINISHED }

/** A recorded handoff/request, not evidence that a game ran or exited successfully. */
@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = Game::class, parentColumns = ["id"], childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId")]
)
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val gameId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val runnerPackage: String? = null,
    val note: String? = null,
    val outcome: String = SessionOutcome.UNVERIFIED.name
)

data class ImportCandidate(val title: String, val uri: String, val isDirectory: Boolean)

data class ImportResult(
    val added: Int = 0,
    val skipped: Int = 0,
    val needsPlatformSelection: List<ImportCandidate> = emptyList(),
    val warnings: List<String> = emptyList()
)

enum class DiagnosticSeverity { INFO, WARNING, ERROR }

data class DiagnosticFinding(
    val code: String,
    val severity: DiagnosticSeverity,
    val title: String,
    /** A rule's observation, never a claim of verified compatibility or root cause. */
    val evidence: String,
    val suggestion: String
)

data class DiagnosticReport(
    val findings: List<DiagnosticFinding>,
    val redactedLog: String? = null,
    val logTruncated: Boolean = false,
    val checkedAt: Long = System.currentTimeMillis(),
    val engine: String = "local-rules-v1",
    val modelUsed: Boolean = false
)
