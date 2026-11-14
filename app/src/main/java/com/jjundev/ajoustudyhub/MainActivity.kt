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
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.android.material.snackbar.Snackbar
import com.jjundev.ajoustudyhub.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    internal var navigationPolicy: NavigationPolicy = NavigationPolicy(BuildConfig.STUDY_HUB_URL)
    internal var downloadExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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

        binding.webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            handleDownload(url, contentDisposition, mimeType)
        }
    }

    private fun handleDownload(url: String?, contentDisposition: String?, mimeType: String?) {
        if (url.isNullOrBlank()) {
            return
        }

        if (!navigationPolicy.isTrustedInAppUrl(url)) {
            openExternally(url)
            return
        }

        val transfer = DownloadTransfer(
            policy = navigationPolicy,
            cacheRoot = cacheDir,
            cookieProvider = { targetUrl ->
                CookieManager.getInstance().getCookie(targetUrl)
            }
        )

        downloadExecutor.execute {
            val result = transfer.fetchToCache(url, mimeType, contentDisposition)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                handleDownloadResult(result)
            }
        }
    }

    private fun handleDownloadResult(result: DownloadResult) {
        when (result) {
            is DownloadResult.Ready -> {
                try {
                    val uri = FileProvider.getUriForFile(
                        this,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        result.file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, result.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    showDownloadSnackbar(R.string.external_app_unavailable)
                } catch (e: Exception) {
                    showDownloadSnackbar(R.string.download_failed)
                }
            }
            is DownloadResult.AuthRequired -> {
                showDownloadSnackbar(R.string.download_auth_required)
            }
            is DownloadResult.UntrustedRedirect -> {
                showDownloadSnackbar(R.string.download_untrusted_redirect)
            }
            is DownloadResult.Unsupported -> {
                showDownloadSnackbar(R.string.download_unsupported)
            }
            is DownloadResult.Failed -> {
                showDownloadSnackbar(R.string.download_failed)
            }
        }
    }

    private fun showDownloadSnackbar(stringResId: Int) {
        Snackbar.make(binding.rootContainer, stringResId, Snackbar.LENGTH_SHORT).show()
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
        downloadExecutor.shutdown()
        binding.webView.destroy()
        super.onDestroy()
    }
}
