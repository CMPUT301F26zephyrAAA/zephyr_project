package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
// Hiral - [AI-09] OpenAI ChatGPT Implement lottery listing edits
/** Verifies booking-day calculations used by the owner calendar. */
class OwnerBookingCalendarControllerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val december2026 = BookingCalendarMonth(2026, 11)

    private fun day(dayOfMonth: Int): Long {
        return OwnerBookingCalendarController.dayStartMillis(december2026, dayOfMonth, utc)
    }

    private fun reservation(
        startDay: Int,
        endDay: Int,
        cancelled: Boolean = false
    ): OwnerReservation {
        return OwnerReservation(
            id = "$startDay-$endDay-$cancelled",
            campsiteName = "North",
            entrantName = "Entrant",
            startMillis = day(startDay),
            endMillis = day(endDay),
            cancelled = cancelled
        )
    }

    @Test
    fun bookedDaysIncludeCheckInAndExcludeCheckout() {
        val bookedDays = OwnerBookingCalendarController.bookedDayStarts(
            december2026,
            listOf(reservation(startDay = 12, endDay = 15)),
            utc
        )

        assertTrue(day(12) in bookedDays)
        assertTrue(day(13) in bookedDays)
        assertTrue(day(14) in bookedDays)
        assertFalse(day(15) in bookedDays)
    }

    @Test
    fun checkoutDateIsExcludedWhenTimestampIsNotMidnight() {
        val reservation = reservation(startDay = 12, endDay = 15).copy(
            endMillis = day(15) + 15 * 60 * 60 * 1000L
        )

        val bookedDays = OwnerBookingCalendarController.bookedDayStarts(
            december2026,
            listOf(reservation),
            utc
        )

        assertTrue(day(14) in bookedDays)
        assertFalse(day(15) in bookedDays)
    }

    @Test
    fun bookingPeriodMarksOnlyItsInclusiveDatesBookable() {
        val bookableDays = OwnerBookingCalendarController.bookableDayStarts(
            december2026,
            day(5),
            day(8),
            utc
        )

        assertEquals(setOf(day(5), day(6), day(7), day(8)), bookableDays)
        assertFalse(day(4) in bookableDays)
        assertFalse(day(9) in bookableDays)
    }

    @Test
    fun cancelledReservationDoesNotBlockCalendarDays() {
        val bookedDays = OwnerBookingCalendarController.bookedDayStarts(
            december2026,
            listOf(reservation(startDay = 5, endDay = 8, cancelled = true)),
            utc
        )

        assertTrue(bookedDays.isEmpty())
    }

    @Test
    fun invalidReservationDoesNotBlockCalendarDays() {
        val bookedDays = OwnerBookingCalendarController.bookedDayStarts(
            december2026,
            listOf(reservation(startDay = 20, endDay = 20)),
            utc
        )

        assertTrue(bookedDays.isEmpty())
    }

    @Test
    fun monthNavigationWrapsAcrossYears() {
        assertEquals(
            BookingCalendarMonth(2027, 0),
            OwnerBookingCalendarController.shiftMonth(december2026, 1)
        )
    }
}
