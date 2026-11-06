package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the profile overview and editor using local account fixtures.
 * These UI tests do not initialize a device account or submit profile changes.
 */
@RunWith(AndroidJUnit4::class)
class AccountsViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun showProfileNavigation() {
        val account = Account("profile-test-device").apply {
            name = "Taylor Morgan"
            email = "taylor@example.com"
            phoneNumber = "7805550100"
        }
        val campsite = Campsite("1").apply {
            name = "Fixture campsite"
            maxOccupancy = 4
        }

        composeTestRule.setContent {
            val context = LocalContext.current
            val locationClient = remember {
                LocationServices.getFusedLocationProviderClient(context)
            }
            val accountsView = remember { AccountsView() }
            val summaryView = remember { CampsiteSummaryView() }
            val navController = rememberNavController()
            val currentEntry by navController.currentBackStackEntryAsState()

            ZephyrCampsitesTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (currentEntry?.destination?.route == CampsiteScreens.SummaryView.route) {
                        Button(onClick = { navController.navigate(GeneralScreen.Profile.route) }) {
                            Text("My Profile")
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = CampsiteScreens.SummaryView.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(CampsiteScreens.SummaryView.route) {
                            summaryView.SummaryView(
                                onClick = {},
                                openDetail = {},
                                searchHandler = { listOf(campsite) },
                                filterPage = {},
                                imageLoader = { "" },
                                locationClient = locationClient
                            )
                        }
                        composable(GeneralScreen.Profile.route) {
                            accountsView.ProfileOverviewView(
                                account = account,
                                onEdit = {
                                    navController.navigate(GeneralScreen.PersonalInformation.route)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(GeneralScreen.PersonalInformation.route) {
                            val controller = remember { AccountsController() }
                            accountsView.ProfileView(
                                account = account,
                                accountsController = controller,
                                onSaved = {},
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Opens the profile overview and optionally its personal-information editor.
     *
     * @param openEditor Whether to open Personal Information from the overview.
     */
    private fun openProfile(openEditor: Boolean = false) {
        composeTestRule.onNodeWithText("My Profile").performClick()
        if (openEditor) {
            composeTestRule.onNodeWithTag("personalInformationButton")
                .performScrollTo()
                .performClick()
        }
    }

    @Test
    fun profileFieldsAreDisplayed() {
        openProfile(openEditor = true)
        composeTestRule.onNodeWithTag("profileName").assertExists()
        composeTestRule.onNodeWithTag("profileEmail").assertExists()
        composeTestRule.onNodeWithTag("profilePhone").assertExists()
    }

    @Test
    fun entrantCanEditProfileFields() {
        openProfile(openEditor = true)
        composeTestRule.onNodeWithTag("profileName").performTextClearance()
        composeTestRule.onNodeWithTag("profileName").performTextInput("Test User")
        composeTestRule.onNodeWithTag("profileEmail").performTextClearance()
        composeTestRule.onNodeWithTag("profileEmail").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("profilePhone").performTextClearance()
        composeTestRule.onNodeWithTag("profilePhone").performTextInput("7805551234")
        composeTestRule.onNodeWithTag("profileName").assertTextContains("Test User")
        composeTestRule.onNodeWithTag("profileEmail").assertTextContains("test@example.com")
        composeTestRule.onNodeWithTag("profilePhone").assertTextContains("7805551234")
    }

    @Test
    fun saveChangesButtonIsDisplayed() {
        openProfile(openEditor = true)
        composeTestRule.onNodeWithTag("saveProfile").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("SAVE").assertExists()
    }

    @Test
    fun backButtonReturnsToCampsiteScreen() {
        openProfile()
        composeTestRule.onNodeWithText("BACK").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("CampsiteDetails1").assertExists()
    }
}
