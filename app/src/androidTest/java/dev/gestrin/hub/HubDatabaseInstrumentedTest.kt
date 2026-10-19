package dev.gestrin.hub

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gestrin.hub.data.Game
import dev.gestrin.hub.data.HubDatabase
import dev.gestrin.hub.data.Platform
import dev.gestrin.hub.data.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises generated Room code and Android SQLite, never the user's singleton database. */
@RunWith(AndroidJUnit4::class)
class HubDatabaseInstrumentedTest {
    private lateinit var database: HubDatabase

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HubDatabase::class.java
        ).build()
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun insertedGameRoundTripsAllFieldsAndIsObserved() = runBlocking {
        val game = sampleGame().copy(
            title = "自制 PSP 样例", favorite = true,
            coverUri = "content://test.provider/covers/sample.png", lastOpened = 1234L
        )
        assertTrue(database.gameDao().insert(game) != -1L)

        assertEquals(game, database.gameDao().getById(game.id))
        assertEquals(game, database.gameDao().getByUri(game.uri))
        assertEquals(listOf(game), database.gameDao().getAll())
        withTimeout(5_000) {
            assertEquals(game, database.gameDao().observeById(game.id).first())
            assertEquals(listOf(game), database.gameDao().observeFavorites().first())
            assertEquals(listOf(game), database.gameDao().observePlatform(Platform.PSP.name).first())
            assertTrue(database.gameDao().observePlatform(Platform.VITA.name).first().isEmpty())
        }
    }

    @Test
    fun repeatedImportPreservesUserEditsAndExistingSession() = runBlocking {
        val original = sampleGame().copy(title = "My edited title", favorite = true)
        database.gameDao().insert(original)
        val session = Session(id = "session-preserve", gameId = original.id, startedAt = 100L)
        database.sessionDao().insert(session)

        val duplicate = original.copy(id = "other-id", title = "Scanned title", favorite = false)
        assertEquals(-1L, database.gameDao().insertIfAbsent(duplicate))
        assertEquals(listOf(original), database.gameDao().getAll())
        assertNull(database.gameDao().getById(duplicate.id))
        assertEquals(session, database.sessionDao().getById(session.id))
    }

    @Test
    fun deletingGameCascadesOnlyItsSessionsAndRejectsOrphans() = runBlocking {
        val removed = sampleGame()
        val retained = sampleGame().copy(id = "retained", uri = "content://test.provider/retained.iso")
        database.gameDao().insert(removed)
        database.gameDao().insert(retained)
        val removedSession = Session(id = "removed-session", gameId = removed.id)
        val retainedSession = Session(id = "retained-session", gameId = retained.id)
        database.sessionDao().insert(removedSession)
        database.sessionDao().insert(retainedSession)

        assertEquals(1, database.gameDao().deleteById(removed.id))
        assertNull(database.gameDao().getById(removed.id))
        assertNull(database.sessionDao().getById(removedSession.id))
        assertEquals(retained, database.gameDao().getById(retained.id))
        assertEquals(retainedSession, database.sessionDao().getById(retainedSession.id))
        try {
            database.sessionDao().insert(Session(id = "orphan", gameId = removed.id))
            fail("SQLite must reject a session whose game no longer exists")
        } catch (_: SQLiteConstraintException) {
            // Expected real foreign-key enforcement, not a mocked repository response.
        }
    }

    @Test
    fun openedTimeDoesNotRegressAndSessionCanOnlyFinishOnce() = runBlocking {
        val game = sampleGame().copy(lastOpened = 200L)
        database.gameDao().insert(game)
        assertEquals(1, database.gameDao().markOpened(game.id, 100L))
        assertEquals(200L, database.gameDao().getById(game.id)?.lastOpened)
        assertEquals(1, database.gameDao().markOpened(game.id, 300L))
        assertEquals(300L, database.gameDao().getById(game.id)?.lastOpened)

        val session = Session(id = "finish-once", gameId = game.id, startedAt = 300L)
        database.sessionDao().insert(session)
        assertEquals(1, database.sessionDao().finish(session.id, 100L, "Returned to Hub"))
        assertEquals(session.copy(endedAt = 300L, note = "Returned to Hub"), database.sessionDao().getById(session.id))
        assertEquals(0, database.sessionDao().finish(session.id, 500L, "Overwrite"))
        assertEquals(300L, database.sessionDao().getById(session.id)?.endedAt)
    }

    private fun sampleGame() = Game(
        id = "instrumented-game", title = "Homebrew sample", platform = Platform.PSP.name,
        uri = "content://test.provider/games/sample.iso", isDirectory = false
    )
}
