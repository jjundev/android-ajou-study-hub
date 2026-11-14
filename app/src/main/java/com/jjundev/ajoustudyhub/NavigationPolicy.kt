package com.jjundev.ajoustudyhub

import java.net.URI
import java.util.Locale

enum class NavigationAction {
    LOAD_IN_WEBVIEW,
    OPEN_EXTERNAL,
    BLOCK
}

class NavigationPolicy(
    allowedBaseUrl: String,
    additionalInAppHosts: Set<String> = emptySet(),
    internal val allowInsecureHttpForTests: Boolean = false
) {
    private val baseUri: URI? = try {
        URI(allowedBaseUrl)
    } catch (e: Exception) {
        null
    }

    private val baseHost: String = baseUri?.host?.lowercase(Locale.ROOT) ?: ""
    private val basePort: Int = effectivePort(baseUri?.scheme, baseUri?.port ?: -1)
    private val normalizedAdditionalHosts: Set<String> =
        additionalInAppHosts.map { it.lowercase(Locale.ROOT) }.toSet()

    fun isTrustedInAppUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }

        if (uri.userInfo != null) {
            return false
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        val isAllowedScheme = if (allowInsecureHttpForTests) {
            scheme == "https" || scheme == "http"
        } else {
            scheme == "https"
        }

        if (!isAllowedScheme) {
            return false
        }

        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val port = effectivePort(scheme, uri.port)

        if (host.equals(baseHost, ignoreCase = true)) {
            return port == basePort
        }

        if (normalizedAdditionalHosts.contains(host)) {
            return port == 443
        }

        return false
    }

    fun classifyTopLevel(
        url: String?,
        isNewWindow: Boolean,
        isUserGesture: Boolean
    ): NavigationAction {
        if (isNewWindow && !isUserGesture) {
            return NavigationAction.BLOCK
        }

        if (url.isNullOrBlank()) {
            return NavigationAction.BLOCK
        }

        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return NavigationAction.BLOCK
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return NavigationAction.BLOCK

        if (scheme == "mailto" || scheme == "tel") {
            return if (isUserGesture) NavigationAction.OPEN_EXTERNAL else NavigationAction.BLOCK
        }

        val isHttpOrHttps = if (allowInsecureHttpForTests) {
            scheme == "https" || scheme == "http"
        } else {
            scheme == "https"
        }

        if (!isHttpOrHttps) {
            return NavigationAction.BLOCK
        }

        if (isTrustedInAppUrl(url)) {
            return if (isNewWindow) NavigationAction.OPEN_EXTERNAL else NavigationAction.LOAD_IN_WEBVIEW
        }

        return if (!isNewWindow || isUserGesture) {
            NavigationAction.OPEN_EXTERNAL
        } else {
            NavigationAction.BLOCK
        }
    }

    private fun effectivePort(scheme: String?, port: Int): Int {
        if (port != -1) return port
        return when (scheme?.lowercase(Locale.ROOT)) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }
}
