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
}