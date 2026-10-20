package dev.gestrin.hub.runner

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

enum class RunnerId { WINLATOR, KIRIKIROID2, PPSSPP, VITA3K, EDEN }

data class RunnerSpec(
    val id: RunnerId,
    val name: String,
    val officialPage: String,
    val expectedPackages: Set<String>,
)

/** No binaries or third-party mirrors. Package IDs were checked in upstream Android sources.
 * Winlator's app is an upstream submodule: brunodev85/winlator-app/app/build.gradle.
 * Kirikiroid2: zeas2/Kirikiroid2/project/android/AndroidManifest.xml.
 * PPSSPP: hrydgard/ppsspp/android/build.gradle.kts.
 * Vita3K: Vita3K/Vita3K/android/app/build.gradle (main repository).
 * Eden: git.eden-emu.dev/eden-emu/eden/src/branch/master/src/android/app/build.gradle.kts.
 * Eden's optimized APK intentionally impersonates another app; it is NEVER accepted here.
 */
object Catalog {
    val runners: List<RunnerSpec> = listOf(
        RunnerSpec(RunnerId.WINLATOR, "Winlator", "https://github.com/brunodev85/winlator/releases", setOf("com.winlator")),
        RunnerSpec(RunnerId.KIRIKIROID2, "Kirikiroid2", "https://github.com/zeas2/Kirikiroid2/releases", setOf("org.tvp.kirikiri2", "org.tvp.kirikiri2_free_10309")),
        RunnerSpec(RunnerId.PPSSPP, "PPSSPP", "https://www.ppsspp.org/download/", setOf("org.ppsspp.ppsspp")),
        RunnerSpec(RunnerId.VITA3K, "Vita3K", "https://github.com/Vita3K/Vita3K/releases/tag/continuous", setOf("org.vita3k.emulator")),
        RunnerSpec(RunnerId.EDEN, "Eden", "https://git.eden-emu.dev/eden-emu/eden/releases", setOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator")),
    )

    fun get(id: RunnerId): RunnerSpec = runners.first { it.id == id }

    internal fun repository(id: RunnerId): String = when (id) {
        RunnerId.WINLATOR -> "brunodev85/winlator"
        RunnerId.KIRIKIROID2 -> "zeas2/Kirikiroid2"
        RunnerId.PPSSPP -> "hrydgard/ppsspp"
        RunnerId.VITA3K -> "Vita3K/Vita3K"
        RunnerId.EDEN -> "eden-emu/eden"
    }

    internal fun assetHosts(id: RunnerId): Set<String> = when (id) {
        RunnerId.EDEN -> setOf("git.eden-emu.dev", "stable.eden-emu.dev")
        RunnerId.PPSSPP -> githubAssetHosts + setOf("www.ppsspp.org", "ppsspp.org")
        else -> githubAssetHosts
    }

    private val githubAssetHosts = setOf(
        "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )
}

/** Pure policy helpers, also usable by local JVM security tests. No suffix host matching. */
internal object RunnerSecurity {
    const val MAX_APK_BYTES = 512L * 1024 * 1024

    fun httpsUri(value: String, hosts: Set<String>): URI {
        val uri = try { URI(value) } catch (_: Exception) { throw IOException("Malformed official URL") }
        if (!uri.scheme.equals("https", true) || uri.host?.lowercase(Locale.ROOT) !in hosts ||
            uri.rawUserInfo != null || uri.rawFragment != null || uri.port !in listOf(-1, 443)) {
            throw IOException("URL is not on an allowed HTTPS host: ${uri.host ?: "missing host"}")
        }
        return uri
    }

    fun checkSize(size: Long) {
        if (size <= 0 || size > MAX_APK_BYTES) throw IOException("APK size $size is outside 1..$MAX_APK_BYTES bytes")
    }
    fun approvedSize(declared: Long, observed: Long): Long {
        if (declared > 0 && observed > 0 && declared != observed) throw IOException("Asset size disagrees with official release metadata")
        val size = if (observed > 0) observed else declared
        if (size > 0) checkSize(size)
        return if (size > 0) size else -1L
    }
    fun completedSize(expected: Long, actual: Long): Long {
        checkSize(actual)
        if (expected > 0 && expected != actual) throw IOException("Downloaded APK size does not match approved size")
        return actual
    }
    fun minimumTarget(sdk: Int) = when { sdk >= 35 -> 24; sdk >= 34 -> 23; else -> 0 }
    fun knownReleaseIssue(id: RunnerId, tag: String, sdk: Int): String? =
        if (id == RunnerId.KIRIKIROID2 && tag == "1.3.9" && sdk >= 34)
            "官方 Kirikiroid2 1.3.9 面向 API 11，Android 14 及以上无法新安装。请绑定兼容的本机应用，或导入已适配系统的 APK。"
        else null

    fun sha256(value: String): String {
        val hash = value.removePrefix("sha256:").trim().lowercase(Locale.ROOT)
        if (!hash.matches(Regex("[0-9a-f]{64}"))) throw IOException("Invalid published SHA-256")
        return hash
    }

    fun matchingSigners(installed: Set<String>, archive: Set<String>): Boolean =
        installed.isNotEmpty() && installed == archive

    /** GNU sha256sum, BSD SHA256(file), or a bare digest for an APK-specific sidecar. */
    fun checksum(text: String, assetName: String, allowBare: Boolean = false): String? {
        val hashes = text.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            val gnu = Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?(.+)$").matchEntire(trimmed)
            val bsd = Regex("^SHA256 ?\\((.+)\\) ?= ?([a-fA-F0-9]{64})$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
            when {
                gnu != null && gnu.groupValues[2].removePrefix("./") == assetName -> sha256(gnu.groupValues[1])
                bsd != null && bsd.groupValues[1].removePrefix("./") == assetName -> sha256(bsd.groupValues[2])
                allowBare && trimmed.matches(Regex("[a-fA-F0-9]{64}")) -> sha256(trimmed)
                else -> null
            }
        }.toSet()
        if (hashes.size > 1) throw IOException("Conflicting published SHA-256 values for $assetName")
        return hashes.singleOrNull()
    }
}

internal data class ResolvedApk(
    val releaseName: String,
    val assetName: String,
    val url: String,
    val size: Long,
    val sha256: String?,
)

/** Bounded, cancellable reads; redirects are checked individually before any connection. */
internal object OfficialHttp {
    private const val MAX_REDIRECTS = 5

    suspend fun <T> request(
        url: String,
        hosts: Set<String>,
        method: String = "GET",
        range: Boolean = false,
        consume: suspend (HttpURLConnection) -> T,
    ): T {
        var target = RunnerSecurity.httpsUri(url, hosts)
        repeat(MAX_REDIRECTS + 1) { attempt ->
            currentCoroutineContext().ensureActive()
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.requestMethod = method
                connection.setRequestProperty("User-Agent", "GestrinHub/1 Android runner installer")
                connection.setRequestProperty("Accept", "application/vnd.github+json, application/json, */*")
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (range) connection.setRequestProperty("Range", "bytes=0-0")
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    if (attempt == MAX_REDIRECTS) throw IOException("Too many official download redirects")
                    val location = connection.getHeaderField("Location") ?: throw IOException("Redirect has no Location")
                    target = RunnerSecurity.httpsUri(target.resolve(location).toString(), hosts)
                } else {
                    if (code !in 200..299) {
                        val retry = connection.getHeaderField("Retry-After")
                        val reset = connection.getHeaderField("X-RateLimit-Reset")
                        throw OfficialHttpException(code, "HTTP $code from ${target.host}" +
                            (retry?.let { "; Retry-After: $it" } ?: "") +
                            (reset?.let { "; rate limit reset (epoch seconds): $it" } ?: ""))
                    }
                    currentCoroutineContext().ensureActive()
                    return consume(connection)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Redirect limit exceeded")
    }

    suspend fun text(url: String, hosts: Set<String>, limit: Int = 2 * 1024 * 1024): String =
        request(url, hosts) { connection ->
            if (connection.contentLengthLong > limit) throw IOException("Official metadata exceeds $limit bytes")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) throw IOException("Official metadata exceeds $limit bytes")
                    output.write(buffer, 0, count)
                }
            }
            output.toString(Charsets.UTF_8.name())
        }

