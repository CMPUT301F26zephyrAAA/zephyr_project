package com.example.zephyrcampsites

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.GeoPoint

/** Displays the saved campsite location on an interactive map. Map tiles require a network connection. */
class CampsiteMapView {
    /**
     * Displays a map marker, or a message when coordinates are missing.
     *
     * @param location Saved campsite coordinates.
     * @param campsiteName Name used to describe the map for accessibility.
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    @Composable
    fun MapView(location: GeoPoint, campsiteName: String) {
        val url = CampsiteMapLocation.embedUrl(location.latitude, location.longitude)
        val description = stringResource(R.string.campsite_map_description, campsiteName)
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant).testTag("CampsiteMap"),
            contentAlignment = Alignment.Center
        ) {
            if (url == null) {
                Text(stringResource(R.string.campsite_map_missing), modifier = Modifier.padding(16.dp))
            } else {
                key(url) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().testTag("CampsiteMapBrowser"),
                        factory = { context ->
                            WebView(context).apply {
                                contentDescription = description
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.userAgentString += " ZephyrCampsites/1.0 (+https://github.com/CMPUT301F26zephyrAAA/zephyr_project)"
                                webViewClient = WebViewClient()
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
                            view.stopLoading()
                            view.destroy()
                        }
                    )
                }
            }
        }
    }
}
