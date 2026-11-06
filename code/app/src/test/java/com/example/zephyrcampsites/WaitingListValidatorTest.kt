package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test
//this test was written with AI - https://chatgpt.com/share/6aed8276-605c-83eb-81c0-8249008661e5
class WaitingListValidatorTest {

    @Test
    fun joinAllowedWhenRegistrationOpen() {
        val result = WaitingListValidator.canJoin(
            currentTime = 150,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = false
        )

        assertTrue(result)
    }

    @Test
    fun joinRejectedBeforeRegistrationStarts() {
        val result = WaitingListValidator.canJoin(
            currentTime = 50,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = false
        )

        assertFalse(result)
    }

    @Test
    fun joinRejectedAfterRegistrationCloses() {
        val result = WaitingListValidator.canJoin(
            currentTime = 250,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = false
        )

        assertFalse(result)
    }

    @Test
    fun joinRejectedAtExactEndTime() {
        val result = WaitingListValidator.canJoin(
            currentTime = 200,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = false
        )

        assertFalse(result)
    }

    @Test
    fun duplicateRegistrationRejected() {
        val result = WaitingListValidator.canJoin(
            currentTime = 150,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = true
        )

        assertFalse(result)
    }

    @Test
    fun registrationAllowedBelowCapacity() {
        assertTrue(
            WaitingListValidator.hasCapacity(
                currentCount = 19,
                capacity = 20
            )
        )
    }

    @Test
    fun registrationRejectedAtCapacity() {
        assertFalse(
            WaitingListValidator.hasCapacity(
                currentCount = 20,
                capacity = 20
            )
        )
    }

    @Test
    fun registrationAllowedWithUnlimitedCapacity() {
        assertTrue(
            WaitingListValidator.hasCapacity(
                currentCount = 100,
                capacity = null
            )
        )
    }

    @Test
    fun joinAllowedAtExactStartTime() {
        val result = WaitingListValidator.canJoin(
            currentTime = 100,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = false
        )

        assertTrue(result)
    }

    @Test
    fun joinAllowedJustBeforeRegistrationEnds() {
        val result = WaitingListValidator.canJoin(
            currentTime = 199,
            registrationStart = 100,
            registrationEnd = 200,
            alreadyJoined = false
        )

        assertTrue(result)
    }
}