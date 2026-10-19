package dev.gestrin.hub.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OfficialReleaseHtmlTest {
    private val id = RunnerId.WINLATOR
    private val page = "https://github.com/brunodev85/winlator/releases/latest"
    private val fragment = "https://github.com/brunodev85/winlator/releases/expanded_assets/v11.2.0"
    private val apkPath = "/brunodev85/winlator/releases/download/v11.2.0/Winlator_11.2.apk"
    private val hash = "a".repeat(64)

    @Test fun ppssppChoosesHighestNumericStandardHrefRegardlessOfOrder() {
        val result = OfficialReleaseHtml.ppsspp("""
            <a href="/files/1_9_99/ppsspp.apk">old</a>
            <a href='/files/1_20_9/ppsspp.apk'>old</a>
            <a href="https://www.ppsspp.org/files/1_20_10/ppsspp.apk">new</a>
            <a href="/files/9_99_99/ppsspp_vr.apk">VR</a>
            <a href="/files/99_0_beta/ppsspp.apk">beta</a>
            <a href="/files/1_20_4/ppsspp.apk">old</a>
        """.trimIndent())
        assertEquals("1.20.10", result.releaseName)
        assertEquals("https://www.ppsspp.org/files/1_20_10/ppsspp.apk", result.url)
        assertNull(result.sha256)
        assertEquals(-1L, result.size)
    }

    @Test fun ppssppIgnoresInsecureMirrorsScriptsCommentsAndFakeAttributes() {
        val result = OfficialReleaseHtml.ppsspp("""
            <a href="http://www.ppsspp.org/files/999/ppsspp.apk">insecure</a>
            <a href="https://www.ppsspp.org.evil.test/files/999/ppsspp.apk">mirror</a>
            <a href="https://user@www.ppsspp.org/files/999/ppsspp.apk">userinfo</a>
            <a data-href="/files/999/ppsspp.apk">fake</a>
            <!-- <a href="/files/999/ppsspp.apk">comment</a> -->
            <script>var sample = '<a href="/files/999/ppsspp.apk">script</a>';</script>
            <a href="/files/1_20_4/ppsspp.apk">real</a>
        """.trimIndent())
        assertEquals("1.20.4", result.releaseName)
    }

    @Test fun ppssppRequiresAnActualStandardApkLink() {
        assertThrows(IOException::class.java) { OfficialReleaseHtml.ppsspp("<p>https://www.ppsspp.org/files/1_20_4/ppsspp.apk</p>") }
        assertThrows(IOException::class.java) { OfficialReleaseHtml.ppsspp("<a href='/files/1_20_4/ppsspp_vr.apk'>VR</a>") }
    }

    @Test fun extractsOnlyActualOfficialAssetFragment() {
        val result = OfficialReleaseHtml.githubFragment(id, """
            <include-fragment src="/brunodev85/winlator/refs?tag_name=v11.2.0&amp;experimental=1">
            <include-fragment src="https://evil.test/brunodev85/winlator/releases/expanded_assets/v99">
            <include-fragment src="/other/repo/releases/expanded_assets/v99">
            <include-fragment loading="lazy" src="$fragment">
        """.trimIndent(), page)
        assertEquals(fragment, result)
    }

    @Test fun acceptsRelativeAndEntityEncodedAttributes() {
        assertEquals(fragment, OfficialReleaseHtml.githubFragment(id,
            "<include-fragment src='/brunodev85/winlator/releases/expanded_assets/v11&#46;2&#46;0'>", page))
        assertEquals("1.20.4", OfficialReleaseHtml.ppsspp("<A HREF='/files/1_20_4/ppsspp&#46;apk'>APK</A>").releaseName)
    }

    @Test fun missingAmbiguousOrFakeFragmentsFailClosed() {
        listOf(
            "<p>$fragment</p>",
            "<include-fragment data-src='$fragment'>",
            "<include-fragment src='http://github.com/brunodev85/winlator/releases/expanded_assets/v1'>",
            "<include-fragment src='$fragment'><include-fragment src='/brunodev85/winlator/releases/expanded_assets/v2'>",
        ).forEach { html -> assertThrows(IOException::class.java) { OfficialReleaseHtml.githubFragment(id, html, page) } }
    }

    @Test fun assetLinksMustMatchExactRepositoryTagAndApkSuffix() {
        val result = OfficialReleaseHtml.githubAssets(id, """
            <a href="$apkPath">APK</a>
            <a href="/brunodev85/winlator/archive/refs/tags/v11.2.0.zip">source</a>
            <a href="/evil/winlator/releases/download/v11.2.0/evil.apk">wrong repo</a>
            <a href="/brunodev85/winlator/releases/download/v10/old.apk">wrong tag</a>
            <a href="http://github.com$apkPath">http</a>
            <a href="https://github.com.evil.test$apkPath">mirror</a>
            <a href="$apkPath">duplicate</a>
        """.trimIndent(), fragment)
        assertEquals(1, result.size)
        assertEquals("Winlator_11.2.apk", result.single().name)
        assertEquals("https://github.com$apkPath", result.single().url)
        assertNull(result.single().sha256)
    }

    @Test fun htmlHashIsBoundToTheCorrectAssetRow() {
        val result = OfficialReleaseHtml.githubAssets(id, """
            <li><a href="$apkPath">APK</a><span>sha256:$hash</span>
            <clipboard-copy aria-label="Copy to clipboard digest for Winlator_11.2.apk" value="sha256:$hash"></clipboard-copy></li>
            <li><a href="${apkPath.removeSuffix("Winlator_11.2.apk")}other.apk">other</a><span>sha256:${"b".repeat(64)}</span></li>
        """.trimIndent(), fragment)
        assertEquals(hash, result.first { it.name == "Winlator_11.2.apk" }.sha256)
        assertEquals("b".repeat(64), result.first { it.name == "other.apk" }.sha256)
    }

    @Test fun hashesAreNeverBorrowedFromNeighboringRowsOrUnrelatedText() {
        val result = OfficialReleaseHtml.githubAssets(id, """
            <div>sha256:$hash</div><li><a href="$apkPath">APK without hash</a></li>
            <li><a href="${apkPath.removeSuffix("Winlator_11.2.apk")}other.apk">other</a><span>sha256:$hash</span></li>
        """.trimIndent(), fragment)
        assertNull(result.first { it.name == "Winlator_11.2.apk" }.sha256)
    }

    @Test fun conflictingRowDigestsFailAndMissingLinksStayMissing() {
        assertThrows(IOException::class.java) {
            OfficialReleaseHtml.githubAssets(id, "<li><a href='$apkPath'>APK</a><span>sha256:$hash</span><span>sha256:${"b".repeat(64)}</span></li>", fragment)
        }
        assertTrue(OfficialReleaseHtml.githubAssets(id, "<p>Winlator_11.2.apk</p>", fragment).isEmpty())
    }
}
