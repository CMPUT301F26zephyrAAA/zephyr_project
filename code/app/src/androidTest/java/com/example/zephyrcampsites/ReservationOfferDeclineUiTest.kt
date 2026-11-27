// AI Citation [AI-31]
// OpenAI, ChatGPT assisted with generating Jetpack Compose
// UI tests for reservation offer decline functionality,
// including winner status, confirmation dialogs, cancellation,
// declined status, and entrant role restrictions.
// AI usage and citation documented in the GitHub Wiki.
package com.example.zephyrcampsites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for US 01.04.03 — Decline a Reservation Offer.
 *
 * Tests the existing CampsiteDetailView without changing
 * production UI code.
 *
 * Uses existing Firestore test records for draw status.
 * No decline is submitted to Firebase.
 */
@RunWith(AndroidJUnit4::class)
class ReservationOfferDeclineUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Completed lottery with an existing winner.
    private val winnerLotteryId =
        "755f05b5-476e-4270-86a2-562533ce6383"

    private val winnerAccountId =
        "7263f0ae1994d6cc"

    // Test lottery that has already been declined.
    private val declinedLotteryId =
        "test_decline_010403"

    private val declinedAccountId =
        "c5489fc3791b76fc"

    /**
     * Fake waiting list dependency.
     * Avoids joining or leaving any real waiting list.
     */
    private class FakeWaitingListGateway : WaitingListGateway {

        override fun isEntrantJoined(
            lotteryId: String,
            accountId: String,
            onResult: (Boolean) -> Unit,
            onError: (String) -> Unit
        ) {
            onResult(true)
        }

        override fun joinWaitingList(
            lotteryId: String,
            accountId: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            onError("Joining is disabled in this test")
        }

        override fun leaveWaitingList(
            lotteryId: String,
            accountId: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            onError("Leaving is disabled in this test")
        }
    }

    /**
     * Displays the real campsite detail screen.
     */
    private fun showScreen(
        lotteryId: String,
        accountId: String,
        entrantMode: Boolean = true
    ) {
        val campsite = Campsite("8").apply {
            name = "aa"
            location = "Edmonton"
        }

        val lottery = Lottery(lotteryId).apply {
            campsiteId = "8"
            lotteryName = "lottery draw"
            drawCompleted = true
            drawStatus = "COMPLETED"
        }

        val waitingListController =
            WaitingListController(FakeWaitingListGateway())

        val offerController =
            ReservationOfferController(ReservationOfferModel())

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    account = Account(accountId),
                    showEntrantActions = entrantMode,
                    lottery = lottery,
                    waitingListController = waitingListController,
                    reservationOfferController = offerController,
                    codeGenerator = { _, _ ->
                        error("QR generation not used in this test")
                    },
                    onBack = {}
                )
            }
        }
    }

    /**
     * Waits for an asynchronous Firestore result to
     * appear in the Compose UI.
     */
    private fun waitForText(text: String) {
        composeTestRule.waitUntil(15000) {
            composeTestRule
                .onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun winnerSeesWinningMessage() {
        showScreen(
            winnerLotteryId,
            winnerAccountId
        )

        waitForText(
            "Congratulations! You won the lottery."
        )

        composeTestRule
            .onNodeWithText(
                "Congratulations! You won the lottery."
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun winnerCanOpenDeclineConfirmation() {
        showScreen(
            winnerLotteryId,
            winnerAccountId
        )

        waitForText("Decline Reservation")

        composeTestRule
            .onNodeWithText("Decline Reservation")
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithText("Decline reservation?")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Yes, Decline")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Cancel")
            .assertIsDisplayed()
    }

    @Test
    fun cancellingDeclineKeepsOfferAvailable() {
        showScreen(
            winnerLotteryId,
            winnerAccountId
        )

        waitForText("Decline Reservation")

        composeTestRule
            .onNodeWithText("Decline Reservation")
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Decline reservation?")
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("Decline Reservation")
            .assertIsDisplayed()
    }

    @Test
    fun declinedEntrantSeesDeclinedMessage() {
        showScreen(
            declinedLotteryId,
            declinedAccountId
        )

        waitForText(
            "You have declined this reservation opportunity."
        )

        composeTestRule
            .onNodeWithText(
                "You have declined this reservation opportunity."
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun declinedEntrantCannotDeclineAgain() {
        showScreen(
            declinedLotteryId,
            declinedAccountId
        )

        waitForText(
            "You have declined this reservation opportunity."
        )

        composeTestRule
            .onNodeWithText("Decline Reservation")
            .assertDoesNotExist()
    }

    @Test
    fun nonEntrantCannotSeeDeclineButton() {
        showScreen(
            winnerLotteryId,
            winnerAccountId,
            entrantMode = false
        )

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Decline Reservation")
            .assertDoesNotExist()
    }


    @Test
    fun winnerWithExistingReservationCannotDecline() {
        showScreen(
            lotteryId = "ui_test_reserved_lottery",
            accountId = "ui_test_reserved_entrant"
        )

        waitForText(
            "Reservation already created. Use Cancel Reservation in My Trips."
        )

        composeTestRule
            .onNodeWithText(
                "Reservation already created. Use Cancel Reservation in My Trips."
            )
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Decline Reservation")
            .assertDoesNotExist()
    }

}
