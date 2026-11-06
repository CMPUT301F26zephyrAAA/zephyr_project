package com.example.zephyrcampsites

/**
 * Holds an entrant's rating and the reservation that permits them to submit it.
 *
 * @property reservationId Reservation used to verify the completed stay.
 * @property score Saved score from 1 to 5, or zero before the first submission.
 * @property review Optional written review.
 */
data class CampsiteRating(val reservationId: String, val score: Int = 0, val review: String = "")
