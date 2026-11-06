package com.example.zephyrcampsites

import android.location.Location
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies campsite navigation and filter preservation with an in-memory campsite list.
 * Uses the actual summary/detail views and navigation routes without accessing Firebase.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteDetailsNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val campsite = Campsite("1").apply {
        name = "Pine Lake Campground"
        location = "Jasper, Alberta"
        maxOccupancy = 4
        pricePerNight = 35.0
        bookingPeriodStart = Date(1893456000000L)
        bookingPeriodEnd = Date(1894060800000L)
    }

    @Test
    fun campsiteRowOpensDetailsAndBackPreservesEveryFilter() {
        val filters = showNavigation()
        composeTestRule.onNodeWithTag("CampsiteSearch").performTextInput("Pine")
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("3")
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()

        var selectedStart: Long? = null
        var selectedEnd: Long? = null
        composeTestRule.runOnIdle {
            selectedStart = filters.startDate?.time
            selectedEnd = filters.endDate?.time
        }
        val deviceLocation = filters.deviceLocation
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("CampsiteRow1").performClick()
        composeTestRule.onNodeWithTag("CampsiteDetailsScreen").assertExists()
        composeTestRule.onNodeWithText("Location: Jasper, Alberta").assertExists()
        composeTestRule.onNodeWithTag("DetailsBack").performClick()

        composeTestRule.onNodeWithTag("CampsiteSearch").assertTextContains("Pine")
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertTextContains("3")
        composeTestRule.onNodeWithText("Current selected date: ").assertExists()
        composeTestRule.onNodeWithTag("DistanceLimitMenu").assertTextContains("50km")
        composeTestRule.runOnIdle {
            assertEquals("Pine", filters.searchString)
            assertEquals(3, filters.guests)
            assertEquals(50, filters.distanceLimit)
            assertSame(deviceLocation, filters.deviceLocation)
            assertEquals(selectedStart, filters.startDate?.time)
            assertEquals(selectedEnd, filters.endDate?.time)
        }
    }

    @Test
    fun viewDetailsButtonAndAndroidBackPreserveSearch() {
        val filters = showNavigation()
        composeTestRule.onNodeWithTag("CampsiteSearch").performTextInput("Pine")
        Espresso.closeSoftKeyboard()
        composeTestRule.onNodeWithTag("CampsiteDetails1").performClick()
        composeTestRule.onNodeWithTag("CampsiteDetailsScreen").assertExists()
        Espresso.pressBack()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("CampsiteSearch")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("CampsiteSearch").assertTextContains("Pine")
        composeTestRule.onNodeWithTag("CampsiteDetails1").assertExists()
        composeTestRule.runOnIdle {
            assertEquals("Pine", filters.searchString)
        }
    }

    @Test
    fun emptyGuestFilterRemainsEmptyAfterReturningFromDetails() {
        val filters = showNavigation()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("CampsiteDetails1").performClick()
        composeTestRule.onNodeWithTag("DetailsBack").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertTextContains("")
        composeTestRule.runOnIdle {
            assertEquals(null, filters.guests)
        }
    }

    /**
     * Connects the existing views with local fixtures and the normal campsite routes.
     *
     * @return The filter object retained by the summary view during navigation.
     */
    private fun showNavigation(): UserFilter {
        val filters = UserFilter(
            deviceLocation = Location("fixture").apply {
                latitude = 52.8737
                longitude = -118.0814
            },
            distanceLimit = 50,
            startDate = Date(1893456000000L),
            endDate = Date(1894060800000L),
            guests = 1
        )

        composeTestRule.setContent {
            val context = LocalContext.current
            val locationClient = remember {
                LocationServices.getFusedLocationProviderClient(context)
            }
            val summary = remember {
                CampsiteSummaryView().apply { currentUserFilters = filters }
            }
            val detail = remember { CampsiteDetailView() }
            val navController = rememberNavController()

            ZephyrCampsitesTheme {
                NavHost(
                    navController = navController,
                    startDestination = CampsiteScreens.SummaryView.route
                ) {
                    composable(CampsiteScreens.SummaryView.route) {
                        summary.SummaryView(
                            onClick = {},
                            openDetail = { id ->
                                navController.navigate(CampsiteScreens.DetailView.createRoute(id))
                            },
                            searchHandler = { listOf(campsite) },
                            filterPage = {},
                            imageLoader = { "" },
                            locationClient = locationClient
                        )
                    }
                    composable(
                        route = CampsiteScreens.DetailView.route,
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { entry ->
                        detail.DetailView(
                            campsite = if (entry.arguments?.getInt("id") == 1) campsite else null,
                            codeGenerator = { _, _ -> ImageBitmap(1, 1) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
        return filters
    }
}
