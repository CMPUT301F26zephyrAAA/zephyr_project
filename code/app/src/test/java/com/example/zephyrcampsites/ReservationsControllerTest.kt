package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Unit tests for US 02.07.01.
 *
 * Verifies that campsite owners can only view active
 * reservations belonging to their own campsites.
 *
 * These tests do not require Firebase.
 */
class ReservationsControllerTest {

    private val controller = ReservationsController()

    private val ownerId = "owner1"
    private val otherOwnerId = "owner2"

    private val currentTime = Date(100000L)

    private val campsite1 = Campsite("101").apply {
        name = "Pine Ridge Campground"
        ownerID = ownerId
    }

    private val campsite2 = Campsite("102").apply {
        name = "Lakeview Retreat"
        ownerID = ownerId
    }

    private val otherCampsite = Campsite("103").apply {
        name = "Mountain View Campsite"
        ownerID = otherOwnerId
    }

    private val campsites = listOf(
        campsite1,
        campsite2,
        otherCampsite
    )

    /**
     * Creates dummy confirmed reservations for testing.
     */
    private fun createReservation(
        id: String = "reservation1",
        campsiteId: String = "101",
        start: Date? = Date(200000L),
        end: Date? = Date(300000L),
        cancelled: Boolean = false
    ): ReservedUser {
        return ReservedUser(
            id = id,
            accountId = "entrant1",
            campsiteId = campsiteId,
            bookedPeriodStart = start,
            bookedPeriodEnd = end,
            cancelled = cancelled
        )
    }

    /**
     * Applies owner reservation filtering using fixed time.
     */
    private fun filter(
        reservations: List<ReservedUser>
    ): List<ReservedUser> {
        return controller.filterOwnerActiveReservations(
            reservations = reservations,
            campsites = campsites,
            ownerId = ownerId,
            currentTime = currentTime
        )
    }

    @Test
    fun upcomingReservationIsIncluded() {
        val reservation = createReservation()

        val result = filter(listOf(reservation))

        assertEquals(listOf(reservation), result)
    }

    @Test
    fun currentStayIsIncluded() {
        val reservation = createReservation(
            start = Date(50000L),
            end = Date(150000L)
        )

        val result = filter(listOf(reservation))

        assertEquals(listOf(reservation), result)
    }

    @Test
    fun completedReservationIsExcluded() {
        val reservation = createReservation(
            start = Date(10000L),
            end = Date(50000L)
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun cancelledReservationIsExcluded() {
        val reservation = createReservation(
            cancelled = true
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun anotherOwnersReservationIsExcluded() {
        val reservation = createReservation(
            campsiteId = "103"
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun reservationsFromMultipleOwnedCampsitesAreIncluded() {
        val first = createReservation(
            id = "reservation1",
            campsiteId = "101"
        )

        val second = createReservation(
            id = "reservation2",
            campsiteId = "102"
        )

        val other = createReservation(
            id = "reservation3",
            campsiteId = "103"
        )

        val result = filter(
            listOf(first, second, other)
        )

        assertEquals(2, result.size)
        assertTrue(result.contains(first))
        assertTrue(result.contains(second))
        assertFalse(result.contains(other))
    }

    @Test
    fun reservationWithMissingStartDateIsExcluded() {
        val reservation = createReservation(
            start = null
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun reservationWithMissingEndDateIsExcluded() {
        val reservation = createReservation(
            end = null
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun reservationWithInvalidDateRangeIsExcluded() {
        val reservation = createReservation(
            start = Date(300000L),
            end = Date(200000L)
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun reservationEndingAtCurrentTimeIsExcluded() {
        val reservation = createReservation(
            start = Date(50000L),
            end = currentTime
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun ownerWithoutCampsitesReceivesEmptyList() {
        val reservation = createReservation()

        val result =
            controller.filterOwnerActiveReservations(
                reservations = listOf(reservation),
                campsites = campsites,
                ownerId = "unknownOwner",
                currentTime = currentTime
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun emptyReservationListReturnsEmptyList() {
        val result = filter(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun reservationForUnknownCampsiteIsExcluded() {
        val reservation = createReservation(
            campsiteId = "999"
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun activeReservationsAreSortedByStartDate() {
        val later = createReservation(
            id = "later",
            start = Date(400000L),
            end = Date(500000L)
        )

        val earlier = createReservation(
            id = "earlier",
            start = Date(200000L),
            end = Date(300000L)
        )

        val result = filter(listOf(later, earlier))

        assertEquals(listOf(earlier, later), result)
    }

    @Test
    fun reservationWithIdenticalStartAndEndIsExcluded() {
        val reservation = createReservation(
            start = Date(200000L),
            end = Date(200000L)
        )

        val result = filter(listOf(reservation))

        assertTrue(result.isEmpty())
    }

    @Test
    fun reservationStartingAtCurrentTimeIsIncluded() {
        val reservation = createReservation(
            start = currentTime,
            end = Date(200000L)
        )

        val result = filter(listOf(reservation))

        assertEquals(listOf(reservation), result)
    }
}
