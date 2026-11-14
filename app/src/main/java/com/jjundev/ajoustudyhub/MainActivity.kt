package com.jjundev.ajoustudyhub

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowCompat
import com.google.android.material.snackbar.Snackbar
import com.jjundev.ajoustudyhub.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    internal var navigationPolicy: NavigationPolicy = NavigationPolicy(BuildConfig.STUDY_HUB_URL)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureWebView()

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            binding.webView.loadUrl(BuildConfig.STUDY_HUB_URL)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
        }

        CookieManager.getInstance().setAcceptCookie(true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) {
                    return false
                }
                val url = request.url.toString()
                val action = navigationPolicy.classifyTopLevel(
                    url = url,
                    isNewWindow = false,
                    isUserGesture = request.hasGesture()
                )
                return when (action) {
                    NavigationAction.LOAD_IN_WEBVIEW -> false
                    NavigationAction.OPEN_EXTERNAL -> {
                        openExternally(url)
                        true
                    }
                    NavigationAction.BLOCK -> true
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture || resultMsg == null) {
                    return false
                }

                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val tempWebView = WebView(this@MainActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        tempView: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        handleNewWindowUrl(url)
                        tempView.post { tempView.destroy() }
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(tempView: WebView, url: String): Boolean {
                        handleNewWindowUrl(url)
                        tempView.post { tempView.destroy() }
                        return true
                    }
                }

                transport.webView = tempWebView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun handleNewWindowUrl(url: String) {
        when (navigationPolicy.classifyTopLevel(url, isNewWindow = true, isUserGesture = true)) {
            NavigationAction.OPEN_EXTERNAL -> openExternally(url)
            NavigationAction.LOAD_IN_WEBVIEW -> binding.webView.loadUrl(url)
            NavigationAction.BLOCK -> Unit
        }
    }

    internal fun openExternally(url: String) {
        val parsedUri = Uri.parse(url)
        val scheme = parsedUri.scheme?.lowercase(Locale.ROOT)

        if (scheme == "https" || scheme == "http") {
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.launchUrl(this, parsedUri)
                return
            } catch (e: ActivityNotFoundException) {
                showExternalOpenFailureSnackbar()
                return
            } catch (e: Exception) {
                // Fallback to standard ACTION_VIEW below
            }
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, parsedUri)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showExternalOpenFailureSnackbar()
        } catch (e: Exception) {
            showExternalOpenFailureSnackbar()
        }
    }

    private fun showExternalOpenFailureSnackbar() {
        Snackbar.make(
            binding.rootContainer,
            R.string.external_app_unavailable,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
