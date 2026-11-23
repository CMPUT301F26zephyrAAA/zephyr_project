
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
// AI Citation [AI-17]
// OpenAI, ChatGPT assisted with JUnit 4 unit tests
// for active and past reservation filtering.
// AI usage and citation documented in the GitHub Wiki.
/**
 * Unit tests for the reservation filtering logic in ReservationsController.
 *
 * Verifies active and past reservation classification, cancellation
 * handling, date validation, boundary conditions, and sorting.
 *
 * Uses fixed timestamps for deterministic results.
 * Does not access Firebase or modify Firestore data.
 *
 * Covers US 01.06.01 and US 01.06.02.
 */
class ReservationsControllerTest {

    private val currentTime = Date(1_800_000_000_000L)

    private val oneDay = 24L * 60 * 60 * 1000

    // The controller's Firebase model is initialized lazily, so the
    // filtering methods can be tested without connecting to Firebase.
    private val controller = ReservationsController()

    /**
     * Creates a reservation for testing.
     *
     * @param id Unique reservation identifier.
     * @param start Booking start date, or null.
     * @param end Booking end date, or null.
     * @param cancelled Whether the reservation is cancelled.
     * @return Reservation with the specified test values.
     */
    private fun reservation(
        id: String,
        start: Date?,
        end: Date?,
        cancelled: Boolean = false
    ): ReservedUser {
        return ReservedUser(
            id = id,
            accountId = "test-entrant",
            campsiteId = "test-campsite",
            bookedPeriodStart = start,
            bookedPeriodEnd = end,
            cancelled = cancelled
        )
    }

    /**
     * Returns a date relative to the fixed current time.
     *
     * @param days Number of days before or after currentTime.
     * @return Date offset from the fixed timestamp.
     */
    private fun daysFromNow(days: Long): Date {
        return Date(currentTime.time + days * oneDay)
    }

    /**
     * Verifies that a future reservation appears in the active list.
     */
    @Test
    fun upcomingReservationAppearsInActiveList() {
        val upcoming = reservation(
            "upcoming",
            daysFromNow(2),
            daysFromNow(5)
        )

        val result = controller.filterActiveReservations(
            listOf(upcoming),
            currentTime
        )

        assertEquals(1, result.size)
        assertEquals("upcoming", result[0].id)
    }

    /**
     * Verifies that a reservation currently in progress is active.
     */
    @Test
    fun ongoingReservationAppearsInActiveList() {
        val ongoing = reservation(
            "ongoing",
            daysFromNow(-2),
            daysFromNow(2)
        )

        val result = controller.filterActiveReservations(
            listOf(ongoing),
            currentTime
        )

        assertEquals(1, result.size)
        assertEquals("ongoing", result[0].id)
    }

    /**
     * Verifies that completed reservations appear in the past list
     * and are excluded from the active list.
     */
    @Test
    fun completedReservationAppearsInPastList() {
        val completed = reservation(
            "completed",
            daysFromNow(-5),
            daysFromNow(-2)
        )

        val active = controller.filterActiveReservations(
            listOf(completed),
            currentTime
        )

        val past = controller.filterPastReservations(
            listOf(completed),
            currentTime
        )

        assertTrue(active.isEmpty())
        assertEquals(1, past.size)
        assertEquals("completed", past[0].id)
    }

    /**
     * Verifies that cancelled reservations do not appear
     * in either the active or past reservation list.
     */
    @Test
    fun cancelledReservationsExcludedFromActiveButIncludedInPast() {
        val cancelledUpcoming = reservation(
            "cancelled-upcoming",
            daysFromNow(1),
            daysFromNow(3),
            cancelled = true
        )

        val cancelledPast = reservation(
            "cancelled-past",
            daysFromNow(-5),
            daysFromNow(-3),
            cancelled = true
        )

        val reservations = listOf(
            cancelledUpcoming,
            cancelledPast
        )

        val active = controller.filterActiveReservations(
            reservations,
            currentTime
        )

        val past = controller.filterPastReservations(
            reservations,
            currentTime
        )

        // Cancelled reservations must not appear in Active.
        assertTrue(active.isEmpty())

        // Cancelled reservations with past checkout dates appear in Past.
        assertEquals(1, past.size)
        assertEquals("cancelled-past", past[0].id)
        assertTrue(past[0].cancelled)
    }