    /** HEAD does not download an APK. The returned CDN URL has passed every redirect check. */
    suspend fun preflight(id: RunnerId, apk: ResolvedApk): ResolvedApk {
        suspend fun probe(method: String, range: Boolean): ResolvedApk = request(apk.url, Catalog.assetHosts(id), method, range) { c ->
            val contentType = c.contentType.orEmpty().lowercase(Locale.ROOT)
            if ("text/html" in contentType || "application/json" in contentType) throw IOException("Official asset returned $contentType instead of an APK")
            val length = if (c.responseCode == 206) {
                c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
            } else c.contentLengthLong
            val size = RunnerSecurity.approvedSize(apk.size, length)
            // Keep the stable publisher URL, not a short-lived signed CDN redirect.
            apk.copy(size = if (size > 0) size else -1L)
        }
        return try { probe("HEAD", false) } catch (e: OfficialHttpException) {
            if (e.status !in setOf(403, 405, 501)) throw e
            probe("GET", true)
        }
    }
}

internal class OfficialHttpException(val status: Int, message: String) : IOException(message)

/** Narrow upstream HTML extraction. Every returned URL was present in a real attribute. */
internal object OfficialReleaseHtml {
    data class Asset(val name: String, val url: String, val sha256: String? = null)

    private fun clean(html: String): String = html
        .replace(Regex("<!--[\\s\\S]*?-->"), "")
        .replace(Regex("<(script|style)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE), "")

    private fun tags(html: String, tag: String): Sequence<String> =
        Regex("<$tag(?=\\s|/?>)(?:\"[^\"]*\"|'[^']*'|[^'\">])*>", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }

    private fun attribute(tag: String, name: String): String? {
        val matches = Regex("(?:^|\\s)([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))")
            .findAll(tag).filter { it.groupValues[1].equals(name, true) }.toList()
        val match = matches.singleOrNull() ?: return null
        val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groupValues[4]
        return Regex("&(?:amp|quot|apos|lt|gt|#\\d+|#x[0-9a-fA-F]+);").replace(value) {
            when (val entity = it.value) {
                "&amp;" -> "&"
                "&quot;" -> "\""
                "&apos;" -> "'"
                "&lt;" -> "<"
                "&gt;" -> ">"
                else -> {
                    val number = entity.removePrefix("&#").removeSuffix(";")
                    val code = if (number.startsWith("x")) number.drop(1).toIntOrNull(16) else number.toIntOrNull()
                    if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF)
                        String(Character.toChars(code)) else entity
                }
            }
        }
    }

    private fun url(base: String, raw: String?, hosts: Set<String>): URI? = try {
        if (raw == null) null else RunnerSecurity.httpsUri(URI(base).resolve(raw).toString(), hosts)
            .takeIf { it.normalize().rawPath == it.rawPath && it.rawQuery == null }
    } catch (_: Exception) { null }

    fun ppsspp(html: String): ResolvedApk {
        val page = Catalog.get(RunnerId.PPSSPP).officialPage
        val candidates = tags(clean(html), "a").mapNotNull { tag ->
            val uri = url(page, attribute(tag, "href"), setOf("www.ppsspp.org", "ppsspp.org")) ?: return@mapNotNull null
            val version = Regex("^/files/([0-9]+(?:_[0-9]+)*)/ppsspp\\.apk$").matchEntire(uri.rawPath)
                ?.groupValues?.get(1) ?: return@mapNotNull null
            version to uri.toString()
        }.distinct().toList()
        // Numeric tuples, so 1_20_10 sorts above 1_20_9, and 1_20 above 1_9_99.
        val best = candidates.maxWithOrNull { a, b ->
            val left = a.first.split('_').map { it.toBigInteger() }
            val right = b.first.split('_').map { it.toBigInteger() }
            (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { i ->
                val comparison = (left.getOrNull(i) ?: java.math.BigInteger.ZERO)
                    .compareTo(right.getOrNull(i) ?: java.math.BigInteger.ZERO)
                comparison.takeIf { it != 0 }
            } ?: 0
        } ?: throw IOException("PPSSPP official page has no standard numeric-version APK. Open $page")
        return ResolvedApk(best.first.replace('_', '.'), "ppsspp.apk", best.second, -1, null)
    }

    fun githubFragment(id: RunnerId, html: String, page: String): String {
        val prefix = "/${Catalog.repository(id)}/releases/expanded_assets/"
        val fragments = tags(clean(html), "include-fragment").mapNotNull { tag ->
            url(page, attribute(tag, "src"), setOf("github.com"))
                ?.takeIf { it.path.startsWith(prefix) && it.path.length > prefix.length &&
                    !it.path.substring(prefix.length).split('/').any { part -> part == ".." || part == "." } }
                ?.toString()
        }.distinct().toList()
        return fragments.singleOrNull() ?: throw IOException("Official release page has no unique asset fragment")
    }

    fun githubAssets(id: RunnerId, html: String, fragment: String): List<Asset> {
        val repo = Catalog.repository(id)
        val fragmentUri = RunnerSecurity.httpsUri(fragment, setOf("github.com"))
        val fragmentPrefix = "/$repo/releases/expanded_assets/"
        if (!fragmentUri.rawPath.startsWith(fragmentPrefix)) throw IOException("Asset fragment belongs to another repository")
        val tag = fragmentUri.rawPath.substring(fragmentPrefix.length)
        if (tag.isEmpty()) throw IOException("Official asset fragment has no release tag")
        val assetPrefix = "/$repo/releases/download/$tag/"
        val document = clean(html)
        fun links(block: String): List<URI> = tags(block, "a").mapNotNull { anchor ->
            url(fragment, attribute(anchor, "href"), setOf("github.com"))?.takeIf {
                it.rawPath.startsWith(assetPrefix) && it.path.substringAfter("/releases/download/")
                    .split('/').none { part -> part == ".." || part == "." } &&
                    it.rawPath.substring(assetPrefix.length).let { name -> name.isNotEmpty() && '/' !in name }
            }
        }.distinct().toList()
        val rows = Regex("<li\\b[^>]*>[\\s\\S]*?</li\\s*>", RegexOption.IGNORE_CASE).findAll(document).map { it.value }.toList()
        return links(document).map { uri ->
            val name = uri.path.substringAfterLast('/')
            val hashes = rows.filter { links(it) == listOf(uri) }.flatMap { row ->
                // GitHub's visible per-asset digest; never borrow a neighboring APK's hash.
                Regex(">\\s*sha256:([a-fA-F0-9]{64})\\s*<").findAll(row).map { RunnerSecurity.sha256(it.groupValues[1]) }.toList() +
                    tags(row, "clipboard-copy").mapNotNull { copy ->
                        if (attribute(copy, "aria-label") != "Copy to clipboard digest for $name") null
                        else attribute(copy, "value")?.takeIf { it.startsWith("sha256:") }?.let { RunnerSecurity.sha256(it) }
                    }.toList()
            }.toSet()
            if (hashes.size > 1) throw IOException("Conflicting official HTML checksums for $name")
            Asset(name, uri.toString(), hashes.singleOrNull())
        }
    }
}

internal object ReleaseResolver {
    suspend fun resolve(id: RunnerId, abis: List<String>, sdk: Int): ResolvedApk {
        // PPSSPP publishes Android APKs on its own page, independently of GitHub assets/API.
        if (id == RunnerId.PPSSPP) {
            val page = Catalog.get(id).officialPage
            val apk = OfficialReleaseHtml.ppsspp(OfficialHttp.text(page, setOf("www.ppsspp.org", "ppsspp.org")))
            return OfficialHttp.preflight(id, apk)
        }
        val repo = Catalog.repository(id)
        val apiHost = if (id == RunnerId.EDEN) "git.eden-emu.dev" else "api.github.com"
        val api = when (id) {
            RunnerId.EDEN -> "https://$apiHost/api/v1/repos/$repo/releases/latest"
            // Upstream publishes current Android builds on this moving official release.
            RunnerId.VITA3K -> "https://$apiHost/repos/$repo/releases/tags/continuous"
            else -> "https://$apiHost/repos/$repo/releases/latest"
        }
        val metadata = try { OfficialHttp.text(api, setOf(apiHost)) } catch (e: OfficialHttpException) {
            if (id == RunnerId.EDEN || e.status !in setOf(403, 404, 429)) throw e
            return githubHtml(id, abis, sdk)
        }
        val release = JSONObject(metadata)
        if (release.optBoolean("draft") || (release.optBoolean("prerelease") && id != RunnerId.VITA3K)) throw IOException("Official latest release is not stable")
        val tag = release.getString("tag_name")
        RunnerSecurity.knownReleaseIssue(id, tag, sdk)?.let { throw IOException(it) }
        val releaseName = release.optString("name").ifBlank { tag }
        val assets = release.optJSONArray("assets")
        val all = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
        val apks = all.filter { it.optString("name").endsWith(".apk", true) }
        val chosen = chooseApk(id, apks, abis, sdk)
        if (chosen == null) {
            throw IOException("No compatible standalone APK in $releaseName. Open ${Catalog.get(id).officialPage}")
        }
        val name = chosen.getString("name")
        val url = chosen.getString("browser_download_url")
        checkAssetOrigin(id, url)
        val size = chosen.optLong("size", -1)
        if (size > RunnerSecurity.MAX_APK_BYTES) RunnerSecurity.checkSize(size)
        return OfficialHttp.preflight(id, ResolvedApk(releaseName, name, url, size, publishedHash(id, chosen, release.optString("body"), all)))
    }

    private suspend fun publishedHash(id: RunnerId, chosen: JSONObject, body: String, all: List<JSONObject>): String? {
        val name = chosen.getString("name")
        val digests = mutableSetOf<String>()
        val digest = chosen.optString("digest")
        if (digest.startsWith("sha256:", true)) digests += RunnerSecurity.sha256(digest.lowercase(Locale.ROOT))
        RunnerSecurity.checksum(body, name)?.let { digests += it }
        // Fetch only checksum assets actually advertised by the same official release.
        all.filter {
            val n = it.optString("name").lowercase(Locale.ROOT)
            n in setOf("${name.lowercase(Locale.ROOT)}.sha256", "${name.lowercase(Locale.ROOT)}.sha256sum", "sha256sums", "sha256sums.txt", "sha256sum.txt", "checksums.txt", "sha256.txt")
        }.forEach { sidecar ->
            val checksumUrl = sidecar.getString("browser_download_url")
            checkAssetOrigin(id, checksumUrl)
            val text = OfficialHttp.text(checksumUrl, Catalog.assetHosts(id), 512 * 1024)
            val specific = sidecar.getString("name").startsWith("$name.", true)
            val hash = RunnerSecurity.checksum(text, name, specific)
            if (specific && hash == null) throw IOException("Published APK checksum file contains no valid SHA-256")
            hash?.let { digests += it }
        }
        if (digests.size > 1) throw IOException("Official checksum sources disagree")
        return digests.singleOrNull()
    }

    private suspend fun githubHtml(id: RunnerId, abis: List<String>, sdk: Int): ResolvedApk {
        val repo = Catalog.repository(id)
        val page = if (id == RunnerId.VITA3K) Catalog.get(id).officialPage else "https://github.com/$repo/releases/latest"
        val releaseHtml = OfficialHttp.text(page, setOf("github.com"))
        val fragment = OfficialReleaseHtml.githubFragment(id, releaseHtml, page)
        val html = OfficialHttp.text(fragment, setOf("github.com"))
        val all = OfficialReleaseHtml.githubAssets(id, html, fragment).map { asset ->
            JSONObject().apply {
                put("name", asset.name); put("browser_download_url", asset.url); put("size", -1)
                asset.sha256?.let { put("digest", "sha256:$it") }
            }
        }
        val chosen = chooseApk(id, all.filter { it.getString("name").endsWith(".apk", true) }, abis, sdk)
            ?: throw IOException("Official release HTML has no compatible APK. Open $page")
        val name = chosen.getString("name")
        val url = chosen.getString("browser_download_url")
        checkAssetOrigin(id, url)
        val tag = URI(fragment).path.substringAfter("/releases/expanded_assets/")
        RunnerSecurity.knownReleaseIssue(id, tag, sdk)?.let { throw IOException(it) }
        val hash = publishedHash(id, chosen, "", all)
        return OfficialHttp.preflight(id, ResolvedApk(tag, name, url, -1, hash))
    }

    private fun chooseApk(id: RunnerId, assets: List<JSONObject>, abis: List<String>, sdk: Int): JSONObject? {
        if (id == RunnerId.VITA3K) return assets.singleOrNull { it.getString("name") == "android-latest.apk" }
        if (id == RunnerId.EDEN) {
            val suffix = when {
                "arm64-v8a" in abis && sdk >= 33 -> "-standard.apk"
                "arm64-v8a" in abis && sdk >= 29 -> "-legacy.apk"
                "x86_64" in abis -> "-chromeos.apk"
                else -> return null
            }
            return assets.singleOrNull { it.getString("name").endsWith(suffix, true) }
        }
        val compatible = assets.filter { asset ->
            val name = asset.getString("name").lowercase(Locale.ROOT)
            !Regex("debug|unsigned|androidtest|\\btest\\b|\\bvr\\b|gold").containsMatchIn(name) &&
                (!("arm64" in name || "aarch64" in name) || "arm64-v8a" in abis) &&
                (!("armeabi" in name || "armv7" in name) || "armeabi-v7a" in abis) &&
                (!("x86_64" in name || "x64" in name) || "x86_64" in abis) &&
                (!("x86" in name && "x86_64" !in name) || "x86" in abis)
        }
        if (compatible.size <= 1) return compatible.singleOrNull()
        val universal = compatible.filter { Regex("universal|all-abi").containsMatchIn(it.getString("name").lowercase(Locale.ROOT)) }
        if (universal.size == 1) return universal.single()
        for (abi in abis) {
            val tokens = when (abi) {
                "arm64-v8a" -> listOf("arm64", "aarch64")
                "armeabi-v7a" -> listOf("armeabi", "armv7")
                "x86_64" -> listOf("x86_64", "x64")
                "x86" -> listOf("x86")
                else -> emptyList()
            }
            val matches = compatible.filter { a -> tokens.any { it in a.getString("name").lowercase(Locale.ROOT) } }
            if (matches.size == 1) return matches.single()
        }
        throw IOException("Official release has multiple APK variants; cannot safely select one")
    }

    private fun checkAssetOrigin(id: RunnerId, value: String) {
        val uri = RunnerSecurity.httpsUri(value, Catalog.assetHosts(id))
        val path = uri.path
        val expected = "/${Catalog.repository(id)}/releases/download/"
        val valid = if (id == RunnerId.EDEN) {
            uri.host == "stable.eden-emu.dev" || (uri.host == "git.eden-emu.dev" &&
                (path.startsWith(expected) || path.startsWith("/attachments/")))
        } else uri.host == "github.com" && path.startsWith(expected)
        if (!valid) throw IOException("Asset link does not belong to the official release repository")
    }

}
