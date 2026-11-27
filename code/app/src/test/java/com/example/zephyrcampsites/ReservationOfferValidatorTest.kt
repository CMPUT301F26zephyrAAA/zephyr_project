package com.example.zephyrcampsites

// **AI Citation** [AI-25], [AI-34]
// OpenAI, ChatGPT assisted with JUnit 4 tests
// for reservation offer decline eligibility.
// AI usage and citations documented in the GitHub Wiki.

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationOfferValidatorTest {

    // Decline tests.

    @Test
    fun winnerCanDeclineAfterDraw() {
        assertTrue(
            ReservationOfferValidator.canDecline(true, "WINNER", false)
        )
    }

    @Test
    fun winnerCannotDeclineBeforeDraw() {
        assertFalse(
            ReservationOfferValidator.canDecline(false, "WINNER", false)
        )
    }

    @Test
    fun backupCannotDeclineWinnerOffer() {
        assertFalse(
            ReservationOfferValidator.canDecline(true, "BACKUP", false)
        )
    }

    @Test
    fun waitingEntrantCannotDecline() {
        assertFalse(
            ReservationOfferValidator.canDecline(true, "WAITING", false)
        )
    }

    @Test
    fun declinedEntrantCannotDeclineAgain() {
        assertFalse(
            ReservationOfferValidator.canDecline(true, "DECLINED", true)
        )
    }

    @Test
    fun previouslyDeclinedWinnerCannotDeclineAgain() {
        assertFalse(
            ReservationOfferValidator.canDecline(true, "WINNER", true)
        )
    }

    // Backup promotion tests.

    @Test
    fun eligibleBackupCanBePromoted() {
        assertTrue(
            ReservationOfferValidator.canPromote(
                true, true, "BACKUP", true, false
            )
        )
    }

    @Test
    fun missingRegistrationCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                false, false, null, true, false
            )
        )
    }

    @Test
    fun mismatchedRegistrationCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, false, "BACKUP", true, false
            )
        )
    }

    @Test
    fun deletedAccountCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, true, "BACKUP", false, false
            )
        )
    }

    @Test
    fun accountPendingDeletionCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, true, "BACKUP", true, true
            )
        )
    }

    @Test
    fun declinedEntrantCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, true, "DECLINED", true, false
            )
        )
    }

    @Test
    fun existingWinnerCannotBePromotedAgain() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, true, "WINNER", true, false
            )
        )
    }

    @Test
    fun waitingEntrantCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, true, "WAITING", true, false
            )
        )
    }

    @Test
    fun missingDrawStatusCannotBePromoted() {
        assertFalse(
            ReservationOfferValidator.canPromote(
                true, true, null, true, false
            )
        )
    }

    // Winner limit tests.

    @Test
    fun replacementAllowedBelowWinnerLimit() {
        assertTrue(
            ReservationOfferValidator.canIssueReplacementOffer(1, 2L)
        )
    }

    @Test
    fun replacementRejectedAtWinnerLimit() {
        assertFalse(
            ReservationOfferValidator.canIssueReplacementOffer(2, 2L)
        )
    }

    @Test
    fun replacementRejectedAboveWinnerLimit() {
        assertFalse(
            ReservationOfferValidator.canIssueReplacementOffer(3, 2L)
        )
    }

    @Test
    fun replacementRejectedWithZeroWinnerLimit() {
        assertFalse(
            ReservationOfferValidator.canIssueReplacementOffer(0, 0L)
        )
    }

    @Test
    fun replacementRejectedWithInvalidWinnerLimit() {
        assertFalse(
            ReservationOfferValidator.canIssueReplacementOffer(0, -1L)
        )
    }

    @Test
    fun replacementRejectedWithNegativeWinnerCount() {
        assertFalse(
            ReservationOfferValidator.canIssueReplacementOffer(-1, 2L)
        )
    }
}