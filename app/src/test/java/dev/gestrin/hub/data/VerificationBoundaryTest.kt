package dev.gestrin.hub.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure input boundaries: these assertions do not use mocked Android return values. */
class VerificationBoundaryTest {
    @Test
    fun ambiguousImagesAndArchivesRequireAnExplicitPlatform() {
        listOf("sample.iso", "sample.zip", "sample.7z", "sample.bin", "sample.pkg", "sample.elf")
            .forEach { assertNull("Must not guess a platform for $it", SafTreeScanner.detectPlatform(it)) }
        assertEquals(Platform.PSP, SafTreeScanner.detectPlatform("HOMEBREW.CSO"))
        assertEquals(Platform.XP3, SafTreeScanner.detectPlatform("game.Xp3"))
        assertEquals(Platform.WINDOWS, SafTreeScanner.detectPlatform("game.exe"))
        assertEquals(Platform.VITA, SafTreeScanner.detectPlatform("homebrew.vpk"))
        assertEquals(Platform.SWITCH, SafTreeScanner.detectPlatform("sample.xci"))
        assertNull(SafTreeScanner.detectPlatform("game.exe.txt"))
    }

    @Test
    fun boundedReadsDistinguishExactLimitFromTruncation() = runBlocking {
        val exact = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)).readBounded(4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), exact.bytes)
        assertFalse(exact.truncated)
        val longer = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6)).readBounded(4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), longer.bytes)
        assertTrue(longer.truncated)
        val empty = ByteArrayInputStream(byteArrayOf()).readBounded(4)
        assertTrue(empty.bytes.isEmpty())
        assertFalse(empty.truncated)
    }

    @Test
    fun boundedReadMakesProgressWhenStreamReturnsZeroForBulkRead() = runBlocking {
        val stream = object : InputStream() {
            private val delegate = ByteArrayInputStream(byteArrayOf(1, 2, 3))
            override fun read(): Int = delegate.read()
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        val result = stream.use { it.readBounded(2) }
        assertArrayEquals(byteArrayOf(1, 2), result.bytes)
        assertTrue(result.truncated)
    }

    @Test
    fun importedDisplayNamesCannotRetainControlCharactersOrGrowWithoutBound() {
        assertEquals("未命名游戏", safeTitle(" \n\t "))
        assertEquals("my game", safeTitle("  my\ngame  "))
        val title = safeTitle("x".repeat(DataLimits.MAX_TITLE + 1))
        assertEquals(DataLimits.MAX_TITLE, title.length)
        assertFalse(safeTitle("game\u0000name").any { it.isISOControl() })
    }
}
