package com.example.zephyrcampsites

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Verifies calendar-day availability for winning entrants. */
class LotteryBookingPickerControllerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val december = ReservationBookingMonth(2026, Calendar.DECEMBER)

    private fun day(dayOfMonth: Int): Long {
        return LotteryBookingPickerController.dayStartMillis(december, dayOfMonth, utc)
    }

    @Test
    fun bookedAndOwnerBlockedDatesCannotStartAStay() {
        val availability = LotteryBookingAvailability(
            bookingStartMillis = day(10),
            bookingEndMillis = day(20),
            bookedPeriods = listOf(BookedReservationPeriod(day(12), day(15)))
        )

        val availableDays = LotteryBookingPickerController.availableDayStarts(
            month = december,
            availability = availability,
            timeZone = utc,
            nowMillis = day(1)
        )

        assertFalse(day(9) in availableDays)
        assertTrue(day(10) in availableDays)
        assertFalse(day(12) in availableDays)
        assertFalse(day(14) in availableDays)
        assertTrue(day(15) in availableDays)
        assertFalse(day(20) in availableDays)
    }

    @Test
    fun selectedStayMustBeContinuousAndUnbooked() {
        val availability = LotteryBookingAvailability(
            bookingStartMillis = day(10),
            bookingEndMillis = day(20),
            bookedPeriods = listOf(BookedReservationPeriod(day(13), day(15)))
        )

        assertTrue(
            LotteryBookingPickerController.isAvailableStay(
                day(10), day(13), availability, utc, day(1)
            )
        )
        assertFalse(
            LotteryBookingPickerController.isAvailableStay(
                day(10), day(16), availability, utc, day(1)
            )
        )
    }
}
