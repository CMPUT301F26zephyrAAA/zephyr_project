
package com.example.zephyrcampsites

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for device-based account identification.
 */
@RunWith(AndroidJUnit4::class)
class DeviceIdentificationTest {

    @get:Rule
    val composeTestRule =
        createAndroidComposeRule<MainActivity>()

    /**
     * Checks that the application does not
     * require a username or password.
     */
    @Test
    fun appLaunch_doesNotRequireLogin() {

        composeTestRule
            .onNodeWithText("Login")
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("Password")
            .assertDoesNotExist()
    }
}
