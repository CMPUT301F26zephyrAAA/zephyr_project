
package com.example.zephyrcampsites

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AccountsViewTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Opens the profile screen using the temporary My Profile button.
     */

    private fun openProfile() {

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule
                .onAllNodesWithText("My Profile")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("My Profile")
            .performClick()
    }


    @Test
    fun profileFieldsAreDisplayed() {
        openProfile()

        composeTestRule.onNodeWithTag("profileName").assertExists()
        composeTestRule.onNodeWithTag("profileEmail").assertExists()
        composeTestRule.onNodeWithTag("profilePhone").assertExists()
    }

    @Test
    fun entrantCanEditProfileFields() {
        openProfile()

        composeTestRule.onNodeWithTag("profileName")
            .performTextClearance()
        composeTestRule.onNodeWithTag("profileName")
            .performTextInput("Test User")

        composeTestRule.onNodeWithTag("profileEmail")
            .performTextClearance()
        composeTestRule.onNodeWithTag("profileEmail")
            .performTextInput("test@example.com")

        composeTestRule.onNodeWithTag("profilePhone")
            .performTextClearance()
        composeTestRule.onNodeWithTag("profilePhone")
            .performTextInput("7805551234")

        composeTestRule.onNodeWithTag("profileName")
            .assertTextContains("Test User")

        composeTestRule.onNodeWithTag("profileEmail")
            .assertTextContains("test@example.com")

        composeTestRule.onNodeWithTag("profilePhone")
            .assertTextContains("7805551234")
    }

    @Test
    fun saveChangesButtonIsDisplayed() {
        openProfile()

        composeTestRule.onNodeWithTag("saveProfile")
            .assertExists()

        composeTestRule.onNodeWithText("Save Changes")
            .assertExists()
    }

    @Test
    fun backButtonReturnsToCampsiteScreen() {
        openProfile()

        composeTestRule.onNodeWithText("Back")
            .performClick()

        composeTestRule.onNodeWithTag("CampsiteDetails1")
            .assertExists()
    }
}
