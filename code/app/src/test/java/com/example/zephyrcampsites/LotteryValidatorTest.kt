package com.example.zephyrcampsites

import org.junit.Assert.assertThrows
import org.junit.Test

class LotteryValidatorTest {

    // Test that a lottery cannot be created without a campsite ID.
    @Test
    fun blankCampsiteIdThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateCampsiteId("")
        }
    }

    // Test that a valid campsite ID is accepted.
    @Test
    fun validCampsiteIdDoesNotThrowException() {
        val validator = LotteryValidator()

        validator.validateCampsiteId("campsite123")
    }

    // Test that the registration end date must be after the start date.
    @Test
    fun invalidRegistrationPeriodThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateRegistrationPeriod(2000L, 1000L)
        }
    }

    // Test that registration cannot start and end at the same time.
    @Test
    fun equalRegistrationDatesThrowException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateRegistrationPeriod(1000L, 1000L)
        }
    }

    // Test that a valid registration period is accepted.
    @Test
    fun validRegistrationPeriodDoesNotThrowException() {
        val validator = LotteryValidator()

        validator.validateRegistrationPeriod(1000L, 2000L)
    }

    // Test that booking can start and end on the same day.
    @Test
    fun singleDayBookingPeriodIsAllowed() {
        val validator = LotteryValidator()

        val start = 1000L
        val end = 2000L

        validator.validateBookingPeriod(start, end)
    }

    // Test that booking end cannot be before booking start.
    @Test
    fun bookingEndBeforeStartThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateBookingPeriod(2000L, 1000L)
        }
    }

    // Test that booking start and end cannot be the same time.
    @Test
    fun equalBookingTimesThrowException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateBookingPeriod(1000L, 1000L)
        }
    }

    // Test that overlapping booking periods are rejected.
    @Test
    fun overlappingBookingPeriodsThrowException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateNoBookingOverlap(1500L, 2500L, 1000L, 2000L)
        }
    }

    // Test that separate booking periods are allowed.
    @Test
    fun nonOverlappingBookingPeriodsAreAllowed() {
        val validator = LotteryValidator()

        validator.validateNoBookingOverlap(3000L, 4000L, 1000L, 2000L)
    }

    // Test that booking periods sharing an endpoint are rejected.
    @Test
    fun bookingPeriodsSharingEndpointThrowException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateNoBookingOverlap(2000L, 3000L, 1000L, 2000L)
        }
    }

    // Test that a booking period before an existing one is allowed.
    @Test
    fun bookingPeriodBeforeExistingOneIsAllowed() {
        val validator = LotteryValidator()

        validator.validateNoBookingOverlap(1000L, 2000L, 3000L, 4000L)
    }

    // Test that registration ends before booking starts.
    @Test
    fun registrationBeforeBookingIsAllowed() {
        val validator = LotteryValidator()

        validator.validateRegistrationBeforeBooking(1000L, 2000L)
    }

    // Test that registration cannot end after booking starts.
    @Test
    fun registrationAfterBookingStartsThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateRegistrationBeforeBooking(3000L, 2000L)
        }
    }

    // Test that registration and booking cannot start/end at the same time.
    @Test
    fun registrationEndEqualsBookingStartThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateRegistrationBeforeBooking(2000L, 2000L)
        }
    }

    // Test that registration dates can be changed before registration starts.
    @Test
    fun registrationUpdateBeforeStartIsAllowed() {
        val validator = LotteryValidator()

        validator.validateRegistrationNotStarted(2000L, 1000L)
    }

    // Test that registration dates cannot be changed after registration starts.
    @Test
    fun registrationUpdateAfterStartThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateRegistrationNotStarted(1000L, 2000L)
        }
    }

    // Test that registration dates cannot be changed at the exact start time.
    @Test
    fun registrationUpdateAtStartTimeThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateRegistrationNotStarted(1000L, 1000L)
        }
    }

    // Test that a positive number of winners is allowed.
    @Test
    fun positiveWinnerCountIsAllowed() {
        val validator = LotteryValidator()

        validator.validateNumberOfWinners(5)
    }

    // Test that zero winners is rejected.
    @Test
    fun zeroWinnerCountThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateNumberOfWinners(0)
        }
    }

    // Test that a negative number of winners is rejected.
    @Test
    fun negativeWinnerCountThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateNumberOfWinners(-3)
        }
    }

    // Test that registration can start in the future.
    @Test
    fun futureRegistrationStartIsAllowed() {
        val validator = LotteryValidator()

        validator.validateFutureRegistrationStart(2000L, 1000L)
    }

    // Test that registration cannot start in the past.
    @Test
    fun pastRegistrationStartThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateFutureRegistrationStart(1000L, 2000L)
        }
    }

    // Test that registration cannot start at the current time.
    @Test
    fun registrationStartAtCurrentTimeThrowsException() {
        val validator = LotteryValidator()

        assertThrows(IllegalArgumentException::class.java) {
            validator.validateFutureRegistrationStart(1000L, 1000L)
        }
    }


}