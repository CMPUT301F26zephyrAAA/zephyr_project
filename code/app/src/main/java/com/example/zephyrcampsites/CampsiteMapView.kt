package com.example.zephyrcampsites

// Displays the existing OpenStreetMap embed within the campsite details screen.

import android.annotation.SuppressLint
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.delay
import org.json.JSONArray

private const val TILE_LOAD_TIMEOUT_MS = 8_000L
private const val OSM_EMBED_ORIGIN = "https://www.openstreetmap.org"
private val tilePath = Regex("""/\d+/\d+/\d+\.png""")

// AI Citation [AI-48]
// OpenAI, Codex, November 27, 2026. Added a request fixture hook before the first WebView load.
// See Bhav Saluja's entry in the team's AI Documentation Google Doc.
/**
 * Displays the saved campsite location on an interactive map. Map tiles require a network connection.
 *
 * @param interceptResource Optional test fixture that serves WebView responses before navigation.
 */
class CampsiteMapView @VisibleForTesting internal constructor(
    private val interceptResource: ((WebResourceRequest) -> WebResourceResponse?)?
) {
    constructor() : this(null)

    // AI Citation [AI-45]
    // OpenAI, Codex, November 27, 2026. Added retry and distinguished embed failures from tile errors.
    // See Bhav Saluja's entry in the team's AI Documentation Google Doc.
    /**
     * Displays a map marker, or a message when coordinates are missing or loading fails.
     *
     * @param location Saved campsite coordinates.
     * @param campsiteName Name used to describe the map for accessibility.
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    @Composable
    fun MapView(location: GeoPoint, campsiteName: String) {
        val url = CampsiteMapLocation.embedUrl(location.latitude, location.longitude)
        var attempt by remember(url) { mutableIntStateOf(0) }
        var failed by remember(url, attempt) { mutableStateOf(false) }
        var browser by remember(url, attempt) { mutableStateOf<WebView?>(null) }
        var failedTiles by remember(url, attempt) { mutableStateOf(emptySet<String>()) }
        var tileFailureCount by remember(url, attempt) { mutableIntStateOf(0) }
        val description = stringResource(R.string.campsite_map_description, campsiteName)

        // AI Citation [AI-46]
        // OpenAI, Codex, November 27, 2026. Added detection of a map with no successful tiles.
        // See Bhav Saluja's entry in the team's AI Documentation Google Doc.
        // AI Citation [AI-50]
        // OpenAI, Codex, November 27, 2026. Rechecked the fallback after each batch of tile failures.
        // See Bhav Saluja's entry in the team's AI Documentation Google Doc.
        LaunchedEffect(browser, tileFailureCount, failed) {
            val view = browser
            val checkedFailureCount = tileFailureCount
            if (view != null && checkedFailureCount > 0 && !failed) {
                delay(TILE_LOAD_TIMEOUT_MS)
                view.evaluateJavascript(
                    """typeof window.zephyrHasVisibleTiles === 'function' &&
                        window.zephyrHasVisibleTiles() !== null ? null :
                        performance.getEntriesByType('resource')
                            .filter(entry => entry.responseEnd > 0).map(entry => entry.name)"""
                ) { result ->
                    if (view === browser && tileFailureCount == checkedFailureCount &&
                        allRequestedTilesFailed(result, failedTiles)
                    ) {
                        failed = true
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant).testTag("CampsiteMap"),
            contentAlignment = Alignment.Center
        ) {
            if (url == null) {
                Text(stringResource(R.string.campsite_map_missing), modifier = Modifier.padding(16.dp))
            } else if (failed) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.campsite_map_error))
                    TextButton(onClick = { attempt++ }) {
                        Text(stringResource(R.string.campsite_map_retry))
                    }
                }
            } else {
                key(url, attempt) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().testTag("CampsiteMapBrowser"),
                        factory = { context ->
                            WebView(context).apply {
                                browser = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                contentDescription = description
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.userAgentString +=
                                    " ZephyrCampsites/1.0 (+https://github.com/CMPUT301F26zephyrAAA/zephyr_project)"
                                watchVisibleTiles(this) {
                                    if (browser === this) {
                                        failed = true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): WebResourceResponse? {
                                        return interceptResource?.invoke(request)
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError
                                    ) {
                                        recordFailure(request)
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        response: WebResourceResponse
                                    ) {
                                        recordFailure(request)
                                    }

                                    private fun recordFailure(request: WebResourceRequest) {
                                        if (isEmbedResource(request)) {
                                            failed = true
                                        } else if (isMapTile(request.url)) {
                                            failedTiles = failedTiles + request.url.toString()
                                            tileFailureCount++
                                        }
                                    }
                                }
                                setOnTouchListener { view, event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> view.parent.requestDisallowInterceptTouchEvent(true)
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
                                    }
                                    false
                                }
                                loadUrl(url)
                            }
                        },
                        onRelease = { view ->
                            browser = null
                            view.stopLoading()
                            view.destroy()
                        }
                    )
                }
            }
        }
    }

    // AI Citation [AI-47]
    // OpenAI, Codex, November 27, 2026. Connected viewport checks to the Compose retry state.
    // See Bhav Saluja's entry in the team's AI Documentation Google Doc.
    /**
     * Watches the embed's current viewport, including tiles already cached by the renderer.
     * The resource-timing check still covers the initial load on older WebViews.
     */
    private fun watchVisibleTiles(view: WebView, onEmpty: () -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            return
        }

        val origins = setOf(OSM_EMBED_ORIGIN)
        WebViewCompat.addWebMessageListener(
            view,
            "zephyrViewportStatus",
            origins
        ) { _, message, _, mainFrame, _ ->
            if (mainFrame && message.data == "empty") {
                onEmpty()
            }
        }
        val script = view.resources.openRawResource(R.raw.campsite_map_viewport).bufferedReader().use {
            it.readText()
        }
        WebViewCompat.addDocumentStartJavaScript(view, "($script)($TILE_LOAD_TIMEOUT_MS)", origins)
    }

    /**
     * Checks completed tile requests, including cached tiles. WebView reports failed URLs
     * separately because resource timing does not expose the tile server's HTTP status.
     */
    private fun allRequestedTilesFailed(timingResult: String?, failedTiles: Set<String>): Boolean {
        val resources = runCatching { JSONArray(timingResult.orEmpty()) }.getOrNull() ?: return false
        return (0 until resources.length()).none { index ->
            val resource = resources.optString(index)
            resource !in failedTiles && isMapTile(Uri.parse(resource))
        }
    }

    private fun isMapTile(url: Uri): Boolean {
        return url.scheme == "https" && url.host == "tile.openstreetmap.org" &&
            url.path?.matches(tilePath) == true
    }

    /** A missing tile can leave the map usable. The embed page and its own assets are required. */
    private fun isEmbedResource(request: WebResourceRequest): Boolean {
        return request.isForMainFrame || (
            request.url.host == "www.openstreetmap.org" &&
                request.url.path?.startsWith("/assets/embed-") == true
            )
    }
}
