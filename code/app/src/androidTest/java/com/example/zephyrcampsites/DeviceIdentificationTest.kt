package com.example.zephyrcampsites

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Hiral - [AI-06] OpenAI, ChatGPT, Fixing Testcases.
@RunWith(AndroidJUnit4::class)
class DeviceIdentificationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deviceAccountCanBrowseAsEntrantAndSwitchToOwnerWithoutLoggingIn() {
        val entrantButton = hasText("Entrant") and hasClickAction() and isEnabled()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodes(entrantButton).fetchSemanticsNodes().size == 1
        }

        composeTestRule.onNodeWithText("Login").assertDoesNotExist()
        composeTestRule.onNodeWithText("Password").assertDoesNotExist()

        composeTestRule.onNode(entrantButton).performScrollTo().performClick()
        composeTestRule.onNodeWithText("My Trips").assertExists()
        composeTestRule.onNodeWithText("Register Campsite").assertDoesNotExist()

        composeTestRule.onNodeWithText("Browse Campsites").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("CampsiteSearch").assertIsDisplayed()
        composeTestRule.onNodeWithText("testing button").assertDoesNotExist()
        composeTestRule.onNodeWithText("Back").performClick()

        composeTestRule.onNodeWithText("Switch Role").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Owner").performClick()
        composeTestRule.onNodeWithText("Register Campsite").assertExists()
        composeTestRule.onNodeWithText("My Trips").assertDoesNotExist()
        composeTestRule.onNodeWithText("My Campsites").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }
}