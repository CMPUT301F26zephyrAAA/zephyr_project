
package com.example.zephyrcampsites

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationOfferValidatorTest {

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
}
