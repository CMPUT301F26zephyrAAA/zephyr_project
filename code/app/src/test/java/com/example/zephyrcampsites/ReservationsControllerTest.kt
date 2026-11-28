package com.example.zephyrcampsites

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies entrant reservation filtering independently from owner reservation coverage. */
class ReservationsControllerTest {
    private val controller = ReservationsController()
    private val currentTime = Date(100_000L)

    private fun reservation(
        id: String,
        start: Date?,
        end: Date?,
        cancelled: Boolean = false
    ) = ReservedUser(
        id = id,
        accountId = "entrant-1",
        campsiteId = "campsite-1",
        bookedPeriodStart = start,
        bookedPeriodEnd = end,
        cancelled = cancelled
    )

    @Test
    fun activeReservationsIncludeCurrentAndUpcomingReservationsInStartOrder() {
        val current = reservation("current", Date(50_000L), Date(150_000L))
        val upcoming = reservation("upcoming", Date(200_000L), Date(300_000L))
        val completed = reservation("completed", Date(10_000L), Date(50_000L))
        val cancelled = reservation("cancelled", Date(200_000L), Date(300_000L), true)

        val results = controller.filterActiveReservations(
            listOf(upcoming, completed, cancelled, current),
            currentTime
        )

        assertEquals(listOf(current, upcoming), results)
    }

    @Test
    fun activeReservationsExcludeMissingAndInvalidBookingPeriods() {
        val missingStart = reservation("missing-start", null, Date(200_000L))
        val missingEnd = reservation("missing-end", Date(200_000L), null)
        val invalidRange = reservation("invalid-range", Date(300_000L), Date(200_000L))

        val results = controller.filterActiveReservations(
            listOf(missingStart, missingEnd, invalidRange),
            currentTime
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun pastReservationsIncludeCompletedRecordsInReverseCheckoutOrder() {
        val older = reservation("older", Date(10_000L), Date(30_000L))
        val cancelled = reservation("cancelled", Date(40_000L), Date(60_000L), true)

        val results = controller.filterPastReservations(
            listOf(older, cancelled),
            currentTime
        )

        assertEquals(listOf(cancelled, older), results)
    }
}
