package com.jjundev.ajoustudyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {

    private val defaultPolicy = NavigationPolicy("https://ajou-study-hub.johnshin617.workers.dev/")

    @Test
    fun sameOriginDocumentAndHashRouteLoadInWebView() {
        val url = "https://ajou-study-hub.johnshin617.workers.dev/#/read/doc-1?anchor=sec2"
        assertTrue(defaultPolicy.isTrustedInAppUrl(url))
        val action = defaultPolicy.classifyTopLevel(
            url = url,
            isNewWindow = false,
            isUserGesture = false
        )
        assertEquals(NavigationAction.LOAD_IN_WEBVIEW, action)

        val actionWithGesture = defaultPolicy.classifyTopLevel(
            url = url,
            isNewWindow = false,
            isUserGesture = true
        )
        assertEquals(NavigationAction.LOAD_IN_WEBVIEW, actionWithGesture)
    }

    @Test
    fun sameOriginNewTabWithUserGestureOpensExternal() {
        val url = "https://ajou-study-hub.johnshin617.workers.dev/docs/intro"
        assertTrue(defaultPolicy.isTrustedInAppUrl(url))
        val action = defaultPolicy.classifyTopLevel(
            url = url,
            isNewWindow = true,
            isUserGesture = true
        )
        assertEquals(NavigationAction.OPEN_EXTERNAL, action)
    }

    @Test
    fun externalHttpsWithUserGestureOpensExternal() {
        val url = "https://developer.android.com/"
        assertFalse(defaultPolicy.isTrustedInAppUrl(url))
        val action = defaultPolicy.classifyTopLevel(
            url = url,
            isNewWindow = false,
            isUserGesture = true
        )
        assertEquals(NavigationAction.OPEN_EXTERNAL, action)
    }

    @Test
    fun newWindowWithoutUserGestureIsBlocked() {
        val trustedUrl = "https://ajou-study-hub.johnshin617.workers.dev/"
        val actionTrusted = defaultPolicy.classifyTopLevel(
            url = trustedUrl,
            isNewWindow = true,
            isUserGesture = false
        )
        assertEquals(NavigationAction.BLOCK, actionTrusted)

        val externalUrl = "https://developer.android.com/"
        val actionExternal = defaultPolicy.classifyTopLevel(
            url = externalUrl,
            isNewWindow = true,
            isUserGesture = false
        )
        assertEquals(NavigationAction.BLOCK, actionExternal)
    }

    @Test
    fun substringAndUserInfoSpoofedHostsDoNotLoadInWebView() {
        val subDomainSpoof = "https://ajou-study-hub.johnshin617.workers.dev.evil.com/"
        assertFalse(defaultPolicy.isTrustedInAppUrl(subDomainSpoof))
        assertEquals(
            NavigationAction.OPEN_EXTERNAL,
            defaultPolicy.classifyTopLevel(subDomainSpoof, isNewWindow = false, isUserGesture = true)
        )

        val userInfoSpoof = "https://ajou-study-hub.johnshin617.workers.dev@evil.com/"
        assertFalse(defaultPolicy.isTrustedInAppUrl(userInfoSpoof))
        assertEquals(
            NavigationAction.OPEN_EXTERNAL,
            defaultPolicy.classifyTopLevel(userInfoSpoof, isNewWindow = false, isUserGesture = true)
        )
    }

    @Test
    fun unsafeSchemesAndCleartextHttpAreBlocked() {
        val cleartextHttp = "http://ajou-study-hub.johnshin617.workers.dev/"
        assertFalse(defaultPolicy.isTrustedInAppUrl(cleartextHttp))
        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel(cleartextHttp, isNewWindow = false, isUserGesture = true)
        )

        val unsafeUrls = listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://com.example.provider/data",
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
            "data:text/html,<b>hello</b>"
        )

        for (unsafeUrl in unsafeUrls) {
            assertFalse(defaultPolicy.isTrustedInAppUrl(unsafeUrl))
            assertEquals(
                NavigationAction.BLOCK,
                defaultPolicy.classifyTopLevel(unsafeUrl, isNewWindow = false, isUserGesture = true)
            )
        }
    }

    @Test
    fun mailtoAndTelRequireUserGesture() {
        val mailto = "mailto:test@ajou.ac.kr"
        assertFalse(defaultPolicy.isTrustedInAppUrl(mailto))
        assertEquals(
            NavigationAction.OPEN_EXTERNAL,
            defaultPolicy.classifyTopLevel(mailto, isNewWindow = false, isUserGesture = true)
        )
        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel(mailto, isNewWindow = false, isUserGesture = false)
        )

        val tel = "tel:01012345678"
        assertFalse(defaultPolicy.isTrustedInAppUrl(tel))
        assertEquals(
            NavigationAction.OPEN_EXTERNAL,
            defaultPolicy.classifyTopLevel(tel, isNewWindow = false, isUserGesture = true)
        )
        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel(tel, isNewWindow = false, isUserGesture = false)
        )
    }

    @Test
    fun insecureHttpAndPortMatchingRespectsTestFlag() {
        val testPolicy = NavigationPolicy(
            allowedBaseUrl = "http://127.0.0.1:12345/",
            allowInsecureHttpForTests = true
        )
        assertTrue(testPolicy.isTrustedInAppUrl("http://127.0.0.1:12345/test"))
        assertFalse(testPolicy.isTrustedInAppUrl("http://127.0.0.1:54321/test"))
        assertFalse(testPolicy.isTrustedInAppUrl("http://127.0.0.1/test"))

        assertEquals(
            NavigationAction.LOAD_IN_WEBVIEW,
            testPolicy.classifyTopLevel("http://127.0.0.1:12345/test", isNewWindow = false, isUserGesture = false)
        )
    }

    @Test
    fun additionalInAppHostsEnforcesPort443() {
        val policyWithAdditional = NavigationPolicy(
            allowedBaseUrl = "https://ajou-study-hub.johnshin617.workers.dev/",
            additionalInAppHosts = setOf("docs.ajou.ac.kr")
        )
        assertTrue(policyWithAdditional.isTrustedInAppUrl("https://docs.ajou.ac.kr/resource"))
        assertFalse(policyWithAdditional.isTrustedInAppUrl("https://docs.ajou.ac.kr:8443/resource"))
        assertFalse(policyWithAdditional.isTrustedInAppUrl("http://docs.ajou.ac.kr/resource"))
    }

    @Test
    fun nullAndMalformedUrlsAreBlocked() {
        assertFalse(defaultPolicy.isTrustedInAppUrl(null))
        assertFalse(defaultPolicy.isTrustedInAppUrl(""))
        assertFalse(defaultPolicy.isTrustedInAppUrl("   "))
        assertFalse(defaultPolicy.isTrustedInAppUrl("not a url"))

        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel(null, isNewWindow = false, isUserGesture = true)
        )
        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel("", isNewWindow = false, isUserGesture = true)
        )
        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel("   ", isNewWindow = false, isUserGesture = true)
        )
        assertEquals(
            NavigationAction.BLOCK,
            defaultPolicy.classifyTopLevel("://bad-uri", isNewWindow = false, isUserGesture = true)
        )
    }
}
