package com.example.zephyrcampsites

import com.google.firebase.Timestamp

// Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
/**
 * Holds an entrant's rating and the reservation that permits them to submit it.
 *
 * @property reservationId Reservation used to verify the completed stay.
 * @property score Saved score from 1 to 5, or zero before the first submission.
 * @property review Optional written review.
 * @property accountId account ID of the user that has written the rating.
 * @property updatedAt timestamp of when the rating was written.
 */
data class CampsiteRating(val reservationId: String,
                          val score: Int = 0,
                          val review: String = "",
                          val accountId: String? = null,
                          val updatedAt: Timestamp? = null)