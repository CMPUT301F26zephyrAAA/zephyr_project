package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test

class LotteryCapacityTest {

    @Test
    fun positiveCapacityBeforeRegistrationIsAllowed() {
        LotteryCapacityValidator.validateCapacityUpdate(
            oldCapacity = 10,
            newCapacity = 15,
            registrationStarted = false
        )
    }

    @Test
    fun zeroCapacityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LotteryCapacityValidator.validateCapacityUpdate(
                oldCapacity = 10,
                newCapacity = 0,
                registrationStarted = false
            )
        }
    }

    @Test
    fun decreasingCapacityAfterRegistrationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LotteryCapacityValidator.validateCapacityUpdate(
                oldCapacity = 15,
                newCapacity = 10,
                registrationStarted = true
            )
        }
    }
    @Test
    fun increasingCapacityAfterRegistrationIsAllowed() {
        LotteryCapacityValidator.validateCapacityUpdate(
            oldCapacity = 15,
            newCapacity = 20,
            registrationStarted = true
        )
    }

    @Test
    fun removingCapacityAfterRegistrationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LotteryCapacityValidator.validateCapacityUpdate(
                oldCapacity = 15,
                newCapacity = null,
                registrationStarted = true
            )
        }
    }

    @Test
    fun limitingUnlimitedCapacityAfterRegistrationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LotteryCapacityValidator.validateCapacityUpdate(
                oldCapacity = null,
                newCapacity = 10,
                registrationStarted = true
            )
        }
    }

    @Test
    fun removingCapacityBeforeRegistrationIsAllowed() {
        LotteryCapacityValidator.validateCapacityUpdate(
            oldCapacity = 10,
            newCapacity = null,
            registrationStarted = false
        )
    }

    @Test
    fun negativeCapacityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            LotteryCapacityValidator.validateCapacityUpdate(
                oldCapacity = 10,
                newCapacity = -5,
                registrationStarted = false
            )
        }
    }

    @Test
    fun decreasingCapacityBeforeRegistrationIsAllowed() {
        LotteryCapacityValidator.validateCapacityUpdate(
            oldCapacity = 15,
            newCapacity = 10,
            registrationStarted = false
        )
    }


}