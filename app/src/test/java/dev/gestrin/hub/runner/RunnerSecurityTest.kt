package dev.gestrin.hub.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RunnerSecurityTest {
    private val hosts = setOf("github.com", "release-assets.githubusercontent.com")
    private val hash = "a".repeat(64)

    @Test fun exactHttpsHostsOnly() {
        assertEquals("github.com", RunnerSecurity.httpsUri("https://github.com/o/r/releases/download/v1/app.apk", hosts).host)
        assertEquals("GITHUB.COM", RunnerSecurity.httpsUri("https://GITHUB.COM:443/a.apk", hosts).host)
        listOf(
            "http://github.com/a.apk", "https://github.com.evil.test/a.apk",
            "https://evilgithub.com/a.apk", "https://github.com@evil.test/a.apk",
            "https://user@github.com/a.apk", "https://github.com:444/a.apk",
            "https://github.com/a.apk#ignored", "file:///tmp/app.apk",
            "https://github.com\\@evil.test/app.apk", "//github.com/a.apk",
        ).forEach { url -> assertThrows(url, IOException::class.java) { RunnerSecurity.httpsUri(url, hosts) } }
    }

    @Test fun sizesAreBoundedAndMustBeKnown() {
        RunnerSecurity.checkSize(1)
        RunnerSecurity.checkSize(RunnerSecurity.MAX_APK_BYTES)
        listOf(-1L, 0L, RunnerSecurity.MAX_APK_BYTES + 1, Long.MAX_VALUE).forEach {
            assertThrows(IOException::class.java) { RunnerSecurity.checkSize(it) }
        }
    }

    @Test fun publishedSha256MustHaveExactly64HexDigits() {
        assertEquals(hash, RunnerSecurity.sha256("sha256:${hash.uppercase()}"))
        listOf("", "a".repeat(63), "a".repeat(65), "z".repeat(64), "sha512:$hash").forEach {
            assertThrows(IOException::class.java) { RunnerSecurity.sha256(it) }
        }
    }

    @Test fun checksumsMatchExactAssetNames() {
        assertEquals(hash, RunnerSecurity.checksum("$hash  app.apk", "app.apk"))
        assertEquals(hash, RunnerSecurity.checksum("$hash *./app.apk", "app.apk"))
        assertEquals(hash, RunnerSecurity.checksum("SHA256 (app.apk) = $hash", "app.apk"))
        assertNull(RunnerSecurity.checksum("$hash  other-app.apk", "app.apk"))
        assertNull(RunnerSecurity.checksum("$hash  ../app.apk", "app.apk"))
        assertNull(RunnerSecurity.checksum("$hash  app.apk.zip", "app.apk"))
    }

    @Test fun bareDigestOnlyAcceptedForAssetSpecificSidecar() {
        assertNull(RunnerSecurity.checksum(hash, "app.apk"))
        assertEquals(hash, RunnerSecurity.checksum(hash, "app.apk", allowBare = true))
        assertNull(RunnerSecurity.checksum("sha256:$hash", "app.apk", allowBare = true))
    }

    @Test fun conflictingChecksumsAreRejected() {
        assertThrows(IOException::class.java) {
            RunnerSecurity.checksum("$hash  app.apk\n${"b".repeat(64)}  app.apk", "app.apk")
        }
        assertEquals(hash, RunnerSecurity.checksum("$hash  app.apk\n$hash  app.apk", "app.apk"))
    }

    @Test fun updateSignersRequireExactNonemptySets() {
        assertTrue(RunnerSecurity.matchingSigners(setOf("a", "b"), setOf("b", "a")))
        assertFalse(RunnerSecurity.matchingSigners(emptySet(), emptySet()))
        assertFalse(RunnerSecurity.matchingSigners(setOf("a"), setOf("b")))
        assertFalse(RunnerSecurity.matchingSigners(setOf("a", "b"), setOf("a")))
        assertFalse(RunnerSecurity.matchingSigners(setOf("a"), emptySet()))
    }

    @Test fun catalogHasExactlyFiveCategoriesAndNoSpoofedPackages() {
        assertEquals(RunnerId.entries.toSet(), Catalog.runners.map { it.id }.toSet())
        assertEquals(5, Catalog.runners.size)
        assertEquals(setOf("com.winlator"), Catalog.get(RunnerId.WINLATOR).expectedPackages)
        assertEquals(setOf("org.tvp.kirikiri2"), Catalog.get(RunnerId.KIRIKIROID2).expectedPackages)
        assertEquals(setOf("org.ppsspp.ppsspp"), Catalog.get(RunnerId.PPSSPP).expectedPackages)
        assertEquals(setOf("org.vita3k.emulator"), Catalog.get(RunnerId.VITA3K).expectedPackages)
        assertFalse("com.miHoYo.Yuanshen" in Catalog.get(RunnerId.EDEN).expectedPackages)
        assertEquals("Vita3K/Vita3K", Catalog.repository(RunnerId.VITA3K))
        Catalog.runners.forEach { RunnerSecurity.httpsUri(it.officialPage, Catalog.assetHosts(it.id)) }
    }
}
