
package com.example.zephyrcampsites
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Date

// AI Citation [AI-22]
// OpenAI, ChatGPT assisted with Compose UI tests
// for reservation screens and navigation callbacks.
// AI usage and citation documented in the GitHub Wiki.

/**
 * Instrumented Jetpack Compose UI tests for entrant reservation screens.
 * Tests reservation display, tab switching, empty states, details navigation, conditional actions, and BACK callbacks.
 * Uses local fake data and does not access Firebase.
 */
class ReservationsViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    // Fixed dates keep test fixtures deterministic.
    private val pastStart = Date(1_577_836_800_000L) // Jan 1, 2020 UTC
    private val pastEnd = Date(1_578_009_600_000L)   // Jan 3, 2020 UTC

    private val futureStart = Date(4_102_444_800_000L) // Jan 1, 2100 UTC
    private val futureEnd = Date(4_102_617_600_000L)   // Jan 3, 2100 UTC

    /**
     * Creates a campsite with local test data.
     *
     * @param id Campsite identifier.
     * @param name Campsite name.
     * @return Populated campsite.
     */
    private fun createCampsite(
        id: String,
        name: String
    ): Campsite {
        return Campsite(id).apply {
            this.name = name
            location = "Edmonton, Alberta"
            maxOccupancy = 4
            averageRating = 4.5
        }
    }

    /**
     * Creates a reservation with supplied booking dates.
     *
     * @param id Reservation identifier.
     * @param campsiteId Associated campsite identifier.
     * @param start Booking start date.
     * @param end Booking end date.
     * @return Fake reservation.
     */
    private fun createReservation(
        id: String,
        campsiteId: String,
        start: Date?,
        end: Date?
    ): ReservedUser {
        return ReservedUser(
            id = id,
            accountId = "test-entrant",
            campsiteId = campsiteId,
            bookedPeriodStart = start,
            bookedPeriodEnd = end,
            cancelled = false
        )
    }

    private val activeCampsite = createCampsite(
        "campsite-active",
        "Mountain View Campsite"
    )

    private val pastCampsite = createCampsite(
        "campsite-past",
        "Lakefront Campsite"
    )

    private val activeReservation = createReservation(
        id = "reservation-active",
        campsiteId = activeCampsite.id,
        start = futureStart,
        end = futureEnd
    )

    private val pastReservation = createReservation(
        id = "reservation-past",
        campsiteId = pastCampsite.id,
        start = pastStart,
        end = pastEnd
    )

    /**
     * Displays the reservation list using fake campsite and booking data.
     *
     * @param active Active reservations to display.
     * @param past Past reservations to display.
     * @param onDetails Callback for selecting a reservation.
     * @param onLotteries Callback for selecting the Lotteries tab.
     */
    private fun showReservations(
        active: List<ReservedUser> = listOf(activeReservation),
        past: List<ReservedUser> = listOf(pastReservation),
        onDetails: (ReservedUser) -> Unit = {},
        onLotteries: () -> Unit = {}
    ) {
        val campsites = listOf(
            activeCampsite,
            pastCampsite
        )

        composeTestRule.setContent {
            var selectedTab by remember {
                mutableIntStateOf(1)
            }

            MaterialTheme {
                ReservationsView(
                    activeReservations = active,
                    pastReservations = past,
                    selectedTab = selectedTab,
                    onSelectedTabChange = { selectedTab = it },
                    campsiteForId = { id ->
                        campsites.find { it.id == id }
                    },
                    onViewDetails = onDetails,
                    onLotteriesClick = onLotteries,
                    imageContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(145.dp)
                        ) {
                            Text("Test campsite image")
                        }
                    }
                )
            }
        }
    }

    /**
     * Displays the reservation details screen with fake information.
     *
     * @param reservation Reservation to display.
     * @param campsite Associated campsite.
     * @param isPast Whether to show the past layout.
     * @param onBack Callback for returning from details.
     * @param onMap Callback for VIEW MAP.
     * @param onMessage Callback for MESSAGE OWNER.
     * @param onCancel Callback for CANCEL RESERVATION.
     * @param onRate Callback for RATE CAMPSITE.
     */
    private fun showDetails(
        reservation: ReservedUser,
        campsite: Campsite?,
        isPast: Boolean,
        onBack: () -> Unit = {},
        onMap: () -> Unit = {},
        onMessage: () -> Unit = {},
        onCancel: () -> Unit = {},
        onRate: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                ReservationDetailsView(
                    reservation = reservation,
                    campsite = campsite,
                    isPast = isPast,
                    imageContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                        ) {
                            Text("Test campsite image")
                        }
                    },
                    onViewMap = onMap,
                    onMessageOwner = onMessage,
                    onCancelReservation = onCancel,
                    onRateCampsite = onRate,
                    onBack = onBack
                )
            }
        }
    }

    /**
     * Verifies active reservation cards display campsite information,
     * booking status, and the details button.
     */
    @Test
    fun activeReservationsDisplayCorrectly() {
        showReservations()

        composeTestRule.onNodeWithTag("tripsHeading")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-active"
        ).assertExists()

        composeTestRule.onNodeWithText(
            "Mountain View Campsite"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText(
            "Edmonton, Alberta"
        ).assertExists()

        composeTestRule.onNodeWithText(
            "Max Occupancy: 4"
        ).assertExists()

        composeTestRule.onNodeWithText(
            "Rating: 4.5"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "reservationStatus_reservation-active"
        ).assertTextEquals("Status: Upcoming")

        composeTestRule.onNodeWithTag(
            "viewReservationDetails_reservation-active"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-past"
        ).assertDoesNotExist()
    }

    /**
     * Verifies that selecting Past Reservations shows completed stays
     * and hides active reservation cards.
     */
    @Test
    fun pastReservationsDisplayCorrectly() {
        showReservations()

        composeTestRule.onNodeWithTag(
            "pastReservationsTab"
        ).performClick()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-past"
        ).assertExists()

        composeTestRule.onNodeWithText(
            "Lakefront Campsite"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag(
            "reservationStatus_reservation-past"
        ).assertTextEquals("Status: Completed")

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-active"
        ).assertDoesNotExist()
    }

    /**
     * Verifies switching between active and past reservation tabs
     * changes the displayed list.
     */
    @Test
    fun switchingTabsUpdatesReservationList() {
        showReservations()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-active"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "pastReservationsTab"
        ).performClick()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-past"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "reservationsTab"
        ).performClick()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-active"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "reservationCard_reservation-past"
        ).assertDoesNotExist()
    }

    /**
     * Verifies that the correct empty messages appear when
     * the entrant has no active or past reservations.
     */
    @Test
    fun emptyReservationStatesDisplayCorrectly() {
        showReservations(
            active = emptyList(),
            past = emptyList()
        )

        composeTestRule.onNodeWithTag(
            "reservationsEmptyState"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText(
            "No active reservations."
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag(
            "pastReservationsTab"
        ).performClick()

        composeTestRule.onNodeWithText(
            "No past reservations."
        ).assertIsDisplayed()
    }

    /**
     * Verifies that VIEW DETAILS passes the selected reservation
     * to the caller instead of a different reservation.
     */
    @Test
    fun viewDetailsPassesCorrectReservation() {
        var selectedReservation: ReservedUser? = null

        showReservations(
            onDetails = { selectedReservation = it }
        )

        composeTestRule.onNodeWithTag(
            "viewReservationDetails_reservation-active"
        ).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                activeReservation.id,
                selectedReservation?.id
            )
            assertEquals(
                activeReservation.campsiteId,
                selectedReservation?.campsiteId
            )
        }
    }

    /**
     * Verifies active reservation details contain the expected
     * information and action buttons, but no rating action.
     */
    @Test
    fun activeDetailsShowCorrectInformationAndButtons() {
        showDetails(
            reservation = activeReservation,
            campsite = activeCampsite,
            isPast = false
        )

        composeTestRule.onNodeWithTag(
            "reservationDetailsScreen"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag(
            "reservationDetailsCampsiteName"
        ).assertTextEquals("Mountain View Campsite")

        composeTestRule.onNodeWithTag(
            "reservationDetailsLocation"
        ).assertTextEquals("Edmonton, Alberta")

        composeTestRule.onNodeWithTag(
            "reservationDetailsDates"
        ).assertTextContains("Dates:", substring = true)

        composeTestRule.onNodeWithTag(
            "reservationDetailsStatus"
        ).assertTextEquals("Status: Upcoming")

        composeTestRule.onNodeWithTag(
            "viewReservationMapButton"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "messageReservationOwnerButton"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "cancelReservationButton"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "reservationDetailsBackButton"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "rateReservationCampsiteButton"
        ).assertDoesNotExist()
    }

    /**
     * Verifies past reservation details show completed status,
     * rating information and the rating action, but no active actions.
     */
    @Test
    fun pastDetailsShowCorrectInformationAndButtons() {
        showDetails(
            reservation = pastReservation,
            campsite = pastCampsite,
            isPast = true
        )

        composeTestRule.onNodeWithTag(
            "reservationDetailsCampsiteName"
        ).assertTextEquals("Lakefront Campsite")

        composeTestRule.onNodeWithTag(
            "reservationDetailsStatus"
        ).assertTextEquals("Status: Completed")

        composeTestRule.onNodeWithTag(
            "reservationDetailsRating"
        ).assertTextEquals("Your Rating: Not rated")

        composeTestRule.onNodeWithTag(
            "rateReservationCampsiteButton"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "reservationDetailsBackButton"
        ).assertExists()

        composeTestRule.onNodeWithTag(
            "viewReservationMapButton"
        ).assertDoesNotExist()

        composeTestRule.onNodeWithTag(
            "messageReservationOwnerButton"
        ).assertDoesNotExist()

        composeTestRule.onNodeWithTag(
            "cancelReservationButton"
        ).assertDoesNotExist()
    }

    /**
     * Verifies BACK invokes the callback from both active
     * and past reservation details screens.
     */
    @Test
    fun backButtonInvokesCallback() {
        var backCalled = false

        showDetails(
            reservation = activeReservation,
            campsite = activeCampsite,
            isPast = false,
            onBack = { backCalled = true }
        )

        composeTestRule.onNodeWithTag(
            "reservationDetailsBackButton"
        ).performClick()

        composeTestRule.runOnIdle {
            assertTrue(backCalled)
        }
    }

    /**
     * Verifies the Lotteries tab invokes the navigation callback.
     */
    @Test
    fun lotteriesTabInvokesNavigationCallback() {
        var lotteriesClicked = false

        showReservations(
            onLotteries = { lotteriesClicked = true }
        )

        composeTestRule.onNodeWithTag(
            "lotteriesTab"
        ).performClick()

        composeTestRule.runOnIdle {
            assertTrue(lotteriesClicked)
        }
    }

    /**
     * Verifies that BACK invokes the callback
     * from the past reservation details screen.
     */
    @Test
    fun pastDetailsBackButtonInvokesCallback() {
        var backCalled = false

        showDetails(
            reservation = pastReservation,
            campsite = pastCampsite,
            isPast = true,
            onBack = { backCalled = true }
        )

        composeTestRule.onNodeWithTag(
            "reservationDetailsBackButton"
        ).performClick()

        composeTestRule.runOnIdle {
            assertTrue(backCalled)
        }
    }
}
