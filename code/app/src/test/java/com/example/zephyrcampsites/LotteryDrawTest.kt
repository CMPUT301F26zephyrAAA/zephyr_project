package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse

class LotteryDrawTest {

    @Test
    fun selectWinners_returnsCorrectNumberOfWinners() {

        // Create five entrants for the lottery.
        val entrants = (1..5).map { number ->
            WaitingUser("registration$number").apply {
                accountId = "account$number"
                lotteryId = "lottery1"
            }
        }

        // Select two winners.
        val (winners, backups) =
            LotteryDraw.selectWinners(entrants, 2)

        // Check that two winners and three backups were selected.
        assertEquals(2, winners.size)
        assertEquals(3, backups.size)

        // Check that no entrant was selected twice.
        val allIds = (winners + backups).map { it.accountId }
        assertEquals(allIds.size, allIds.distinct().size)

        // Check that every selected entrant came from the waiting list.
        assertTrue(allIds.all { id ->
            entrants.any { it.accountId == id }
        })
    }

    @Test
    fun selectWinners_handlesFewerEntrantsThanWinners() {

        // Create three entrants for the lottery.
        val entrants = (1..3).map { number ->
            WaitingUser("registration$number").apply {
                accountId = "account$number"
                lotteryId = "lottery1"
            }
        }

        // The lottery allows five winners, but only three joined.
        val (winners, backups) =
            LotteryDraw.selectWinners(entrants, 5)

        // All three entrants should win.
        assertEquals(3, winners.size)

        // No entrants should remain on the backup list.
        assertEquals(0, backups.size)
    }

    @Test
    fun selectWinners_preventsDuplicateEntrants() {

        // Create two registrations belonging to the same account.
        val entrants = listOf(
            WaitingUser("registration1").apply {
                accountId = "account1"
                lotteryId = "lottery1"
            },
            WaitingUser("registration2").apply {
                accountId = "account1"
                lotteryId = "lottery1"
            },
            WaitingUser("registration3").apply {
                accountId = "account2"
                lotteryId = "lottery1"
            }
        )

        // Try to select three winners.
        val (winners, backups) =
            LotteryDraw.selectWinners(entrants, 3)

        // Only two unique accounts should be selected.
        assertEquals(2, winners.size)
        assertEquals(0, backups.size)

        // Make sure no account appears more than once.
        assertEquals(
            winners.size,
            winners.map { it.accountId }.distinct().size
        )
    }

    @Test
    fun selectWinners_handlesEmptyWaitingList() {

        // No entrants registered for this lottery.
        val entrants = emptyList<WaitingUser>()

        // Try to draw five winners.
        val (winners, backups) =
            LotteryDraw.selectWinners(entrants, 5)

        // No winners or backups should be selected.
        assertEquals(0, winners.size)
        assertEquals(0, backups.size)
    }

    @Test
    fun getEligibleEntrants_filtersInvalidEntrants() {

        // Create entrants with different lottery statuses.
        val entrants = listOf(
            WaitingUser("registration1").apply {
                lotteryId = "lottery1"
                accountId = "account1"
                drawStatus = "WAITING"
            },
            WaitingUser("registration2").apply {
                lotteryId = "lottery1"
                accountId = "account2"
                drawStatus = "WINNER"
            },
            WaitingUser("registration3").apply {
                lotteryId = "lottery2"
                accountId = "account3"
                drawStatus = "WAITING"
            },
            WaitingUser("registration4").apply {
                lotteryId = "lottery1"
                accountId = ""
                drawStatus = "WAITING"
            }
        )

        // Get only eligible entrants for lottery1.
        val eligible = LotteryDraw.getEligibleEntrants(
            entrants,
            "lottery1"
        )

        // Only account1 should be eligible.
        assertEquals(1, eligible.size)
        assertEquals("account1", eligible[0].accountId)
    }

    @Test
    fun canStartDraw_checksRegistrationEndAndDrawStatus() {

        // Set the current time for testing.
        val currentTime = 10000L

        // Registration is still open, so the draw cannot start.
        assertFalse(
            LotteryDraw.canStartDraw(15000L, false, currentTime)
        )

        // Registration has ended, so the draw can start.
        assertTrue(
            LotteryDraw.canStartDraw(5000L, false, currentTime)
        )

        // Draw has already finished, so it cannot run again.
        assertFalse(
            LotteryDraw.canStartDraw(5000L, true, currentTime)
        )

        // Registration ends exactly now, so the draw can start.
        assertTrue(
            LotteryDraw.canStartDraw(10000L, false, currentTime)
        )
    }

    @Test
    fun drawStatus_checksValidTransitions() {

        // A pending lottery can start processing.
        assertTrue(
            LotteryDraw.canChangeDrawStatus("PENDING", "PROCESSING")
        )

        // A processing lottery can finish.
        assertTrue(
            LotteryDraw.canChangeDrawStatus("PROCESSING", "COMPLETED")
        )

        // A completed lottery cannot start another draw.
        assertFalse(
            LotteryDraw.canChangeDrawStatus("COMPLETED", "PROCESSING")
        )

        // Two workers cannot claim the same processing lottery.
        assertFalse(
            LotteryDraw.canChangeDrawStatus("PROCESSING", "PROCESSING")
        )
    }

    @Test
    fun drawStatus_allowsResetAfterFailedDraw() {

        // A processing lottery can return to pending for recovery.
        assertTrue(
            LotteryDraw.canChangeDrawStatus("PROCESSING", "PENDING")
        )

        // A completed lottery cannot be reset.
        assertFalse(
            LotteryDraw.canChangeDrawStatus("COMPLETED", "PENDING")
        )
    }

}