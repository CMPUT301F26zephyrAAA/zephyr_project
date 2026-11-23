package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the profile overview and editor using local account fixtures.
 * Account updates use an in-memory model without accessing Firebase.
 */
@RunWith(AndroidJUnit4::class)
class AccountsViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var currentAccount: MutableState<Account>
    private lateinit var accountsModel: TestAccountsModel

    @Before
    fun showProfileNavigation() {
        val account = Account("profile-test-device").apply {
            name = "Taylor Morgan"
            email = "taylor@example.com"
            phoneNumber = "7805550100"
        }
        currentAccount = mutableStateOf(account)
        accountsModel = TestAccountsModel(account)
        val accountsController = AccountsController(accountsModel)
        val campsite = Campsite("1").apply {
            name = "Fixture campsite"
            maxOccupancy = 4
        }

        composeTestRule.setContent {
            val displayedAccount = currentAccount.value
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
                                account = displayedAccount,
                                onEdit = {
                                    navController.navigate(GeneralScreen.PersonalInformation.route)
                                },
                                onPolicy = { navController.navigate(GeneralScreen.Policy.route) },
                                onSettings = { navController.navigate(GeneralScreen.Settings.route) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(GeneralScreen.Policy.route) {
                            PolicyView().PolicyAndGuidelinesView(onBack = { navController.popBackStack() })
                        }
                        composable(GeneralScreen.PersonalInformation.route) {
                            accountsView.ProfileView(
                                account = displayedAccount,
                                accountsController = accountsController,
                                onSaved = { currentAccount.value = it },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(GeneralScreen.Settings.route) {
                            SettingsView().SettingsScreen(
                                account = displayedAccount,
                                accountsController = accountsController,
                                onSaved = { currentAccount.value = it },
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

    @Test
    fun policyEntryIsAvailableOnProfile() {
        openProfile()
        composeTestRule.onNodeWithTag("PolicyAndGuidelinesEntry")
            .performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("App Policy and Guidelines").assertIsDisplayed()
    }

    @Test
    fun policyGuidelinesCanBeReadByScrolling() {
        openPolicy()
        composeTestRule.onNodeWithText("Device-based profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Only rate a campsite after you have stayed there.", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("PolicyBack").assertIsDisplayed()
    }

    @Test
    fun policyBackReturnsToProfile() {
        openPolicy()
        composeTestRule.onNodeWithTag("PolicyBack").performClick()
        composeTestRule.onNodeWithText("PROFILE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Taylor Morgan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PolicyAndGuidelinesEntry").assertExists()
    }

    @Test
    fun androidBackFromPolicyReturnsToProfile() {
        openPolicy()
        Espresso.pressBack()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("PolicyAndGuidelinesEntry")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PROFILE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Taylor Morgan").assertIsDisplayed()
    }

    /**
     * Opens policy and guidelines through the profile entry.
     */
    private fun openPolicy() {
        openProfile()
        composeTestRule.onNodeWithTag("PolicyAndGuidelinesEntry").performScrollTo().performClick()
        composeTestRule.onNodeWithText("POLICY AND GUIDELINES").assertIsDisplayed()
    }

    @Test
    fun settingsEntryOpensNotificationToggle() {
        openSettings()
        composeTestRule.onNodeWithText("App Notifications").assertIsDisplayed()
        composeTestRule.onNodeWithTag("NotificationsToggle").assertIsOn()
    }

    @Test
    fun notificationPreferenceIsPreservedWhenSettingsReopen() {
        openSettings()
        composeTestRule.onNodeWithTag("NotificationsToggle").performClick().assertIsOff()
        composeTestRule.onNodeWithTag("SettingsBack").performClick()
        composeTestRule.onNodeWithTag("AccountSettingsEntry").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("NotificationsToggle").assertIsOff()
        composeTestRule.onNodeWithTag("NotificationsToggle").performClick().assertIsOn()
        composeTestRule.runOnIdle { assertTrue(accountsModel.saved.notificationsEnabled) }
    }

    @Test
    fun failedNotificationSaveKeepsThePreviousPreference() {
        openSettings()
        accountsModel.failPreferenceUpdates = true
        composeTestRule.onNodeWithTag("NotificationsToggle").performClick().assertIsOn()
        composeTestRule.onNodeWithTag("SettingsError").assertIsDisplayed()
        accountsModel.failPreferenceUpdates = false
        composeTestRule.onNodeWithTag("NotificationsToggle").performClick().assertIsOff()
        composeTestRule.onNodeWithTag("SettingsError").assertDoesNotExist()
    }

    @Test
    fun notificationToggleIsDisabledWhileSaving() {
        openSettings()
        accountsModel.delayPreferenceUpdates = true
        composeTestRule.onNodeWithTag("NotificationsToggle").performClick().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("SettingsSaving").assertIsDisplayed()
        composeTestRule.runOnIdle { accountsModel.pendingUpdate!!.invoke() }
        composeTestRule.onNodeWithTag("NotificationsToggle").assertIsOff().assertIsEnabled()
    }

    @Test
    fun profileEditDoesNotResetNotificationPreference() {
        openSettings()
        composeTestRule.onNodeWithTag("NotificationsToggle").performClick().assertIsOff()
        composeTestRule.onNodeWithTag("SettingsBack").performClick()
        composeTestRule.onNodeWithTag("personalInformationButton").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("profileName").performTextClearance()
        composeTestRule.onNodeWithTag("profileName").performTextInput("Updated Name")
        composeTestRule.onNodeWithTag("saveProfile").performScrollTo().performClick()
        composeTestRule.onNodeWithText("BACK").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AccountSettingsEntry").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("NotificationsToggle").assertIsOff()
        composeTestRule.runOnIdle {
            assertEquals("Updated Name", accountsModel.saved.name)
            assertFalse(accountsModel.saved.notificationsEnabled)
        }
    }

    @Test
    fun androidBackFromSettingsReturnsToProfile() {
        openSettings()
        Espresso.pressBack()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("AccountSettingsEntry").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PROFILE").assertExists()
        composeTestRule.onNodeWithTag("AccountSettingsEntry").assertIsDisplayed()
    }

    /**
     * Opens account settings through the profile entry.
     */
    private fun openSettings() {
        openProfile()
        composeTestRule.onNodeWithTag("AccountSettingsEntry").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("AccountSettingsScreen").assertIsDisplayed()
    }

    /**
     * Stores fixture account updates and simulates preference-save results.
     *
     * @param account Initial saved account.
     */
    private class TestAccountsModel(account: Account) : AccountsModel() {
        var saved = account.copy()
        var failPreferenceUpdates = false
        var delayPreferenceUpdates = false
        var pendingUpdate: (() -> Unit)? = null

        override fun updateNotificationPreference(
            deviceId: String,
            enabled: Boolean,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit
        ) {
            val complete = {
                if (failPreferenceUpdates) {
                    onError(IllegalStateException("Simulated failure"))
                } else {
                    saved = saved.copy(notificationsEnabled = enabled)
                    onSuccess()
                }
            }
            if (delayPreferenceUpdates) {
                pendingUpdate = complete
            } else {
                complete()
            }
        }

        override fun updateAccount(account: Account, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
            saved = saved.copy(name = account.name, email = account.email, phoneNumber = account.phoneNumber)
            onSuccess()
        }
    }
}
