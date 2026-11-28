package com.example.zephyrcampsites

import android.view.View
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matcher
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Verifies the map layout, missing coordinates, and load errors using local campsite fixtures. */
@RunWith(AndroidJUnit4::class)
class CampsiteMapViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun missingCoordinatesShowFallbackWithoutCreatingABrowser() {
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteMapView().MapView(GeoPoint(0.0, 0.0), "Pine Lake Campground")
            }
        }
        composeTestRule.onNodeWithText("Map location not provided.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CampsiteMapBrowser").assertDoesNotExist()
    }

    @Test
    fun mapUsesFullWidthBelowDetailsAndBackStillWorks() {
        var returned = false
        val campsite = Campsite("1").apply {
            name = "Pine Lake Campground"
            description = "A lakeside campsite in Jasper."
        }
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(campsite = campsite, codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = { returned = true })
            }
        }
        val map = composeTestRule.onNodeWithTag("CampsiteMap").performScrollTo()
        map.assertIsDisplayed().assertHeightIsEqualTo(160.dp)
        val mapBounds = map.fetchSemanticsNode().boundsInRoot
        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(rootBounds.width, mapBounds.width, 1f)
        val availability = composeTestRule.onNodeWithText("Availability (booking period):", substring = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(mapBounds.top >= availability.bottom)
        composeTestRule.onNodeWithTag("DetailsBack").performClick()
        assertTrue(returned)
    }

    @Test
    fun savedCoordinatesCreateTheMapBrowser() {
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteMapView().MapView(GeoPoint(52.86, -118.11), "Pine Lake Campground")
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("CampsiteMapBrowser").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("CampsiteMapBrowser").assertIsDisplayed()
        onView(isAssignableFrom(WebView::class.java)).check { view, exception ->
            if (exception != null) { throw exception }
            assertEquals(CampsiteMapLocation.embedUrl(52.86, -118.11), (view as WebView).url)
        }
    }

    /** Runs with the onlineMap instrumentation argument to check the real map service and touch gestures. */
    @Test
    fun onlineMapLoadsTilesAndRespondsToZoomAndPan() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineMap") == "true")
        val campsite = Campsite("1").apply {
            name = "Pine Lake Campground"
            location = "Jasper, Alberta"
            geolocation = GeoPoint(52.86, -118.11)
        }
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(campsite = campsite, codeGenerator = { _, _ -> ImageBitmap(2, 2) }, onBack = {})
            }
        }
        val map = composeTestRule.onNodeWithTag("CampsiteMap").performScrollTo()
        map.assertIsDisplayed()
        lateinit var browser: WebView
        onView(isAssignableFrom(WebView::class.java)).check { view, exception ->
            if (exception != null) { throw exception }
            browser = view as WebView
        }
        composeTestRule.waitUntil(45000) {
            readMap(browser, "document.querySelector('#map')?.clientHeight > 0 && document.querySelector('.maplibregl-canvas')?.width > 0 && performance.getEntriesByType('resource').some(t => t.name.includes('tile.openstreetmap.org/') && t.responseEnd > 0)") == "true"
        }
        assertEquals("true", readMap(browser, "document.querySelector('.maplibregl-marker')?.getBoundingClientRect().width > 0"))
        composeTestRule.onNodeWithText("Unable to load map. Check your connection.").assertDoesNotExist()
        saveMapScreenshot("map-loaded")

        val zoom = readMap(browser, "performance.getEntriesByType('resource').find(t => t.name.includes('tile.openstreetmap.org/')).name.split('/').slice(-3)[0]").trim('"').toInt()
        map.performTouchInput { click(Offset(width - height * 0.15f, height * 0.15f)) }
        composeTestRule.waitUntil(15000) {
            readMap(browser, "performance.getEntriesByType('resource').some(t => t.name.includes('tile.openstreetmap.org/') && t.responseEnd > 0 && Number(t.name.split('/').slice(-3)[0]) > $zoom)") == "true"
        }
        saveMapScreenshot("map-zoomed")

        val position = readMap(browser, "document.querySelector('.maplibregl-marker').style.transform")
        map.performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(10000) {
            readMap(browser, "document.querySelector('.maplibregl-marker').style.transform") != position
        }
        composeTestRule.onNodeWithText("Unable to load map. Check your connection.").assertDoesNotExist()
        saveMapScreenshot("map-panned")
    }

    /** Reads the rendered map on the UI thread and waits for WebView's asynchronous result. */
    private fun readMap(browser: WebView, script: String): String {
        val completed = CountDownLatch(1)
        var result = ""
        composeTestRule.runOnIdle {
            browser.evaluateJavascript(script) {
                result = it
                completed.countDown()
            }
        }
        assertTrue("WebView did not respond", completed.await(15, TimeUnit.SECONDS))
        return result
    }

    /** Saves emulator screenshots to the app cache for visual verification outside the repository. */
    private fun saveMapScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.externalCacheDir, "$name.png").outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    @Test
    fun failedMapLoadShowsAConnectionMessage() {
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteMapView().MapView(GeoPoint(52.86, -118.11), "Pine Lake Campground")
            }
        }
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Load an unreachable map address without using cached content"
            override fun perform(uiController: UiController, view: View) {
                val browser = view as WebView
                browser.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                browser.loadUrl("https://map-load-test.invalid/")
            }
        })
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Unable to load map. Check your connection.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Unable to load map. Check your connection.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CampsiteMapBrowser").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry map").performClick()
        composeTestRule.onNodeWithTag("CampsiteMapBrowser").assertIsDisplayed()
        onView(isAssignableFrom(WebView::class.java)).check { view, exception ->
            if (exception != null) {
                throw exception
            }
            assertEquals(CampsiteMapLocation.embedUrl(52.86, -118.11), (view as WebView).url)
        }
    }

    // AI Citation [AI-46]
    // OpenAI, Codex, November 27, 2026. Added tile-failure response fixtures and retry coverage.
    // See Bhav Saluja's entry in the team's AI Documentation Google Doc.
    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun successfulPageWithAllTilesFailedShowsRetry() {
        val httpErrors = loadMapWithTileResponses(workingTile = false)
        assertTrue("Both tile requests should fail", httpErrors.await(5, TimeUnit.SECONDS))

        composeTestRule.waitUntil(15_000) {
            composeTestRule.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Unable to load map. Check your connection.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CampsiteMapBrowser").assertDoesNotExist()
        composeTestRule.onNodeWithText("Retry map").performClick()
        onView(isAssignableFrom(WebView::class.java)).check { view, exception ->
            if (exception != null) {
                throw exception
            }
            assertEquals(CampsiteMapLocation.embedUrl(52.86, -118.11), (view as WebView).url)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun successfulTileKeepsMapVisibleWhenAnotherTileFails() {
        val httpErrors = loadMapWithTileResponses(workingTile = true)
        assertTrue("One tile request should fail", httpErrors.await(5, TimeUnit.SECONDS))

        android.os.SystemClock.sleep(9_000) // Give the production tile-check timeout time to run.
        composeTestRule.onNodeWithTag("CampsiteMapBrowser").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry map").assertDoesNotExist()
    }

    // AI Citation [AI-47]
    // OpenAI, Codex, November 27, 2026. Added a regression test using the live embed and controlled tiles.
    // See Bhav Saluja's entry in the team's AI Documentation Google Doc.
    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun panAfterConnectionLossShowsRetryOnceCachedTilesLeaveView() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineMap") == "true")
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))

        val connected = AtomicBoolean(true)
        val tileError = CountDownLatch(1)
        val savedUrl = checkNotNull(CampsiteMapLocation.embedUrl(52.86, -118.11))
        val png = mapTileImage()
        val browser = loadMapFixture("$savedUrl&viewport-test", intercept = { request ->
            if (request.url.host == "tile.openstreetmap.org") {
                tileResponse(connected.get(), png)
            } else {
                null
            }
        }, onTileError = {
            tileError.countDown()
        })
        composeTestRule.waitUntil(30_000) {
            readMap(browser, "window.zephyrHasVisibleTiles?.()") == "true"
        }

        connected.set(false)
        val map = composeTestRule.onNodeWithTag("CampsiteMap")
        map.performTouchInput {
            swipe(Offset(width * 0.5f, height * 0.6f), Offset(width * 0.46f, height * 0.6f), 400)
        }
        android.os.SystemClock.sleep(9_000) // The visible cached tiles must outlast the tile-check timeout.
        map.assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry map").assertDoesNotExist()
        assertEquals("true", readMap(browser, "window.zephyrHasVisibleTiles?.()"))

        repeat(5) {
            map.performTouchInput { swipeLeft() }
        }
        assertTrue("Panning beyond the cached tiles should request new ones", tileError.await(10, TimeUnit.SECONDS))
        composeTestRule.waitUntil(20_000) {
            composeTestRule.onAllNodesWithText("Retry map").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Unable to load map. Check your connection.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry map").performClick()
        onView(isAssignableFrom(WebView::class.java)).check { view, exception ->
            if (exception != null) {
                throw exception
            }
            assertEquals(savedUrl, (view as WebView).url)
        }
    }

    /** Serves a successful embed document and lets WebView report the controlled tile HTTP responses. */
    private fun loadMapWithTileResponses(workingTile: Boolean): CountDownLatch {
        val tileError = "https://tile.openstreetmap.org/1/0/0.png"
        val tileOther = "https://tile.openstreetmap.org/1/1/0.png"
        val fixtureUrl = "https://www.openstreetmap.org/export/embed.html?tile-test"
        val html = """
            <!doctype html><html><body>
            <script>fetch('$tileError'); fetch('$tileOther');</script>
            </body></html>
        """.trimIndent()
        val png = mapTileImage()
        val httpErrors = CountDownLatch(if (workingTile) 1 else 2)
        loadMapFixture(fixtureUrl, intercept = { request ->
            when (request.url.toString()) {
                fixtureUrl -> WebResourceResponse("text/html", "UTF-8", html.byteInputStream())
                tileOther -> tileResponse(workingTile, png)
                tileError -> tileResponse(false, png)
                else -> null
            }
        }, onTileError = {
            httpErrors.countDown()
        })
        return httpErrors
    }

    private fun mapTileImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        val png = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        return png
    }

    private fun tileResponse(working: Boolean, png: ByteArray): WebResourceResponse {
        val headers = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store")
        return if (working) {
            WebResourceResponse("image/png", null, 200, "OK", headers, png.inputStream())
        } else {
            WebResourceResponse("text/plain", "UTF-8", 503, "Unavailable", headers,
                "Unavailable".byteInputStream())
        }
    }

    /** Intercepts fixture responses while leaving the production WebView error handling active. */
    private fun loadMapFixture(
        fixtureUrl: String,
        intercept: (WebResourceRequest) -> WebResourceResponse?,
        onTileError: () -> Unit
    ): WebView {
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteMapView().MapView(GeoPoint(52.86, -118.11), "Pine Lake Campground")
            }
        }

        lateinit var fixtureBrowser: WebView
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun getDescription(): String = "Load the embed and controlled tile HTTP responses"
            override fun perform(uiController: UiController, view: View) {
                val browser = view as WebView
                fixtureBrowser = browser
                browser.stopLoading()
                val client = browser.webViewClient
                browser.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return intercept(request)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        client.onReceivedError(view, request, error)
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        response: WebResourceResponse
                    ) {
                        client.onReceivedHttpError(view, request, response)
                        if (!request.isForMainFrame && response.statusCode == 503 &&
                            request.url.host == "tile.openstreetmap.org"
                        ) {
                            onTileError()
                        }
                    }
                }
                browser.loadUrl(fixtureUrl)
            }
        })
        return fixtureBrowser
    }
}
