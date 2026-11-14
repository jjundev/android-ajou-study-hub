package com.jjundev.ajoustudyhub

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

sealed interface DownloadResult {
    data class Ready(val file: File, val mimeType: String) : DownloadResult
    data object AuthRequired : DownloadResult
    data object UntrustedRedirect : DownloadResult
    data class Unsupported(val reason: String) : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

object DownloadPolicy {
    internal val HTML_MEDIA_TYPES = setOf("text/html", "application/xhtml+xml")

    fun isValidResponse(statusCode: Int, contentType: String?, fileName: String?): Boolean {
        if (statusCode != 200) {
            return false
        }
        val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        if (mediaType != null && HTML_MEDIA_TYPES.contains(mediaType)) {
            return false
        }
        return true
    }

    fun sanitizeFileName(rawName: String?, fallback: String): String {
        if (rawName.isNullOrBlank()) {
            return fallback
        }

        var name = rawName.trim().trim('"', '\'')
        name = name.replace('\\', '/')
        name = name.substringAfterLast('/')
        name = name.replace("..", "")
        name = name.filter { ch ->
            ch.code >= 32 && ch.code != 127 && ch !in "<>:\"/\\|?*"
        }.trim()

        if (name.isEmpty() || name.all { it == '.' }) {
            return fallback
        }

        return name
    }
}

class DownloadTransfer(
    private val policy: NavigationPolicy,
    private val cacheRoot: File,
    private val cookieProvider: (String) -> String?
) {
    companion object {
        private const val MAX_REDIRECT_HOPS = 5
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
    }

    fun fetchToCache(
        url: String,
        suggestedMimeType: String? = null,
        contentDisposition: String? = null
    ): DownloadResult {
        var currentUrl = url
        var hops = 0

        while (hops <= MAX_REDIRECT_HOPS) {
            if (!policy.isTrustedInAppUrl(currentUrl)) {
                return DownloadResult.UntrustedRedirect
            }

            val connection: HttpURLConnection = try {
                val parsedUri = URI(currentUrl)
                val targetUrl = parsedUri.toURL()
                (targetUrl.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    cookieProvider(currentUrl)?.takeIf { it.isNotBlank() }?.let {
                        setRequestProperty("Cookie", it)
                    }
                }
            } catch (e: Exception) {
                return DownloadResult.Failed(e.message ?: "Failed to open connection")
            }

            try {
                connection.connect()
                val statusCode = connection.responseCode

                if (statusCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()

                    if (location.isNullOrBlank()) {
                        return DownloadResult.Failed("Redirect location missing")
                    }

                    currentUrl = try {
                        URI(currentUrl).resolve(location).toString()
                    } catch (e: Exception) {
                        return DownloadResult.Failed("Invalid redirect URL: $location")
                    }

                    hops++
                    if (hops > MAX_REDIRECT_HOPS) {
                        return DownloadResult.Failed("Too many redirects")
                    }
                    continue
                }

                if (statusCode == 401 || statusCode == 403) {
                    connection.disconnect()
                    return DownloadResult.AuthRequired
                }

                if (statusCode != 200) {
                    connection.disconnect()
                    return DownloadResult.Failed("HTTP $statusCode")
                }

                val rawContentType = connection.contentType
                val mediaType = rawContentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)

                if (!DownloadPolicy.isValidResponse(statusCode, rawContentType, null)) {
                    connection.disconnect()
                    return if (mediaType != null && DownloadPolicy.HTML_MEDIA_TYPES.contains(mediaType)) {
                        DownloadResult.Unsupported("HTML response is not supported for download")
                    } else {
                        DownloadResult.Failed("Invalid response")
                    }
                }

                val headerDisposition = connection.getHeaderField("Content-Disposition")
                val nameFromDisposition = extractFileNameFromContentDisposition(headerDisposition ?: contentDisposition)
                val nameFromUrl = try {
                    URI(currentUrl).path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let {
                        URLDecoder.decode(it, "UTF-8")
                    }
                } catch (_: Exception) {
                    null
                }

                val fallback = if (mediaType == "application/pdf" ||
                    suggestedMimeType == "application/pdf" ||
                    currentUrl.contains(".pdf", ignoreCase = true)
                ) {
                    "download.pdf"
                } else {
                    "download.bin"
                }

                val sanitizedName = DownloadPolicy.sanitizeFileName(nameFromDisposition ?: nameFromUrl, fallback)
                val resolvedMime = when {
                    !mediaType.isNullOrBlank() && mediaType != "application/octet-stream" -> mediaType
                    !suggestedMimeType.isNullOrBlank() -> suggestedMimeType
                    sanitizedName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    else -> "application/octet-stream"
                }

                val downloadsDir = File(cacheRoot, "downloads")
                if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                    connection.disconnect()
                    return DownloadResult.Failed("Failed to create downloads directory")
                }

                val tempFile = try {
                    File.createTempFile("dl_", ".tmp", downloadsDir)
                } catch (e: IOException) {
                    connection.disconnect()
                    return DownloadResult.Failed("Failed to create temporary file")
                }

                try {
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val targetFile = File(downloadsDir, sanitizedName)
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }

                    val renamed = tempFile.renameTo(targetFile)
                    val finalFile = if (renamed) {
                        targetFile
                    } else {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        targetFile
                    }

                    return DownloadResult.Ready(finalFile, resolvedMime)
                } catch (e: Exception) {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    return DownloadResult.Failed(e.message ?: "Failed to save file")
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                connection.disconnect()
                return DownloadResult.Failed(e.message ?: "Download connection error")
            }
        }

        return DownloadResult.Failed("Too many redirects")
    }

    internal fun extractFileNameFromContentDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null

        val extRegex = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
        val extMatch = extRegex.find(disposition)
        if (extMatch != null) {
            val charset = extMatch.groupValues[1].ifBlank { "UTF-8" }
            val value = extMatch.groupValues[2].trim().trim('"', '\'')
            try {
                return URLDecoder.decode(value, charset)
            } catch (_: Exception) {
                try {
                    return URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {}
            }
        }

        val quotedRegex = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        val quotedMatch = quotedRegex.find(disposition)
        if (quotedMatch != null) {
            return quotedMatch.groupValues[1].trim()
        }

        val unquotedRegex = Regex("""filename\s*=\s*([^; ]+)""", RegexOption.IGNORE_CASE)
        val unquotedMatch = unquotedRegex.find(disposition)
        if (unquotedMatch != null) {
            return unquotedMatch.groupValues[1].trim().trim('"', '\'')
        }

        return null
    }
}
