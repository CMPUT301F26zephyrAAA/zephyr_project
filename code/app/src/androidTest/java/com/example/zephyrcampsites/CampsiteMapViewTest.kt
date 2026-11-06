package com.example.zephyrcampsites

import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matcher

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
    }
}
