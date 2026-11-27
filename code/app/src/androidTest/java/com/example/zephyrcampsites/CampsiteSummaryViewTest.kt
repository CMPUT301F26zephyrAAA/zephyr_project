package com.example.zephyrcampsites

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices
import java.text.NumberFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Hiral - [AI-06] OpenAI, ChatGPT, Fixing Testcases.
@RunWith(AndroidJUnit4::class)
class CampsiteSummaryViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private lateinit var filters: UserFilter

    @Before
    fun showEntrantCampsites() {
        val campsite = Campsite("1").apply {
            name = "Pine Lake Campground"
            maxOccupancy = 10
        }
        val account = Account("summary-test-device")
        val summaryRoute = roleRoute(ENTRANT, CampsiteScreens.SummaryView.route)
        val detailsRoute = roleRoute(ENTRANT, CampsiteScreens.DetailView.route)
        val summary = CampsiteSummaryView()
        filters = summary.currentUserFilters

        composeTestRule.setContent {
            val context = LocalContext.current
            val location = remember { LocationServices.getFusedLocationProviderClient(context) }
            val nav = rememberNavController()

            ZephyrCampsitesTheme {
                NavHost(navController = nav, startDestination = summaryRoute) {
                    composable(summaryRoute) {
                        summary.SummaryView(
                            onClick = null,
                            openDetail = { id ->
                                nav.navigate(roleRoute(ENTRANT, CampsiteScreens.DetailView.createRoute(id)))
                            },
                            searchHandler = { listOf(campsite) },
                            filterPage = {},
                            imageLoader = { "" },
                            locationClient = location,
                            onViewChanged = null,
                        )
                    }
                    composable(
                        route = detailsRoute,
                        arguments = listOf(navArgument("id") { type = NavType.IntType }),
                    ) { entry ->
                        CampsiteDetailView().DetailView(
                            campsite = campsite.takeIf { entry.arguments?.getInt("id") == 1 },
                            account = account,
                            showEntrantActions = true,
                            codeGenerator = { _, _ -> ImageBitmap(1, 1) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun selectCalendarDay(day: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val label = NumberFormat.getIntegerInstance(locale).format(day)
        composeTestRule.onNode(hasText(label) and hasClickAction()).performClick()
    }

    @Test
    fun openFilter() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithText("Choose maximum distance for campsite: ").assertExists()
        composeTestRule.onNodeWithText("Click to select dates you want to stay...").assertExists()
        composeTestRule.onNodeWithText("Enter amount of guests (max: 10)").assertExists()
        composeTestRule.onNodeWithText("Reset filters").assertExists()
    }

    @Test
    fun verifyFilterPersistence_guestsLimitTest() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("10")
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertExists()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun guestsLimitTestFail() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("11")
        composeTestRule.onNodeWithText("Max guests exceeded!").assertExists()
        composeTestRule.onNodeWithText("Dismiss").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun verifyGuestsTextFieldFilter_mixLetter() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("ajdk10ajkd")
        composeTestRule.onNodeWithTag("GuestsEntry").printToLog("TextFieldLog")
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertExists()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun verifyGuestsTextFieldFilter_mixSpace() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput(" 1   0 ")
        composeTestRule.onNodeWithTag("GuestsEntry").printToLog("TextFieldLog")
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertExists()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun sameDayRangeCanBeConfirmed() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        selectCalendarDay(5)
        selectCalendarDay(5)
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()

        composeTestRule.onNodeWithTag("DateSelectConfirm").assertDoesNotExist()
        composeTestRule.onNodeWithText("Current selected date: ", substring = true).assertExists()
        composeTestRule.runOnIdle {
            assertEquals(filters.startDate!!.time, filters.endDate!!.time)
        }
    }

    @Test
    fun forwardRangeCanBeConfirmed() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        selectCalendarDay(5)
        selectCalendarDay(7)
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()

        composeTestRule.onNodeWithTag("DateSelectConfirm").assertDoesNotExist()
        composeTestRule.onNodeWithText("Current selected date: ", substring = true).assertExists()
        composeTestRule.runOnIdle {
            assertEquals(2L * 24 * 60 * 60 * 1000, filters.endDate!!.time - filters.startDate!!.time)
        }
    }

    @Test
    fun reverseSelectionNeedsAnEndDateBeforeItCanBeConfirmed() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        selectCalendarDay(7)
        selectCalendarDay(5)
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()

        composeTestRule.onNodeWithTag("DateSelectConfirm").assertExists()
        composeTestRule.onNodeWithTag("DateSelectDismiss").performClick()
        composeTestRule.onNodeWithText("Current selected date: ", substring = true).assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertNull(filters.startDate)
            assertNull(filters.endDate)
        }
    }

    @Test
    fun openDetails() {
        composeTestRule.onNodeWithTag("CampsiteDetails1").performClick()
        composeTestRule.onNodeWithTag("CampsiteDetailsScreen").assertExists()
        composeTestRule.onNodeWithText("Pine Lake Campground").assertExists()
        Espresso.pressBack()
        composeTestRule.onNodeWithTag("CampsiteDetails1").assertExists()
    }
}