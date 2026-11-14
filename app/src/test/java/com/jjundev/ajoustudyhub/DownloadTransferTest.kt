package com.jjundev.ajoustudyhub

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DownloadTransferTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun trustedHttpsPdf200SavesCleanFileAndSendsCookie() {
        val receivedCookie = AtomicReference<String?>()
        val pdfContent = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".toByteArray(Charsets.UTF_8)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sample.pdf", HttpHandler { exchange ->
                receivedCookie.set(exchange.requestHeaders.getFirst("Cookie"))
                exchange.responseHeaders.set("Content-Type", "application/pdf")
                exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"sample.pdf\"")
                exchange.sendResponseHeaders(200, pdfContent.size.toLong())
                exchange.responseBody.use { it.write(pdfContent) }
            })
            start()
        }

        try {
            val port = server.address.port
            val baseUrl = "http://127.0.0.1:$port/"
            val policy = NavigationPolicy(baseUrl, allowInsecureHttpForTests = true)
            val transfer = DownloadTransfer(
                policy = policy,
                cacheRoot = tempFolder.root,
                cookieProvider = { "session_token=test_cookie_value" }
            )

            val result = transfer.fetchToCache("http://127.0.0.1:$port/sample.pdf")

            assertTrue("Expected Ready but was $result", result is DownloadResult.Ready)
            val ready = result as DownloadResult.Ready
            assertEquals("application/pdf", ready.mimeType)
            assertEquals("sample.pdf", ready.file.name)
            assertTrue(ready.file.exists())
            assertTrue(ready.file.readBytes().contentEquals(pdfContent))
            assertEquals("session_token=test_cookie_value", receivedCookie.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun redirectToUntrustedHostIsBlockedWithoutForwardingCookie() {
        val serverBHitCount = AtomicInteger(0)
        val serverBReceivedCookie = AtomicReference<String?>()

        val serverB = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/untrusted.pdf", HttpHandler { exchange ->
                serverBHitCount.incrementAndGet()
                serverBReceivedCookie.set(exchange.requestHeaders.getFirst("Cookie"))
                val body = "secret pdf on server B".toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/pdf")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            })
            start()
        }

        val portB = serverB.address.port
        val serverA = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/redirect.pdf", HttpHandler { exchange ->
                exchange.responseHeaders.set("Location", "http://127.0.0.1:$portB/untrusted.pdf")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            })
            start()
        }

        try {
            val portA = serverA.address.port
            val policy = NavigationPolicy("http://127.0.0.1:$portA/", allowInsecureHttpForTests = true)
            val transfer = DownloadTransfer(
                policy = policy,
                cacheRoot = tempFolder.root,
                cookieProvider = { "confidential_auth_cookie=secret" }
            )

            val result = transfer.fetchToCache("http://127.0.0.1:$portA/redirect.pdf")

            assertEquals(DownloadResult.UntrustedRedirect, result)
            assertEquals("Server B must not receive any requests", 0, serverBHitCount.get())
            assertNull("Server B must not receive any cookies", serverBReceivedCookie.get())
        } finally {
            serverA.stop(0)
            serverB.stop(0)
        }
    }

    @Test
    fun http401Or403ReturnsAuthRequired() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/unauthorized.pdf", HttpHandler { exchange ->
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
            })
            createContext("/forbidden.pdf", HttpHandler { exchange ->
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            })
            start()
        }

        try {
            val port = server.address.port
            val policy = NavigationPolicy("http://127.0.0.1:$port/", allowInsecureHttpForTests = true)
            val transfer = DownloadTransfer(
                policy = policy,
                cacheRoot = tempFolder.root,
                cookieProvider = { null }
            )

            val result401 = transfer.fetchToCache("http://127.0.0.1:$port/unauthorized.pdf")
            assertEquals(DownloadResult.AuthRequired, result401)

            val result403 = transfer.fetchToCache("http://127.0.0.1:$port/forbidden.pdf")
            assertEquals(DownloadResult.AuthRequired, result403)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun htmlLoginPageDisguisedAsPdfIsRejected() {
        val htmlContent = "<!DOCTYPE html><html><body>Login Required</body></html>".toByteArray(Charsets.UTF_8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/disguised.pdf", HttpHandler { exchange ->
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, htmlContent.size.toLong())
                exchange.responseBody.use { it.write(htmlContent) }
            })
            start()
        }

        try {
            val port = server.address.port
            val policy = NavigationPolicy("http://127.0.0.1:$port/", allowInsecureHttpForTests = true)
            val transfer = DownloadTransfer(
                policy = policy,
                cacheRoot = tempFolder.root,
                cookieProvider = { null }
            )

            val result = transfer.fetchToCache("http://127.0.0.1:$port/disguised.pdf")

            assertTrue("Expected Unsupported but was $result", result is DownloadResult.Unsupported)
            val downloadsDir = File(tempFolder.root, "downloads")
            assertTrue(
                "No file should be created in downloads dir",
                !downloadsDir.exists() || (downloadsDir.listFiles()?.isEmpty() == true)
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun pathTraversalFileNameIsSanitized() {
        val content = "%PDF-1.4 traversal test".toByteArray(Charsets.UTF_8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/path-traversal.pdf", HttpHandler { exchange ->
                exchange.responseHeaders.set("Content-Type", "application/pdf")
                exchange.responseHeaders.set("Content-Disposition", "attachment; filename=\"../../secret.pdf\"")
                exchange.sendResponseHeaders(200, content.size.toLong())
                exchange.responseBody.use { it.write(content) }
            })
            start()
        }

        try {
            val port = server.address.port
            val policy = NavigationPolicy("http://127.0.0.1:$port/", allowInsecureHttpForTests = true)
            val transfer = DownloadTransfer(
                policy = policy,
                cacheRoot = tempFolder.root,
                cookieProvider = { null }
            )

            val result = transfer.fetchToCache("http://127.0.0.1:$port/path-traversal.pdf")

            assertTrue("Expected Ready but was $result", result is DownloadResult.Ready)
            val ready = result as DownloadResult.Ready
            assertEquals("secret.pdf", ready.file.name)
            val downloadsDir = File(tempFolder.root, "downloads")
            assertEquals(downloadsDir.canonicalFile, ready.file.canonicalFile.parentFile)
            assertTrue(ready.file.exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadPolicyUnitValidation() {
        assertTrue(DownloadPolicy.isValidResponse(200, "application/pdf", "sample.pdf"))
        assertTrue(DownloadPolicy.isValidResponse(200, "application/octet-stream", "sample.pdf"))
        assertTrue(DownloadPolicy.isValidResponse(200, null, "sample.pdf"))
        assertEquals(false, DownloadPolicy.isValidResponse(200, "text/html; charset=utf-8", "sample.pdf"))
        assertEquals(false, DownloadPolicy.isValidResponse(200, "application/xhtml+xml", "sample.pdf"))
        assertEquals(false, DownloadPolicy.isValidResponse(404, "application/pdf", "sample.pdf"))

        assertEquals("secret.pdf", DownloadPolicy.sanitizeFileName("../../secret.pdf", "fallback.pdf"))
        assertEquals("secret.pdf", DownloadPolicy.sanitizeFileName("..\\..\\secret.pdf", "fallback.pdf"))
        assertEquals("fallback.pdf", DownloadPolicy.sanitizeFileName(null, "fallback.pdf"))
        assertEquals("fallback.pdf", DownloadPolicy.sanitizeFileName("", "fallback.pdf"))
        assertEquals("fallback.pdf", DownloadPolicy.sanitizeFileName("...", "fallback.pdf"))
        assertEquals("자료.pdf", DownloadPolicy.sanitizeFileName("자료.pdf", "fallback.pdf"))
    }
}
