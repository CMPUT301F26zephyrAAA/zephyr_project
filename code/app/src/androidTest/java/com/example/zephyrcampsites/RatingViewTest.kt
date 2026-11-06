package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests entrant rating controls and navigation with local account, stay, and campsite fixtures. */
@RunWith(AndroidJUnit4::class)
class RatingViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private val account = mutableStateOf(Account("entrant-1"))
    private val campsites = mutableStateListOf(campsite(listOf(4, 5)))
    private val model = FakeRatingModel()

    @Test
    fun eligibleEntrantCanOpenTheRatingForm() {
        showNavigation()
        openRating()
        composeTestRule.onNodeWithTag("RateCampsiteScreen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pine Lake Campground").assertIsDisplayed()
        for (value in 1..5) {
            composeTestRule.onNodeWithTag("RatingStar$value").assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("Review (optional):").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SubmitRating").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("RatingStar1").performClick()
        composeTestRule.onNodeWithTag("SubmitRating").assertIsEnabled()
    }

    @Test
    fun noCompletedStayHidesTheRatingEntry() {
        model.eligible = false
        showNavigation()
        composeTestRule.onNodeWithTag("RateCampsiteEntry").assertDoesNotExist()
    }

    @Test
    fun directRouteCannotBypassTheStayCheck() {
        model.eligible = false
        showNavigation(startOnRating = true)
        composeTestRule.onNodeWithText("You can rate this campsite after a non-cancelled stay has ended.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RatingStar1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("SubmitRating").assertDoesNotExist()
    }

    @Test
    fun ownerAndAdminAreNotOfferedTheEntrantRatingEntry() {
        account.value = Account("owner-1", userType = "owner")
        showNavigation()
        composeTestRule.onNodeWithTag("RateCampsiteEntry").assertDoesNotExist()
        composeTestRule.runOnIdle { account.value = account.value.copy(userType = "admin") }
        composeTestRule.onNodeWithTag("RateCampsiteEntry").assertDoesNotExist()
        assertEquals(0, model.loadCalls)
    }

    @Test
    fun repeatRatingUpdatesDetailsAndListWithoutIncreasingCount() {
        showNavigation(startOnList = true)
        composeTestRule.onNodeWithTag("CampsiteRow1").performClick()
        openRating()
        composeTestRule.onNodeWithTag("RatingStar2").performClick()
        composeTestRule.onNodeWithTag("RatingReview").performTextInput("Quiet lakeside stay.")
        composeTestRule.onNodeWithTag("SubmitRating").performClick()
        composeTestRule.onNodeWithText("Rating: 3.67 / 5").assertExists()

        openRating()
        composeTestRule.onNodeWithTag("RatingStar2").assertIsOn()
        composeTestRule.onNodeWithTag("RatingReview").assertTextContains("Quiet lakeside stay.")
        composeTestRule.onNodeWithTag("RatingStar5").performClick()
        composeTestRule.onNodeWithTag("SubmitRating").performClick()
        composeTestRule.onNodeWithText("Rating: 4.67 / 5").assertExists()
        composeTestRule.onNodeWithTag("DetailsBack").performClick()
        composeTestRule.onNodeWithText("⭐ Rating: 4.67").assertExists()
        composeTestRule.runOnIdle {
            assertEquals(listOf(4, 5, 5), campsites.single().rating)
            assertEquals(2, model.saveCalls)
        }
    }

    @Test
    fun saveFailureKeepsSelectionAndReviewForRetry() {
        model.failSave = true
        showNavigation()
        openRating()
        composeTestRule.onNodeWithTag("RatingStar4").performClick()
        composeTestRule.onNodeWithTag("RatingReview").performTextInput("Clean campground.")
        composeTestRule.onNodeWithTag("SubmitRating").performClick()
        composeTestRule.onNodeWithText("Save failed.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("RatingStar4").assertIsOn()
        composeTestRule.onNodeWithTag("RatingReview").assertTextContains("Clean campground.")
        composeTestRule.runOnIdle { model.failSave = false }
        composeTestRule.onNodeWithTag("SubmitRating").performClick()
        composeTestRule.onNodeWithTag("CampsiteDetailsScreen").assertExists()
    }

    @Test
    fun pendingSaveDisablesChangesAndLateSuccessDoesNotNavigateAfterBack() {
        model.delaySave = true
        showNavigation()
        openRating()
        composeTestRule.onNodeWithTag("RatingStar3").performClick()
        composeTestRule.onNodeWithTag("SubmitRating").performClick()
        composeTestRule.onNodeWithTag("SubmitRating").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("RatingStar5").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("RatingReview").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("RatingBack").performClick()
        composeTestRule.runOnIdle { model.pendingSave?.invoke() }
        composeTestRule.onNodeWithTag("CampsiteDetailsScreen").assertExists()
    }

    @Test
    fun loadFailureCanBeRetriedWithoutOfferingUnverifiedRating() {
        model.failLoad = true
        showNavigation(startOnRating = true)
        composeTestRule.onNodeWithText("Unable to load rating.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("SubmitRating").assertDoesNotExist()
        composeTestRule.runOnIdle { model.failLoad = false }
        composeTestRule.onNodeWithText("Retry").performClick()
        composeTestRule.onNodeWithTag("RatingStar1").assertIsDisplayed()
    }

    @Test
    fun backAndAndroidBackDiscardEditsAndPreserveSearch() {
        showNavigation(startOnList = true)
        composeTestRule.onNodeWithTag("CampsiteSearch").performTextInput("Pine")
        Espresso.closeSoftKeyboard()
        composeTestRule.onNodeWithTag("CampsiteRow1").performClick()
        openRating()
        composeTestRule.onNodeWithTag("RatingStar4").performClick()
        composeTestRule.onNodeWithTag("RatingBack").performClick()
        openRating()
        composeTestRule.onNodeWithTag("SubmitRating").assertIsNotEnabled()
        Espresso.pressBack()
        composeTestRule.onNodeWithTag("DetailsBack").performClick()
        composeTestRule.onNodeWithTag("CampsiteSearch").assertTextContains("Pine")
        assertEquals(0, model.saveCalls)
    }

    private fun openRating() {
        composeTestRule.onNodeWithTag("RateCampsiteEntry").performScrollTo().performClick()
    }

    private fun showNavigation(startOnRating: Boolean = false, startOnList: Boolean = false) {
        composeTestRule.setContent {
            val nav = rememberNavController()
            val controller = remember { RatingController(model) }
            val summaryView = remember { CampsiteSummaryView() }
            val context = LocalContext.current
            val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
            val campsite = campsites.single()
            val start = when {
                startOnRating -> CampsiteScreens.RateCampsite.createRoute("1")
                startOnList -> CampsiteScreens.SummaryView.route
                else -> CampsiteScreens.DetailView.createRoute(1)
            }
            ZephyrCampsitesTheme {
                NavHost(nav, startDestination = start) {
                    composable(CampsiteScreens.SummaryView.route) {
                        summaryView.SummaryView(
                            onClick = {},
                            openDetail = { nav.navigate(CampsiteScreens.DetailView.createRoute(it)) },
                            searchHandler = { campsites.filter { item -> item.name.contains(it.searchString, ignoreCase = true) } },
                            filterPage = {}, imageLoader = { "" }, locationClient = locationClient
                        )
                    }
                    composable(CampsiteScreens.DetailView.route, arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                        CampsiteDetailView().DetailView(
                            campsite = campsite, account = account.value, codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                            onBack = { nav.popBackStack() }, ratingController = controller,
                            onRateCampsite = { nav.navigate(CampsiteScreens.RateCampsite.createRoute(it)) }
                        )
                    }
                    composable(CampsiteScreens.RateCampsite.route, arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                        RatingView().RatingScreen(campsite, account.value, controller,
                            onSaved = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    private fun campsite(scores: List<Int>): Campsite = Campsite("1").apply {
        name = "Pine Lake Campground"
        location = "Jasper, Alberta"
        maxOccupancy = 4
        rating = scores.toMutableList()
        averageRating = scores.average()
    }

    private inner class FakeRatingModel : RatingGateway {
        var eligible = true
        var rating = CampsiteRating("stay-1")
        var loadCalls = 0
        var saveCalls = 0
        var failLoad = false
        var failSave = false
        var delaySave = false
        var pendingSave: (() -> Unit)? = null

        override fun loadRating(campsiteId: String, accountId: String, onResult: (CampsiteRating?) -> Unit, onError: (String) -> Unit) {
            loadCalls++
            if (failLoad) { onError("Unable to load rating.") } else { onResult(if (eligible) rating else null) }
        }

        override fun submitRating(campsiteId: String, accountId: String, rating: CampsiteRating, onSuccess: () -> Unit, onError: (String) -> Unit) {
            saveCalls++
            if (failSave || !eligible) {
                onError("Save failed.")
            } else {
                val save = {
                    val previous = this.rating.score.takeIf { it in 1..5 }
                    val updated = RatingValidator.replaceRating(campsites.single().rating, previous, rating.score)
                    campsites.clear()
                    campsites.add(campsite(updated))
                    this.rating = rating
                    onSuccess()
                }
                if (delaySave) { pendingSave = save } else { save() }
            }
        }
    }
}
