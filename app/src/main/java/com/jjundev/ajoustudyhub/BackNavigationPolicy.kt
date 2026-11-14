package com.jjundev.ajoustudyhub

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Resolves how many WebView history steps to move back on a system Back press.
 *
 * Inside trusted `/content/` documents, internal topic transitions push `#fragment` or pathname
 * entries onto the browser history stack. This policy skips consecutive entries belonging to the
 * same normalized document so a single Back press returns to the previous document or catalog.
 */
class BackNavigationPolicy(
    allowedBaseUrl: String,
    additionalInAppHosts: Set<String> = emptySet(),
    internal val allowInsecureHttpForTests: Boolean = false
) {
    private val baseUri: URI? = parseUriOrNull(allowedBaseUrl)
    private val baseHost: String? = baseUri?.host?.lowercase(Locale.ROOT)
    private val basePort: Int = baseUri?.let { uri ->
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != null) effectivePort(scheme, uri.port) else -1
    } ?: -1
    private val normalizedAdditionalHosts: Set<String> = additionalInAppHosts
        .mapNotNull { host -> host.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() } }
        .toSet()

    /**
     * Returns the negative step count to pass to `WebView.goBackOrForward`, or `null` when
     * no prior document exists and the enclosing Activity should finish.
     */
    fun resolveBackSteps(currentIndex: Int, historyUrls: List<String?>): Int? {
        if (currentIndex <= 0 || currentIndex >= historyUrls.size) {
            return null
        }

        val currentUrl = historyUrls[currentIndex] ?: return -1
        val currentUri = parseTrustedUri(currentUrl)
        val currentRawPath = currentUri?.rawPath
        if (currentUri == null || currentRawPath == null || !currentRawPath.startsWith(CONTENT_PATH_PREFIX)) {
            return -1
        }

        val currentKey = normalizedDocumentKey(currentUri) ?: return -1

        for (i in currentIndex - 1 downTo 0) {
            val candidateUrl = historyUrls[i]
            if (candidateUrl.isNullOrBlank()) {
                continue
            }
            val candidateKey = parseTrustedUri(candidateUrl)?.let(::normalizedDocumentKey)
            if (candidateKey != currentKey) {
                return i - currentIndex
            }
        }

        return null
    }

    private fun parseTrustedUri(url: String): URI? {
        val uri = parseUriOrNull(url) ?: return null
        if (uri.userInfo != null) {
            return null
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val isAllowedScheme = scheme == "https" || (allowInsecureHttpForTests && scheme == "http")
        if (!isAllowedScheme) {
            return null
        }

        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val port = effectivePort(scheme, uri.port)
        val matchesBaseHost = baseHost != null && host == baseHost && port == basePort
        val matchesAdditionalHost = host in normalizedAdditionalHosts &&
            ((scheme == "https" && port == 443) || (allowInsecureHttpForTests && scheme == "http" && port == 80))
        if (!matchesBaseHost && !matchesAdditionalHost) {
            return null
        }

        return uri
    }

    private fun normalizedDocumentKey(uri: URI): String? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val port = effectivePort(scheme, uri.port)
        val rawPath = uri.rawPath ?: ""
        val rawQuery = uri.rawQuery?.let { "?$it" } ?: ""
        return "$scheme://$host:$port$rawPath$rawQuery"
    }

    private fun parseUriOrNull(url: String): URI? {
        return try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun effectivePort(scheme: String, port: Int): Int {
        return when {
            port != -1 -> port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> -1
        }
    }

    companion object {
        private const val CONTENT_PATH_PREFIX = "/content/"
    }
}
