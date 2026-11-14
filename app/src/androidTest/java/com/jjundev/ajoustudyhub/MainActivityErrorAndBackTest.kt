package com.jjundev.ajoustudyhub

import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityErrorAndBackTest {

    @Before
    fun wakeAndDismissKeyguard() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("input keyevent 224") // WAKEUP
        uiAutomation.executeShellCommand("wm dismiss-keyguard")
    }

    @Test
    fun subresourceErrorDoesNotShowErrorOverlay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.webView)
                webView.stopLoading()
                activity.findViewById<View>(R.id.errorContainer).visibility = View.GONE
                activity.handleFrameLoadError(isForMainFrame = false, description = "HTTP 400")
            }
            onView(withId(R.id.errorContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun mainFrameErrorShowsRetryOverlayAndRetryHidesIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.webView)
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://ajou-study-hub.johnshin617.workers.dev/",
                    "<html><body>Main Page</body></html>",
                    "text/html",
                    "UTF-8",
                    "https://ajou-study-hub.johnshin617.workers.dev/"
                )
            }

            var pageLoaded = false
            val deadline = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline && !pageLoaded) {
                scenario.onActivity { activity ->
                    val webView = activity.findViewById<WebView>(R.id.webView)
                    webView.evaluateJavascript("document.body ? document.body.innerText : ''") { text ->
                        if (text?.contains("Main Page") == true) {
                            pageLoaded = true
                        }
                    }
                }
                if (!pageLoaded) {
                    Thread.sleep(50)
                }
            }
            assertTrue("Initial page should be loaded before error injection", pageLoaded)

            scenario.onActivity { activity ->
                activity.handleFrameLoadError(isForMainFrame = true, description = "ERR_NAME_NOT_RESOLVED")
            }
            onView(withId(R.id.errorContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.retryButton)).check(matches(isDisplayed()))
            onView(withId(R.id.retryButton)).perform(click())
            onView(withId(R.id.errorContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun backPressNavigatesWebHistoryBeforeFinishingActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.webView)
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://ajou-study-hub.johnshin617.workers.dev/page1",
                    "<html><body><h1>Page 1</h1></body></html>",
                    "text/html",
                    "UTF-8",
                    "https://ajou-study-hub.johnshin617.workers.dev/page1"
                )
            }

            var page1Loaded = false
            val deadline1 = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline1 && !page1Loaded) {
                scenario.onActivity { activity ->
                    val webView = activity.findViewById<WebView>(R.id.webView)
                    webView.evaluateJavascript("document.body ? document.body.innerText : ''") { text ->
                        if (text?.contains("Page 1") == true) {
                            page1Loaded = true
                        }
                    }
                }
                if (!page1Loaded) {
                    Thread.sleep(50)
                }
            }
            assertTrue("Page 1 should be loaded", page1Loaded)

            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.webView)
                webView.clearHistory()
                webView.loadDataWithBaseURL(
                    "https://ajou-study-hub.johnshin617.workers.dev/page2",
                    "<html><body><h1>Page 2</h1></body></html>",
                    "text/html",
                    "UTF-8",
                    "https://ajou-study-hub.johnshin617.workers.dev/page2"
                )
            }

            var canGoBack = false
            val deadline2 = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline2 && !canGoBack) {
                scenario.onActivity { activity ->
                    val webView = activity.findViewById<WebView>(R.id.webView)
                    canGoBack = webView.canGoBack()
                }
                if (!canGoBack) {
                    Thread.sleep(50)
                }
            }
            assertTrue("WebView history should have at least 2 entries", canGoBack)

            // First back press: should navigate back in history and not finish activity
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            scenario.onActivity { activity ->
                assertFalse("Activity should not be finishing on first back press", activity.isFinishing)
            }

            var backNavigated = false
            val deadline3 = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline3 && !backNavigated) {
                scenario.onActivity { activity ->
                    val webView = activity.findViewById<WebView>(R.id.webView)
                    if (!webView.canGoBack()) {
                        backNavigated = true
                    }
                }
                if (!backNavigated) {
                    Thread.sleep(50)
                }
            }
            assertTrue("WebView should have navigated back to initial history entry", backNavigated)

            // Second back press: history is exhausted, activity should finish
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            var isFinishing = false
            val deadline4 = SystemClock.uptimeMillis() + 5000
            while (SystemClock.uptimeMillis() < deadline4 && !isFinishing) {
                scenario.onActivity { activity ->
                    isFinishing = activity.isFinishing
                }
                if (!isFinishing) {
                    Thread.sleep(50)
                }
            }
            assertTrue("Activity should finish when back pressed with no web history", isFinishing)
        }
    }
}
