package com.gvam.boosterevidence

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    /*
     * Se activa cuando la encuesta llega a /gracias.
     *
     * Cuando el código se valida desde /staff, la web vuelve
     * automáticamente a la página inicial. En ese momento cerramos
     * la Activity para regresar al launcher.
     */
    private var reachedThankYouPage = false

    private var closingEvidence = false

    companion object {

        private const val TAG = "BoosterEvidence"

        private const val STATE_REACHED_THANK_YOU =
            "state_reached_thank_you"

        const val START_URL =
            "https://pre.evidence.gvam.es/"

        private const val TRUSTED_HOST =
            "pre.evidence.gvam.es"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reachedThankYouPage =
            savedInstanceState?.getBoolean(
                STATE_REACHED_THANK_YOU,
                false
            ) ?: false

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        webView = WebView(this)

        setContentView(webView)

        hideSystemBars()
        configureWebView()
        configureBackButton()

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        } else {
            val restoredState =
                webView.restoreState(savedInstanceState)

            if (restoredState == null) {
                webView.loadUrl(START_URL)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            mediaPlaybackRequiresUserGesture = false

            allowFileAccess = false
            allowContentAccess = false

            cacheMode = WebSettings.LOAD_DEFAULT

            useWideViewPort = true
            loadWithOverviewMode = true

            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)

            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webChromeClient =
            object : WebChromeClient() {

                /*
                 * Respaldo por si el frontend utiliza window.close().
                 */
                override fun onCloseWindow(window: WebView) {
                    closeEvidence(
                        source = "WebChromeClient.onCloseWindow"
                    )
                }
            }

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    /*
                     * No interferimos con iframes o recursos internos.
                     */
                    if (!request.isForMainFrame) {
                        return false
                    }

                    val uri = request.url

                    /*
                     * La navegación principal solamente puede permanecer
                     * dentro del dominio autorizado de Evidence.
                     */
                    if (!isTrustedEvidenceUri(uri)) {
                        Log.w(
                            TAG,
                            "Navegación bloqueada: $uri"
                        )

                        return true
                    }

                    return false
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String?,
                    favicon: Bitmap?
                ) {
                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    hideSystemBars()

                    processEvidenceUrl(
                        url = url,
                        source = "onPageStarted"
                    )
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String?
                ) {
                    super.onPageFinished(
                        view,
                        url
                    )

                    hideSystemBars()

                    processEvidenceUrl(
                        url = url,
                        source = "onPageFinished"
                    )
                }

                /*
                 * También detecta cambios de ruta realizados mediante
                 * history.pushState() o history.replaceState().
                 */
                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean
                ) {
                    super.doUpdateVisitedHistory(
                        view,
                        url,
                        isReload
                    )

                    processEvidenceUrl(
                        url = url,
                        source = "doUpdateVisitedHistory"
                    )
                }
            }
    }

    private fun processEvidenceUrl(
        url: String?,
        source: String
    ) {
        if (
            closingEvidence ||
            !isTrustedEvidenceUrl(url)
        ) {
            return
        }

        when {
            isThankYouUrl(url) -> {

                if (!reachedThankYouPage) {
                    reachedThankYouPage = true

                    Log.i(
                        TAG,
                        "Pantalla /gracias detectada: $url"
                    )
                }
            }

            reachedThankYouPage &&
                isRootEvidenceUrl(url) -> {

                Log.i(
                    TAG,
                    "Regreso a inicio después de /gracias. Fuente: $source"
                )

                closeEvidence(
                    source =
                        "código validado desde staff"
                )
            }
        }
    }

    private fun configureBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        closeEvidence(
                            source = "botón atrás"
                        )
                    }
                }
            }
        )
    }

    private fun isTrustedEvidenceUrl(
        url: String?
    ): Boolean {

        if (url.isNullOrBlank()) {
            return false
        }

        return runCatching {
            isTrustedEvidenceUri(
                Uri.parse(url)
            )
        }.getOrDefault(false)
    }

    private fun isTrustedEvidenceUri(
        uri: Uri
    ): Boolean {

        val scheme =
            uri.scheme
                ?.lowercase()
                ?: return false

        val host =
            uri.host
                ?.lowercase()
                ?: return false

        return scheme == "https" &&
            (
                host == TRUSTED_HOST ||
                    host.endsWith(
                        ".$TRUSTED_HOST"
                    )
            )
    }

    private fun isThankYouUrl(
        url: String?
    ): Boolean {

        if (!isTrustedEvidenceUrl(url)) {
            return false
        }

        val path =
            runCatching {
                Uri.parse(url)
                    .path
                    .orEmpty()
            }.getOrDefault("")

        return path == "/gracias" ||
            path.startsWith("/gracias/")
    }

    private fun isRootEvidenceUrl(
        url: String?
    ): Boolean {

        if (!isTrustedEvidenceUrl(url)) {
            return false
        }

        val uri =
            runCatching {
                Uri.parse(url)
            }.getOrNull()
                ?: return false

        val path =
            uri.path
                .orEmpty()

        /*
         * Evitamos considerar raíz una dirección que tenga parámetros.
         */
        return (
            path.isBlank() ||
                path == "/"
            ) &&
            uri.query.isNullOrBlank()
    }

    private fun closeEvidence(
        source: String
    ) {

        if (
            closingEvidence ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        closingEvidence = true

        val currentUrl =
            if (::webView.isInitialized) {
                webView.url
            } else {
                null
            }

        Log.i(
            TAG,
            "Cerrando Evidence. Origen: $source. URL: $currentUrl"
        )

        runOnUiThread {

            if (::webView.isInitialized) {
                webView.stopLoading()
            }

            /*
             * Cierra Evidence y muestra nuevamente el launcher
             * que se encontraba debajo.
             */
            finish()

            @Suppress("DEPRECATION")
            overridePendingTransition(
                0,
                0
            )
        }
    }

    private fun hideSystemBars() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).apply {

            hide(
                WindowInsetsCompat.Type.systemBars()
            )

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(
            hasFocus
        )

        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        outState.putBoolean(
            STATE_REACHED_THANK_YOU,
            reachedThankYouPage
        )

        webView.saveState(outState)

        super.onSaveInstanceState(
            outState
        )
    }

    override fun onDestroy() {

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }

        super.onDestroy()
    }
}