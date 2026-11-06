package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test
// [AI-08] OpenAI, ChatGPT, assistance with developing unit tests for lottery registration eligibility, duplicate membershidp, and waiting-list capacity

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