    /**
     * Verifies that a reservation ending exactly at currentTime
     * belongs only to the past reservation list.
     */
    @Test
    fun reservationEndingExactlyNowAppearsOnlyInPastList() {
        val endingNow = reservation(
            "ending-now",
            daysFromNow(-2),
            currentTime
        )

        val active = controller.filterActiveReservations(
            listOf(endingNow),
            currentTime
        )

        val past = controller.filterPastReservations(
            listOf(endingNow),
            currentTime
        )

        assertFalse(active.contains(endingNow))
        assertEquals(1, past.size)
        assertEquals("ending-now", past[0].id)
    }

    /**
     * Verifies that missing dates, reversed date ranges,
     * and equal start/end dates are excluded from both lists.
     */
    @Test
    fun invalidOrMissingDatesAreExcluded() {
        val missingStart = reservation(
            "missing-start",
            null,
            daysFromNow(3)
        )

        val missingEnd = reservation(
            "missing-end",
            daysFromNow(-3),
            null
        )

        val missingBoth = reservation(
            "missing-both",
            null,
            null
        )

        val reversedDates = reservation(
            "reversed",
            daysFromNow(5),
            daysFromNow(2)
        )

        val equalDates = reservation(
            "equal",
            daysFromNow(2),
            daysFromNow(2)
        )

        val reservations = listOf(
            missingStart,
            missingEnd,
            missingBoth,
            reversedDates,
            equalDates
        )

        val active = controller.filterActiveReservations(
            reservations,
            currentTime
        )

        val past = controller.filterPastReservations(
            reservations,
            currentTime
        )

        assertTrue(active.isEmpty())
        assertTrue(past.isEmpty())
    }

    /**
     * Verifies that active reservations are sorted
     * by booking start date, earliest first.
     */
    @Test
    fun activeReservationsSortedByStartDate() {
        val latest = reservation(
            "latest",
            daysFromNow(10),
            daysFromNow(12)
        )

        val earliest = reservation(
            "earliest",
            daysFromNow(1),
            daysFromNow(3)
        )

        val middle = reservation(
            "middle",
            daysFromNow(5),
            daysFromNow(7)
        )

        val result = controller.filterActiveReservations(
            listOf(latest, earliest, middle),
            currentTime
        )

        assertEquals(
            listOf("earliest", "middle", "latest"),
            result.map { it.id }
        )
    }

    /**
     * Verifies that past reservations are sorted by
     * checkout date, most recent first.
     */
    @Test
    fun pastReservationsSortedByEndDateMostRecentFirst() {
        val oldest = reservation(
            "oldest",
            daysFromNow(-12),
            daysFromNow(-10)
        )

        val newest = reservation(
            "newest",
            daysFromNow(-3),
            daysFromNow(-1)
        )

        val middle = reservation(
            "middle",
            daysFromNow(-7),
            daysFromNow(-5)
        )

        val result = controller.filterPastReservations(
            listOf(oldest, newest, middle),
            currentTime
        )

        assertEquals(
            listOf("newest", "middle", "oldest"),
            result.map { it.id }
        )
    }

    @Test
    fun emptyReservationListReturnsEmptyResults() {
        val reservations = emptyList<ReservedUser>()

        val active = controller.filterActiveReservations(
            reservations,
            currentTime
        )

        val past = controller.filterPastReservations(
            reservations,
            currentTime
        )

        assertTrue(active.isEmpty())
        assertTrue(past.isEmpty())
    }
}
