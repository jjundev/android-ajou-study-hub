package com.jjundev.ajoustudyhub

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {
    @Test
    fun defaultStudyHubUrlUsesPublicWorkersHost() {
        assertEquals("https://ajou-study-hub.johnshin617.workers.dev/", BuildConfig.STUDY_HUB_URL)
    }
}
