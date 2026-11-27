package com.example.zephyrcampsites

import java.util.Date
import org.junit.Assert.*
import org.junit.Test

/** Verifies completed-stay boundaries and rating replacement calculations without Firebase. */
class RatingValidatorTest {
    private val stay = ReservedUser("stay-1", "entrant-1", "1", Date(1000), Date(2000))

    @Test
    fun onlyMatchingEndedStaysAreEligible() {
        assertTrue(RatingValidator.hasStayed(stay, "entrant-1", "1", 3000))
        assertFalse(RatingValidator.hasStayed(stay, "someone-else", "1", 3000))
        assertFalse(RatingValidator.hasStayed(stay, "entrant-1", "2", 3000))
        assertFalse(RatingValidator.hasStayed(stay.copy(cancelled = true), "entrant-1", "1", 3000))
    }

    @Test
    fun futureAndOngoingStaysAreNotEligible() {
        assertFalse(RatingValidator.hasStayed(stay, "entrant-1", "1", 500))
        assertFalse(RatingValidator.hasStayed(stay, "entrant-1", "1", 1500))
        assertFalse(RatingValidator.hasStayed(stay, "entrant-1", "1", 2000))
    }

    @Test
    fun incompleteOrReversedDatesAreNotEligible() {
        assertFalse(RatingValidator.hasStayed(stay.copy(bookedPeriodStart = null), "entrant-1", "1", 3000))
        assertFalse(RatingValidator.hasStayed(stay.copy(bookedPeriodEnd = null), "entrant-1", "1", 3000))
        assertFalse(RatingValidator.hasStayed(stay.copy(bookedPeriodStart = Date(2000)), "entrant-1", "1", 3000))
        assertFalse(RatingValidator.hasStayed(stay.copy(bookedPeriodEnd = Date(500)), "entrant-1", "1", 3000))
    }

    @Test
    fun firstRatingIncludesExistingContributionsInAverage() {
        val updated = RatingValidator.replaceRating(listOf(4, 5), null, 2)
        assertEquals(listOf(4, 5, 2), updated)
        assertEquals(11.0 / 3, updated.average(), 0.0001)
    }

    @Test
    fun replacementKeepsCountAndOtherEntrantsScores() {
        val original = listOf(4, 5, 2)
        val updated = RatingValidator.replaceRating(original, 2, 5)
        assertEquals(listOf(4, 5, 5), updated)
        assertEquals(original.size, updated.size)
        assertEquals(14.0 / 3, updated.average(), 0.0001)
        assertEquals(listOf(4, 5, 2), original)
    }

    @Test
    fun equalScoresFromOtherEntrantsArePreserved() {
        assertEquals(listOf(4, 4, 1), RatingValidator.replaceRating(listOf(4, 4, 4), 4, 1))
        assertEquals(listOf(5), RatingValidator.replaceRating(listOf(5), 5, 5))
    }

    @Test
    fun bothRatingLimitsAreAccepted() {
        assertEquals(listOf(1), RatingValidator.replaceRating(emptyList(), null, 1))
        assertEquals(listOf(5), RatingValidator.replaceRating(emptyList(), null, 5))
    }

    @Test
    fun outOfRangeScoresAreRejected() {
        for (score in listOf(-1, 0, 6)) {
            assertThrows(IllegalArgumentException::class.java) {
                RatingValidator.replaceRating(emptyList(), null, score)
            }
        }
    }

    @Test
    fun inconsistentPreviousContributionIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            RatingValidator.replaceRating(listOf(4), 2, 5)
        }
    }
}
