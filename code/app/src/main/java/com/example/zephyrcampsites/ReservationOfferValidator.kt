
package com.example.zephyrcampsites
// AI Citation [AI-25]
// OpenAI, ChatGPT assisted with reservation offer
// decline eligibility validation.
// AI usage and citation documented in the GitHub Wiki.
/**
 * Validates whether an entrant may decline a lottery-winning offer.
 */
object ReservationOfferValidator {

    fun canDecline(
        drawCompleted: Boolean,
        entrantStatus: String,
        alreadyDeclined: Boolean
    ): Boolean {
        return drawCompleted &&
                entrantStatus == "WINNER" &&
                !alreadyDeclined
    }
}
