package dev.gestrin.hub.runner

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LaunchAndDownloadPolicyTest {
    @Test fun manualProjectsNeverAcquireAnAutomaticLaunchContract() {
        for (platform in listOf("WINDOWS", "XP3")) {
            assertFalse(LaunchPolicy.hasVerifiedContract(platform, false, ""))
            assertFalse(LaunchPolicy.hasVerifiedContract(platform, true, ""))
        }
        assertFalse(LaunchPolicy.hasVerifiedContract("VITA", false, ""))
        assertFalse(LaunchPolicy.hasVerifiedContract("PSP", true, ""))
        assertTrue(LaunchPolicy.hasVerifiedContract("PSP", false, ""))
        assertTrue(LaunchPolicy.hasVerifiedContract("VITA", true, "PCSG00000"))
    }
    @Test fun chunkedTransferMayHaveUnknownSizeButCompletionIsStillBounded() {
        assertEquals(-1L, RunnerSecurity.approvedSize(0, -1))
        assertEquals(100L, RunnerSecurity.completedSize(-1, 100))
        assertThrows(IOException::class.java) { RunnerSecurity.completedSize(-1, 0) }
        assertThrows(IOException::class.java) { RunnerSecurity.completedSize(-1, RunnerSecurity.MAX_APK_BYTES + 1) }
    }
    @Test fun knownLengthsCannotSilentlyDisagreeOrTruncate() {
        assertThrows(IOException::class.java) { RunnerSecurity.approvedSize(100, 101) }
        assertThrows(IOException::class.java) { RunnerSecurity.completedSize(100, 99) }
        assertEquals(100L, RunnerSecurity.approvedSize(100, -1))
    }
    @Test fun actualOfficialKirikiroidPackageIsRecognizedWithoutWildcards() {
        val packages = Catalog.get(RunnerId.KIRIKIROID2).expectedPackages
        assertTrue("org.tvp.kirikiri2_free_10309" in packages)
        assertFalse("org.tvp.kirikiri2_free_untrusted" in packages)
        assertNotNull(RunnerSecurity.knownReleaseIssue(RunnerId.KIRIKIROID2, "1.3.9", 36))
        assertNull(RunnerSecurity.knownReleaseIssue(RunnerId.KIRIKIROID2, "1.4.0", 36))
        assertNull(RunnerSecurity.knownReleaseIssue(RunnerId.KIRIKIROID2, "1.3.9", 30))
        assertEquals(24, RunnerSecurity.minimumTarget(36))
    }
}
