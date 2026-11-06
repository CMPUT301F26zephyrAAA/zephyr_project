package com.example.zephyrcampsites

/** Validates completed stays and calculates replacement rating values. */
object RatingValidator {
    /**
     * Checks that a matching, non-cancelled reservation has ended.
     *
     * @param reservation Reservation to check.
     * @param accountId Account requesting a rating.
     * @param campsiteId Campsite being rated.
     * @param now Current time in milliseconds.
     * @return Whether the reservation records a completed stay for this account and campsite.
     */
    fun hasStayed(reservation: ReservedUser, accountId: String, campsiteId: String, now: Long): Boolean {
        val start = reservation.bookedPeriodStart?.time ?: return false
        val end = reservation.bookedPeriodEnd?.time ?: return false
        return reservation.accountId == accountId && reservation.campsiteId == campsiteId &&
            !reservation.cancelled && start < end && end < now
    }

    /**
     * Adds a first rating or replaces one previous contribution without changing other ratings.
     *
     * @param ratings Existing campsite rating values.
     * @param previousScore Entrant's previous score, or null for a first rating.
     * @param score New score from 1 to 5.
     * @return Updated values used to calculate the campsite average.
     * @throws IllegalArgumentException If any score is outside the allowed range.
     * @throws IllegalStateException If the previous contribution is missing.
     */
    fun replaceRating(ratings: List<Int>, previousScore: Int?, score: Int): List<Int> {
        require(score in 1..5 && ratings.all { it in 1..5 }) { "Ratings must be from 1 to 5." }
        val updated = ratings.toMutableList()
        if (previousScore != null) {
            check(updated.remove(previousScore)) { "The previous rating could not be found." }
        }
        updated.add(score)
        return updated
    }
}
