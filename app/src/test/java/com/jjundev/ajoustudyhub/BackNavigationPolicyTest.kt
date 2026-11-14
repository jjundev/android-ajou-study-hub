package com.jjundev.ajoustudyhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackNavigationPolicyTest {

    private val policy = BackNavigationPolicy("https://ajou-study-hub.johnshin617.workers.dev/")

    @Test
    fun skipsSameContentDocumentHashesAndTrailingPathnameRepush() {
        val historyUrls = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s1",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s2",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html"
        )

        assertEquals(-4, policy.resolveBackSteps(4, historyUrls))
    }

    @Test
    fun crossDocumentContentNavigationReturnsToPreviousContentFirstThenCatalog() {
        val historyUrls = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/",
            "https://ajou-study-hub.johnshin617.workers.dev/content/1113.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/1113.html#s2",
            "https://ajou-study-hub.johnshin617.workers.dev/content/1111.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/1111.html#t1",
            "https://ajou-study-hub.johnshin617.workers.dev/content/1111.html#t2"
        )

        assertEquals(-3, policy.resolveBackSteps(5, historyUrls))
        assertEquals(-2, policy.resolveBackSteps(2, historyUrls))
    }

    @Test
    fun contentDocumentWithoutPriorDocumentReturnsNullToFinishActivity() {
        val historyUrls = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s1",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s2"
        )

        assertNull(policy.resolveBackSteps(2, historyUrls))
    }

    @Test
    fun nonContentRoutesAndDifferentQueriesStepBackOneByOne() {
        val spaHistory = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/#/catalog",
            "https://ajou-study-hub.johnshin617.workers.dev/#/read/doc-1"
        )
        assertEquals(-1, policy.resolveBackSteps(1, spaHistory))

        val queryHistory = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html?v=1",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html?v=2#s1"
        )
        assertEquals(-1, policy.resolveBackSteps(1, queryHistory))
    }

    @Test
    fun percentEncodedPathsAndMalformedOrOutOfBoundsInputsHandledSafely() {
        val encodedHistory = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/",
            "https://ajou-study-hub.johnshin617.workers.dev/content/circuits-2/%EB%B3%B5%EC%8A%B5%EB%85%B8%ED%8A%B8/1113/%EC%A0%95%EB%A6%AC%EB%85%B8%ED%8A%B8.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/circuits-2/%EB%B3%B5%EC%8A%B5%EB%85%B8%ED%8A%B8/1113/%EC%A0%95%EB%A6%AC%EB%85%B8%ED%8A%B8.html#s1"
        )
        assertEquals(-2, policy.resolveBackSteps(2, encodedHistory))

        assertNull(policy.resolveBackSteps(0, encodedHistory))
        assertNull(policy.resolveBackSteps(-1, encodedHistory))
        assertNull(policy.resolveBackSteps(encodedHistory.size, encodedHistory))
        assertNull(policy.resolveBackSteps(0, emptyList()))

        val malformedLeadingHistory = listOf(
            "not a valid uri://[",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s1"
        )
        assertEquals(-2, policy.resolveBackSteps(2, malformedLeadingHistory))

        val nullGapHistory = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/",
            null,
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html",
            "https://ajou-study-hub.johnshin617.workers.dev/content/a.html#s1"
        )
        assertEquals(-3, policy.resolveBackSteps(3, nullGapHistory))

        val malformedCurrentHistory = listOf(
            "https://ajou-study-hub.johnshin617.workers.dev/",
            "not a valid uri://["
        )
        assertEquals(-1, policy.resolveBackSteps(1, malformedCurrentHistory))
    }
}
