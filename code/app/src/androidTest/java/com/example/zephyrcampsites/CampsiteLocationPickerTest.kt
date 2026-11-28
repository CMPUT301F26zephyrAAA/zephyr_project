package com.example.zephyrcampsites

// Exercises the owner location step and its GeoPoint callback using local fixtures.

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

// AI Citation [AI-44]
// OpenAI, Codex, November 27, 2026. Added tests for the form and the live map picker.
// See Bhav Saluja's entry in the team's AI Documentation Google Doc.
@RunWith(AndroidJUnit4::class)
class CampsiteLocationPickerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val savedLocation = GeoPoint(52.86, -118.11)
    private val campsite = Campsite("1").apply {
        geolocation = savedLocation
        bookingPeriodStart = Date(1893456000000L)
        bookingPeriodEnd = Date(1894060800000L)
    }

    @Test
    fun missingPinDisablesSaveEvenWhenDatesArePresent() {
        showLocationStep(initialLocation = null)

        composeTestRule.onNodeWithText("Choose on map").assertIsDisplayed()
        composeTestRule.onNodeWithText("Latitude").assertDoesNotExist()
        composeTestRule.onNodeWithText("Longitude").assertDoesNotExist()
        composeTestRule.onNodeWithText("SAVE CHANGES").assertIsNotEnabled()
    }

    @Test
    fun editCanSaveThePreviouslyStoredGeoPointWithoutOpeningTheMap() {
        var published: GeoPoint? = null
        showLocationStep(savedLocation) { published = it }

        composeTestRule.onNodeWithText("Change map location").assertIsDisplayed()
        composeTestRule.onNodeWithText("SAVE CHANGES").performClick()
        composeTestRule.runOnIdle { assertEquals(savedLocation, published) }
    }

    @Test
    fun androidBackFromThePickerPreservesTheSavedPin() {
        var published: GeoPoint? = null
        showLocationStep(savedLocation) { published = it }

        composeTestRule.onNodeWithText("Change map location").performClick()
        Espresso.pressBack()
        composeTestRule.onNodeWithText("Use this location").assertDoesNotExist()
        composeTestRule.onNodeWithText("SAVE CHANGES").performClick()
        composeTestRule.runOnIdle { assertEquals(savedLocation, published) }
    }

    /** Requires the onlineMap runner argument and access to the OpenFreeMap tile service. */
    @Test
    fun tapPanAndConfirmReplaceTheSavedPin() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("onlineMap") == "true")
        var published: GeoPoint? = null
        showLocationStep(savedLocation) { published = it }

        composeTestRule.onNodeWithText("Change map location").performClick()
        val confirm = hasText("Use this location") and hasClickAction() and isEnabled()
        composeTestRule.waitUntil(25_000) {
            composeTestRule.onAllNodes(confirm).fetchSemanticsNodes().isNotEmpty()
        }
        val map = composeTestRule.onNodeWithTag("CampsiteLocationPickerMap")
        map.performTouchInput { click(Offset(width * 0.25f, height * 0.55f)) }
        map.performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("Use this location").performClick()
        composeTestRule.onNodeWithText("SAVE CHANGES").assertIsEnabled().performClick()

        composeTestRule.runOnIdle {
            assertNotEquals(null, published)
            assertNotEquals(savedLocation, published)
        }
    }

    private fun showLocationStep(
        initialLocation: GeoPoint?,
        onPublish: (GeoPoint) -> Unit = {}
    ) {
        composeTestRule.setContent {
            var location by remember { mutableStateOf(initialLocation) }
            ZephyrCampsitesTheme {
                CampsiteLocationView(
                    mapLocation = location,
                    onLocationSelected = { location = it },
                    onPublish = { pin, _, _, _ -> onPublish(pin) },
                    onBack = {},
                    campsite = campsite
                )
            }
        }
    }
}
